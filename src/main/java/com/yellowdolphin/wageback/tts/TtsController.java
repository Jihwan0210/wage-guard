package com.yellowdolphin.wageback.tts;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.yellowdolphin.wageback.common.ApiException;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

// 소리로 읽어 주기: 화면 글을 영상 나레이션과 같은 목소리(Gemini TTS Charon)의 wav 로 만들어 보낸다.
// Gemini 가 안 되거나 한도(429)면 edge-tts ko-KR-InJoonNeural mp3 로, 그것도 안 되면 503 이라 화면이 브라우저 목소리로 읽는다.
// Gemini 키는 앱 env 의 GEMINI_API_KEY 를 그대로 쓴다. edge-tts 설치는 deploy 의 add_wageback_tts.sh (앱 폴더의 .tts-venv).
// 글은 명령줄 인자로 넘기지 않고 임시 파일로 넘긴다. 글과 결과는 로그에 남기지 않는다(목소리 이름만).
// 같은 글은 목소리 이름과 글의 해시 이름으로 디스크에 두고 다시 쓴다(최대 200개, 오래 안 쓴 것부터 지움). 응답 헤더 X-Tts-Voice 에 목소리 이름
@RestController
public class TtsController {

    static final int MAX_CHARS = 600;
    static final int MAX_FILES = 200;
    static final String VOICE = "ko-KR-InJoonNeural";
    static final String GEMINI_VOICE = "Charon";
    static final String STYLE = "차분하고 또렷하게, 사람이 말하듯 자연스럽게, 너무 빠르지 않게";
    private static final MediaType MP3 = MediaType.parseMediaType("audio/mpeg");
    private static final MediaType WAV = MediaType.parseMediaType("audio/wav");
    private static final Pattern HANGUL = Pattern.compile("\\p{IsHangul}");
    private static final Pattern CONTROL = Pattern.compile("[\\p{Cntrl}&&[^\\n\\t]]");
    private static final Logger log = LoggerFactory.getLogger(TtsController.class);

    public record TtsRequest(String text) {
    }

    private final String command;
    private final Path cache;
    private final long timeoutMs;
    private final List<String> keys;
    private final URI geminiUrl;
    private final List<String> models;
    private final Duration geminiTimeout;
    private final HttpClient http;
    private final JsonMapper json = JsonMapper.builder().build();
    private final Semaphore slots = new Semaphore(3); // 동시에 만드는 수 (작은 서버라 몰리지 않게)

    @Autowired
    public TtsController(Environment env) {
        this(env.getProperty("wageback.tts.command", ".tts-venv/bin/edge-tts"), Path.of(env.getProperty("wageback.tts.cache", ".tts-cache")), 15_000,
                env.getProperty("GEMINI_API_KEY", ""), env.getProperty("wageback.tts.gemini-url", "https://generativelanguage.googleapis.com"),
                env.getProperty("wageback.tts.gemini-models", "gemini-3.8-flash-tts,gemini-3.1-flash-tts-preview,gemini-3.8-flash-lite-tts,gemini-2.5-flash-preview-tts"), 30_000);
    }

    TtsController(String command, Path cache, long timeoutMs) {
        this(command, cache, timeoutMs, "", "http://127.0.0.1", "none", 1000);
    }

    TtsController(String command, Path cache, long timeoutMs, String geminiKeys, String geminiBase, String models, long geminiTimeoutMs) {
        this.command = command;
        this.cache = cache;
        this.timeoutMs = timeoutMs;
        this.keys = Arrays.stream(geminiKeys.split(",")).map(String::trim).filter(k -> !k.isEmpty()).toList();
        this.geminiUrl = URI.create(geminiBase.replaceAll("/+$", "") + "/v1beta/interactions");
        this.models = Arrays.stream(models.split(",")).map(String::trim).filter(m -> !m.isEmpty()).toList();
        this.geminiTimeout = Duration.ofMillis(geminiTimeoutMs);
        this.http = HttpClient.newBuilder().connectTimeout(geminiTimeout).build();
    }

