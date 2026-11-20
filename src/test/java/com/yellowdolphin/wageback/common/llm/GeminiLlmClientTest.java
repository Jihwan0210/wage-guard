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

// 가짜 Gemini 서버로 요청 모양 · 응답 읽기 · 키 바꾸기를 시험한다
class GeminiLlmClientTest {

    private static final String OK = """
            {"candidates":[{"content":{"parts":[{"text":"생각 중","thought":true},{"text":"{\\"a\\":"},{"text":"1}"}]}}]}""";

    private final List<String> keys = new CopyOnWriteArrayList<>();
    private final List<String> bodies = new CopyOnWriteArrayList<>();
    private HttpServer server;

    // 429 를 돌려줄 키 목록을 받아 가짜 서버를 띄운다
    private GeminiLlmClient client(String apiKeys, List<String> limited, int otherStatus) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1beta/models/test-model:generateContent", ex -> {
            String key = ex.getRequestHeaders().getFirst("x-goog-api-key");
            keys.add(key);
            bodies.add(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            int status = limited.contains(key) ? 429 : otherStatus;
            byte[] out = (status == 200 ? OK : "{}").getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(status, out.length);
            ex.getResponseBody().write(out);
            ex.close();
        });
        server.start();
        return new GeminiLlmClient(apiKeys, "test-model", "http://127.0.0.1:" + server.getAddress().getPort() + "/", 5,
                JsonMapper.builder().build());
    }

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void 요청에_시스템_문장_첨부_질문을_넣고_생각_조각은_빼고_읽는다() throws IOException {
        GeminiLlmClient c = client("k1", List.of(), 200);
        String answer = c.ask("시스템", "질문", new LlmClient.Attachment("image/png", new byte[] { 1, 2, 3 }));
        assertThat(answer).isEqualTo("{\"a\":1}");
        assertThat(bodies.get(0)).contains("\"systemInstruction\":{\"parts\":[{\"text\":\"시스템\"}]}")
                .contains("\"inlineData\":{\"mimeType\":\"image/png\",\"data\":\"AQID\"}")
                .contains("{\"text\":\"질문\"}");
    }

    @Test
    void 키_두_개는_번갈아_쓰고_429면_다음_키로_다시_보낸다() throws IOException {
        GeminiLlmClient c = client(" k1 , k2 ", List.of("k1"), 200);
        assertThat(c.ask("s", "p", null)).isEqualTo("{\"a\":1}"); // k1 429 → k2
        assertThat(c.ask("s", "p", null)).isEqualTo("{\"a\":1}"); // 이번엔 k2 부터
        assertThat(keys).containsExactly("k1", "k2", "k2");
    }

    @Test
    void 모든_키가_429거나_다른_오류면_LlmException() throws IOException {
        GeminiLlmClient all = client("k1,k2", List.of("k1", "k2"), 200);
        assertThatThrownBy(() -> all.ask("s", "p", null)).isInstanceOf(LlmException.class).hasMessageContaining("429");
        stop();
        keys.clear();
        GeminiLlmClient bad = client("k1,k2", List.of(), 400);
        assertThatThrownBy(() -> bad.ask("s", "p", null)).isInstanceOf(LlmException.class).hasMessageContaining("400");
        assertThat(keys).hasSize(1); // 키 문제가 아니면 다음 키로 안 보낸다
    }

    @Test
    void 키가_없으면_LlmException() {
        GeminiLlmClient c = new GeminiLlmClient(" , ", "m", "http://127.0.0.1:1", 1, JsonMapper.builder().build());
        assertThatThrownBy(() -> c.ask("s", "p", null)).isInstanceOf(LlmException.class).hasMessageContaining("키");
    }
}
