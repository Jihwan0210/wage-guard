package com.yellowdolphin.wageback.tts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;

import com.sun.net.httpserver.HttpServer;

import com.yellowdolphin.wageback.common.ApiException;

// 목소리 프로그램 없이 글 검사, 캐시, 실패 처리를 시험한다
class TtsControllerTest {

    @TempDir
    Path dir;

    private TtsController tts() {
        return new TtsController("no-such-edge-tts-command", dir, 1000);
    }

    private static HttpStatus status(Runnable r) {
        try {
            r.run();
        } catch (ApiException e) {
            return e.getStatus();
        }
        return HttpStatus.OK;
    }

    @Test
    void rejectsEmptyTooLongOrNonKorean() {
        TtsController t = tts();
        assertThat(status(() -> t.speak(new TtsController.TtsRequest("")))).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(status(() -> t.speak(new TtsController.TtsRequest(null)))).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(status(() -> t.speak(new TtsController.TtsRequest("hello world")))).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(status(() -> t.speak(new TtsController.TtsRequest("가".repeat(601))))).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void servesCachedMp3WithoutRunningCommand() throws IOException {
        String text = "아직 받지 못한 돈은 모두 549만 5,561원이에요.";
        Files.write(dir.resolve(TtsController.hash(TtsController.VOICE, text) + ".mp3"), new byte[] { 1, 2, 3 });
        var res = tts().speak(new TtsController.TtsRequest(text));
        assertThat(res.getStatusCode().value()).isEqualTo(200);
        assertThat(res.getHeaders().getContentType().toString()).isEqualTo("audio/mpeg");
        assertThat(res.getBody()).containsExactly(1, 2, 3);
    }

    @Test
    void missingCommandIsServiceUnavailableAndLeavesNoTempFiles() throws IOException {
        assertThatThrownBy(() -> tts().speak(new TtsController.TtsRequest("읽어 주세요; rm -rf /")))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        try (var s = Files.list(dir)) {
            assertThat(s.toList()).isEmpty();
        }
    }

    @Test
    void pruneKeepsNewest200() throws IOException {
        for (int i = 0; i < 205; i++) {
            Path f = Files.write(dir.resolve("f" + i + ".mp3"), new byte[] { 1 });
            Files.setLastModifiedTime(f, FileTime.fromMillis(1_000_000L + i * 1000L));
        }
        tts().prune();
        try (var s = Files.list(dir)) {
            assertThat(s.count()).isEqualTo(TtsController.MAX_FILES);
        }
        assertThat(dir.resolve("f0.mp3")).doesNotExist();
        assertThat(dir.resolve("f204.mp3")).exists();
    }

    // 가짜 Gemini 서버: 상태 코드와 PCM 을 돌려준다
    private HttpServer fakeGemini(int code, AtomicReference<String> seen) throws IOException {
        HttpServer srv = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        srv.createContext("/v1beta/interactions", ex -> {
            seen.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            String pcm = Base64.getEncoder().encodeToString(new byte[200]);
            int c = seen.get().contains("\"model\":\"limited\"") ? 429 : seen.get().contains("\"model\":\"nostyle\"") && seen.get().contains("annotations") ? 400 : code;
            byte[] out = (c == 200 ? "{\"steps\":[{\"type\":\"model_output\",\"content\":[{\"type\":\"audio\",\"data\":\"" + pcm + "\"}]}]}" : "{}")
                    .getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(c, out.length);
            ex.getResponseBody().write(out);
            ex.close();
        });
        srv.start();
        return srv;
    }

    @Test
    void geminiCharonFirstAndCached() throws IOException {
        AtomicReference<String> seen = new AtomicReference<>();
        HttpServer srv = fakeGemini(200, seen);
        try {
            TtsController t = new TtsController("no-such-edge-tts-command", dir, 1000, "k1", "http://127.0.0.1:" + srv.getAddress().getPort(), "m", 5000);
            String text = "아직 받지 못한 돈이에요.";
            var res = t.speak(new TtsController.TtsRequest(text));
            assertThat(res.getHeaders().getContentType().toString()).isEqualTo("audio/wav");
            assertThat(res.getHeaders().getFirst("X-Tts-Voice")).isEqualTo("Charon");
            assertThat(new String(res.getBody(), 0, 4, StandardCharsets.US_ASCII)).isEqualTo("RIFF");
            assertThat(res.getBody()).hasSize(244);
            assertThat(seen.get()).contains("\"voice\":\"Charon\"").contains(TtsController.STYLE);
            assertThat(dir.resolve(TtsController.hash("Charon", text) + ".wav")).exists();
            seen.set(null);
            assertThat(t.speak(new TtsController.TtsRequest(text)).getHeaders().getFirst("X-Tts-Voice")).isEqualTo("Charon");
            assertThat(seen.get()).isNull();
        } finally {
            srv.stop(0);
        }
    }

    @Test
    void geminiLimitFallsBackToEdgeVoice() throws IOException {
        HttpServer srv = fakeGemini(429, new AtomicReference<>());
        try {
            TtsController t = new TtsController("no-such-edge-tts-command", dir, 1000, "k1,k2", "http://127.0.0.1:" + srv.getAddress().getPort(), "m", 5000);
            String text = "퇴직금이에요.";
            Files.write(dir.resolve(TtsController.hash(TtsController.VOICE, text) + ".mp3"), new byte[] { 7 });
            var res = t.speak(new TtsController.TtsRequest(text));
            assertThat(res.getHeaders().getContentType().toString()).isEqualTo("audio/mpeg");
            assertThat(res.getHeaders().getFirst("X-Tts-Voice")).isEqualTo(TtsController.VOICE);
            assertThat(status(() -> t.speak(new TtsController.TtsRequest("다른 글이에요.")))).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        } finally {
            srv.stop(0);
        }
    }

    @Test
    void dailyLimitOnOneModelTriesNextModel() throws IOException {
        AtomicReference<String> seen = new AtomicReference<>();
        HttpServer srv = fakeGemini(200, seen);
        try {
            TtsController t = new TtsController("no-such-edge-tts-command", dir, 1000, "k1", "http://127.0.0.1:" + srv.getAddress().getPort(), "limited,nostyle", 5000);
            var res = t.speak(new TtsController.TtsRequest("다음 모델로 읽어요."));
            assertThat(res.getHeaders().getFirst("X-Tts-Voice")).isEqualTo("Charon");
            assertThat(seen.get()).contains("\"model\":\"nostyle\"").doesNotContain("annotations");
        } finally {
            srv.stop(0);
        }
    }
}
