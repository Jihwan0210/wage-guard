package com.yellowdolphin.wageback.tts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;

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
        Files.write(dir.resolve(TtsController.hash(text) + ".mp3"), new byte[] { 1, 2, 3 });
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
}
