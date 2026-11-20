package com.yellowdolphin.wageback.common.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

import tools.jackson.databind.json.JsonMapper;

// 가짜 Groq 서버로 요청 모양 · 응답 읽기 · 키 없을 때 넘기기를 시험한다
class GroqLlmClientTest {

    private final List<String> bodies = new CopyOnWriteArrayList<>();
    private final List<String> auth = new CopyOnWriteArrayList<>();
    private HttpServer server;

    private GroqLlmClient client(int status) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/openai/v1/chat/completions", ex -> {
            auth.add(ex.getRequestHeaders().getFirst("authorization"));
            bodies.add(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] out = """
                    {"choices":[{"message":{"role":"assistant","content":"답"}}]}""".getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(status, out.length);
            ex.getResponseBody().write(out);
            ex.close();
        });
        server.start();
        return new GroqLlmClient(" g1 ", "text-model", "vision-model", "http://127.0.0.1:" + server.getAddress().getPort(), 5,
                JsonMapper.builder().build());
    }

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void 글만_있으면_글_모델_사진이_있으면_사진_모델() throws IOException {
        GroqLlmClient c = client(200);
        assertThat(c.ask("시스템", "질문", null)).isEqualTo("답");
        assertThat(auth.get(0)).isEqualTo("Bearer g1");
        assertThat(bodies.get(0)).contains("\"model\":\"text-model\"").contains("{\"role\":\"system\",\"content\":\"시스템\"}")
                .contains("{\"role\":\"user\",\"content\":\"질문\"}");

        assertThat(c.ask("s", "p", new LlmClient.Attachment("image/jpeg", new byte[] { 1, 2, 3 }))).isEqualTo("답");
        assertThat(bodies.get(1)).contains("\"model\":\"vision-model\"")
                .contains("{\"type\":\"image_url\",\"image_url\":{\"url\":\"data:image/jpeg;base64,AQID\"}}");
    }

    @Test
    void PDF는_보내지_않고_오류면_LlmException() throws IOException {
        GroqLlmClient c = client(429);
        assertThatThrownBy(() -> c.ask("s", "p", new LlmClient.Attachment("application/pdf", new byte[] { 1 })))
                .isInstanceOf(LlmException.class).hasMessageContaining("PDF");
        assertThat(bodies).isEmpty();
        assertThatThrownBy(() -> c.ask("s", "p", null)).isInstanceOf(LlmException.class).hasMessageContaining("429");
    }

    @Test
    void 앞이_실패하면_다음_것에_묻고_다_실패하면_마지막_오류() {
        LlmClient down = (s, p, f) -> {
            throw new LlmException("첫째 실패");
        };
        assertThat(LlmConfig.fallback(List.of(down, (s, p, f) -> "둘째 답")).ask("s", "p", null)).isEqualTo("둘째 답");
        LlmClient alsoDown = (s, p, f) -> {
            throw new LlmException("둘째 실패");
        };
        assertThatThrownBy(() -> LlmConfig.fallback(List.of(down, alsoDown)).ask("s", "p", null)).hasMessage("둘째 실패");
    }
}