    @PostMapping("/api/tts")
    public ResponseEntity<byte[]> speak(@RequestBody TtsRequest req) {
        String text = req == null || req.text() == null ? "" : CONTROL.matcher(req.text()).replaceAll(" ").strip();
        if (text.isEmpty() || text.length() > MAX_CHARS || !HANGUL.matcher(text).find()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TEXT", "text", "읽을 한국어 글을 600자 안으로 보내 주세요.");
        }
        Path wav = cache.resolve(hash(GEMINI_VOICE, text) + ".wav");
        Path mp3 = cache.resolve(hash(VOICE, text) + ".mp3");
        try {
            if (Files.isRegularFile(wav)) {
                return send(wav, WAV, GEMINI_VOICE, true);
            }
            if (!slots.tryAcquire()) {
                if (Files.isRegularFile(mp3)) {
                    return send(mp3, MP3, VOICE, true);
                }
                throw unavailable();
            }
            try {
                if (!keys.isEmpty()) {
                    try {
                        makeGemini(text, wav);
                        prune();
                        return send(wav, WAV, GEMINI_VOICE, false);
                    } catch (IOException | RuntimeException e) {
                        log.warn("tts gemini 실패, edge-tts 로 넘어감: {}", e instanceof IOException ? e.getMessage() : e.getClass().getSimpleName());
                    }
                }
                if (Files.isRegularFile(mp3)) {
                    return send(mp3, MP3, VOICE, true);
                }
                make(text, mp3);
                prune();
                return send(mp3, MP3, VOICE, false);
            } finally {
                slots.release();
            }
        } catch (IOException e) {
            throw unavailable();
        }
    }

    private ResponseEntity<byte[]> send(Path f, MediaType type, String voice, boolean cached) throws IOException {
        if (cached) {
            Files.setLastModifiedTime(f, FileTime.fromMillis(System.currentTimeMillis()));
        }
        log.info("tts voice={} cached={}", voice, cached);
        return ResponseEntity.ok().contentType(type).header("X-Tts-Voice", voice).cacheControl(CacheControl.noStore()).body(Files.readAllBytes(f));
    }

    // Gemini TTS (interactions). 무료 구간은 모델마다 하루 한도가 따로라 429 면 다음 모델(같은 Charon 목소리), 키가 여럿이면 다음 키로.
    // 응답은 24kHz mono 16bit WAV (헤더가 없으면 붙인다)
    private void makeGemini(String text, Path wav) throws IOException {
        int status = 0;
        for (String model : models) {
            for (String key : keys) {
                status = tryGemini(model, key, text, wav, true);
                if (status == 400) { // 말투 지시를 받지 않는 모델이 있다
                    status = tryGemini(model, key, text, wav, false);
                }
                if (status == 200) {
                    log.info("tts gemini model={}", model);
                    return;
                }
                log.warn("tts gemini http {} {}", status, model);
                if (status != 429) {
                    break; // 이 모델은 안 되니 다음 모델로
                }
            }
        }
        throw new IOException("gemini http " + status);
    }

