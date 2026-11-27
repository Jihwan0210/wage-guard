package com.yellowdolphin.wageback.tts;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Stream;

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

// 소리로 읽어 주기: 화면 글을 영상 나레이션과 같은 목소리(edge-tts ko-KR-InJoonNeural)의 mp3 로 만들어 보낸다.
// 크롬에는 이 목소리가 없어서 서버가 만든다. 서버 설치는 deploy 의 add_wageback_tts.sh (앱 폴더의 .tts-venv).
// 글은 명령줄 인자로 넘기지 않고 임시 파일로 넘긴다. 글과 결과는 로그에 남기지 않는다.
// 같은 글은 해시 이름으로 디스크에 두고 다시 쓴다(최대 200개, 오래 안 쓴 것부터 지움). 15초 안에 못 만들면 503
@RestController
public class TtsController {

    static final int MAX_CHARS = 600;
    static final int MAX_FILES = 200;
    static final String VOICE = "ko-KR-InJoonNeural";
    private static final MediaType MP3 = MediaType.parseMediaType("audio/mpeg");
    private static final Pattern HANGUL = Pattern.compile("\\p{IsHangul}");
    private static final Pattern CONTROL = Pattern.compile("[\\p{Cntrl}&&[^\\n\\t]]");

    public record TtsRequest(String text) {
    }

    private final String command;
    private final Path cache;
    private final long timeoutMs;
    private final Semaphore slots = new Semaphore(3); // 동시에 만드는 수 (작은 서버라 프로세스가 몰리지 않게)

    @Autowired
    public TtsController(Environment env) {
        this(env.getProperty("wageback.tts.command", ".tts-venv/bin/edge-tts"), Path.of(env.getProperty("wageback.tts.cache", ".tts-cache")), 15_000);
    }

    TtsController(String command, Path cache, long timeoutMs) {
        this.command = command;
        this.cache = cache;
        this.timeoutMs = timeoutMs;
    }

    @PostMapping("/api/tts")
    public ResponseEntity<byte[]> speak(@RequestBody TtsRequest req) {
        String text = req == null || req.text() == null ? "" : CONTROL.matcher(req.text()).replaceAll(" ").strip();
        if (text.isEmpty() || text.length() > MAX_CHARS || !HANGUL.matcher(text).find()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TEXT", "text", "읽을 한국어 글을 600자 안으로 보내 주세요.");
        }
        Path mp3 = cache.resolve(hash(text) + ".mp3");
        try {
            if (Files.isRegularFile(mp3)) {
                Files.setLastModifiedTime(mp3, FileTime.fromMillis(System.currentTimeMillis()));
            } else {
                if (!slots.tryAcquire()) {
                    throw unavailable();
                }
                try {
                    make(text, mp3);
                    prune();
                } finally {
                    slots.release();
                }
            }
            return ResponseEntity.ok().contentType(MP3).cacheControl(CacheControl.noStore()).body(Files.readAllBytes(mp3));
        } catch (IOException e) {
            throw unavailable();
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
            all = s.filter(f -> f.getFileName().toString().endsWith(".mp3"))
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

    static String hash(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((VOICE + "\n" + text).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static ApiException unavailable() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "TTS_UNAVAILABLE", null, "지금은 소리를 만들 수 없어요.");
    }
}