    private int tryGemini(String model, String key, String text, Path wav, boolean style) throws IOException {
        ObjectNode body = json.createObjectNode();
        body.put("model", model);
        ObjectNode content = body.putArray("input").addObject().put("type", "user_input").putArray("content").addObject();
        content.put("type", "text").put("text", text);
        if (style) {
            content.putArray("annotations").addObject().put("type", "speech_metadata").put("style", STYLE);
        }
        body.putObject("response_format").put("type", "audio");
        body.putObject("generation_config").putArray("speech_config").addObject().put("voice", GEMINI_VOICE);
        String req = json.writeValueAsString(body);
        {
            HttpRequest r = HttpRequest.newBuilder(geminiUrl).timeout(geminiTimeout).header("Content-Type", "application/json")
                    .header("x-goog-api-key", key).POST(HttpRequest.BodyPublishers.ofString(req, StandardCharsets.UTF_8)).build();
            HttpResponse<byte[]> res;
            try {
                res = http.send(r, HttpResponse.BodyHandlers.ofByteArray());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("interrupted");
            }
            if (res.statusCode() != 200) {
                return res.statusCode();
            }
            byte[] audio = audioOf(json.readTree(res.body()));
            if (audio == null || audio.length < 100) {
                throw new IOException("gemini no audio");
            }
            if (!(audio[0] == 'R' && audio[1] == 'I' && audio[2] == 'F' && audio[3] == 'F')) {
                audio = wavHeader(audio);
            }
            Files.createDirectories(cache);
            Path part = Files.createTempFile(cache, "out", ".part");
            try {
                Files.write(part, audio);
                Files.move(part, wav, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } finally {
                Files.deleteIfExists(part);
            }
            return 200;
        }
    }

    private static byte[] audioOf(JsonNode root) {
        for (JsonNode step : root.path("steps")) {
            if (!"model_output".equals(step.path("type").asString(""))) {
                continue;
            }
            for (JsonNode c : step.path("content")) {
                String data = c.path("data").asString("");
                if ("audio".equals(c.path("type").asString("")) && !data.isEmpty()) {
                    return Base64.getDecoder().decode(data);
                }
            }
        }
        return null;
    }

    static byte[] wavHeader(byte[] pcm) {
        int rate = 24000, ch = 1, bits = 16;
        ByteArrayOutputStream o = new ByteArrayOutputStream(44 + pcm.length);
        o.writeBytes("RIFF".getBytes(StandardCharsets.US_ASCII));
        le(o, 36 + pcm.length, 4);
        o.writeBytes("WAVEfmt ".getBytes(StandardCharsets.US_ASCII));
        le(o, 16, 4);
        le(o, 1, 2);
        le(o, ch, 2);
        le(o, rate, 4);
        le(o, rate * ch * bits / 8, 4);
        le(o, ch * bits / 8, 2);
        le(o, bits, 2);
        o.writeBytes("data".getBytes(StandardCharsets.US_ASCII));
        le(o, pcm.length, 4);
        o.writeBytes(pcm);
        return o.toByteArray();
    }

    private static void le(ByteArrayOutputStream o, int v, int n) {
        for (int i = 0; i < n; i++) {
            o.write((v >> (8 * i)) & 0xff);
        }
    }

    private void make(String text, Path mp3) throws IOException {
        Files.createDirectories(cache);
        Path txt = Files.createTempFile(cache, "in", ".txt");
        Path part = Files.createTempFile(cache, "out", ".part");
        Process p = null;
        try {
            Files.writeString(txt, text, StandardCharsets.UTF_8);
            p = new ProcessBuilder(command, "--voice", VOICE, "-f", txt.toString(), "--write-media", part.toString())
                    .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
            if (!p.waitFor(timeoutMs, TimeUnit.MILLISECONDS) || p.exitValue() != 0 || Files.size(part) == 0) {
                throw unavailable();
            }
            Files.move(part, mp3, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw unavailable();
        } finally {
            if (p != null && p.isAlive()) {
                p.destroyForcibly();
            }
            Files.deleteIfExists(txt);
            Files.deleteIfExists(part);
        }
    }

    // 오래 안 쓴 것부터 지워 MAX_FILES 개만 남긴다
    void prune() throws IOException {
        List<Path> all;
        try (Stream<Path> s = Files.list(cache)) {
            all = s.filter(f -> f.getFileName().toString().endsWith(".mp3") || f.getFileName().toString().endsWith(".wav"))
                    .sorted((a, b) -> Long.compare(modified(b), modified(a))).toList();
        }
        for (Path old : all.subList(Math.min(MAX_FILES, all.size()), all.size())) {
            Files.deleteIfExists(old);
        }
    }

    private static long modified(Path f) {
        try {
            return Files.getLastModifiedTime(f).toMillis();
        } catch (IOException e) {
            return 0;
        }
    }

    static String hash(String voice, String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((voice + "\n" + text).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static ApiException unavailable() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "TTS_UNAVAILABLE", null, "지금은 소리를 만들 수 없어요.");
    }
}
