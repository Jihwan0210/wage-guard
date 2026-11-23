package com.yellowdolphin.wageback.common.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;
import com.yellowdolphin.wageback.complaint.ComplaintController.Company;
import com.yellowdolphin.wageback.complaint.ComplaintController.Person;
import com.yellowdolphin.wageback.complaint.ComplaintController.Request;
import com.yellowdolphin.wageback.complaint.ComplaintService;
import com.yellowdolphin.wageback.explanation.ExplanationService;

import tools.jackson.databind.json.JsonMapper;

// 가짜 서버로 OpenAI 호환 제공처(로컬 Ollama · OpenRouter · NVIDIA · Mistral)의 요청 모양과,
// 제공처가 바뀌어도 '금액은 룰 엔진만' · '이름과 주소는 보내지 않는다'가 지켜지는지 시험한다
class OpenAiCompatLlmClientTest {

    static final JsonMapper JSON = JsonMapper.builder().build();
    static final String RESULT = """
            {"period":{"start":"2025-03-03","end":"2026-10-31"},"summary":{"totalUnpaid":618856},
             "items":[{"code":"BASE_WAGE","label":"못 받은 월급","month":"2026-09","amount":456880},
              {"code":"OVERTIME","label":"연장근로수당","month":"2026-09","amount":161976}]}
            """;

    private final List<String> bodies = new CopyOnWriteArrayList<>();
    private final List<String> auth = new CopyOnWriteArrayList<>();
    private final List<String> answers = new ArrayList<>(); // 차례로 돌려줄 답. 마지막 답은 계속 쓴다
    private HttpServer server;

    private String url(int status) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", ex -> {
            auth.add(String.valueOf(ex.getRequestHeaders().getFirst("authorization")));
            bodies.add(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            String answer = answers.get(Math.min(bodies.size(), answers.size()) - 1);
            byte[] out = JSON.writeValueAsBytes(JSON.createObjectNode().set("choices",
                    JSON.createArrayNode().add(JSON.createObjectNode().set("message", JSON.createObjectNode().put("content", answer)))));
            ex.sendResponseHeaders(status, out.length);
            ex.getResponseBody().write(out);
            ex.close();
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
    }

    private OpenAiCompatLlmClient client(String key, boolean local, int status, String... answer) throws IOException {
        answers.addAll(List.of(answer));
        return new OpenAiCompatLlmClient(key, "text-model", "vision-model", url(status), 5, local, JSON);
    }

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void 키가_있으면_Bearer로_보내고_사진이_있으면_사진_모델() throws IOException {
        OpenAiCompatLlmClient c = client(" k1 ", false, 200, "답");
        assertThat(c.ask("시스템", "질문", null)).isEqualTo("답");
        assertThat(auth.get(0)).isEqualTo("Bearer k1");
        assertThat(bodies.get(0)).contains("\"model\":\"text-model\"").contains("{\"role\":\"system\",\"content\":\"시스템\"}")
                .contains("{\"role\":\"user\",\"content\":\"질문\"}").doesNotContain("reasoning_effort");

        assertThat(c.ask("s", "p", new LlmClient.Attachment("image/jpeg", new byte[] { 1, 2, 3 }))).isEqualTo("답");
        assertThat(bodies.get(1)).contains("\"model\":\"vision-model\"")
                .contains("{\"type\":\"image_url\",\"image_url\":{\"url\":\"data:image/jpeg;base64,AQID\"}}");
    }

    @Test
    void 로컬은_키_없이_보내고_생각_과정은_끄고_답에서도_뺀다() throws IOException {
        OpenAiCompatLlmClient c = client("", true, 200, "<think>\n계산해 보면 123\n</think>\n\n답");
        assertThat(c.ask("s", "p", null)).isEqualTo("답");
        assertThat(auth.get(0)).isEqualTo("null");
        assertThat(bodies.get(0)).contains("\"reasoning_effort\":\"none\"");
    }

    @Test
    void 키가_필요한_곳에_키가_없으면_보내지_않고_PDF도_보내지_않는다() throws IOException {
        OpenAiCompatLlmClient noKey = client("", false, 200, "답");
        assertThatThrownBy(() -> noKey.ask("s", "p", null)).isInstanceOf(LlmException.class).hasMessageContaining("키");
        OpenAiCompatLlmClient local = new OpenAiCompatLlmClient("", "t", "v", "http://127.0.0.1:" + server.getAddress().getPort() + "/v1", 5, true, JSON);
        assertThatThrownBy(() -> local.ask("s", "p", new LlmClient.Attachment("application/pdf", new byte[] { 1 })))
                .isInstanceOf(LlmException.class).hasMessageContaining("PDF");
        assertThat(bodies).isEmpty();
    }

    @Test
    void 응답_오류면_LlmException() throws IOException {
        OpenAiCompatLlmClient c = client("k", false, 429, "답");
        assertThatThrownBy(() -> c.ask("s", "p", null)).isInstanceOf(LlmException.class).hasMessageContaining("429");
    }

    @Test
    void 해설에_숫자를_지어내면_버리고_고정_문구로_바꾼다() throws IOException {
        // 금액을 직접 쓴 답 (자리표시자 밖 숫자) → 세 번 모두 버리고 서버가 만든 고정 문구
        OpenAiCompatLlmClient c = client("", true, 200, """
                {"items":[{"key":"BASE_WAGE","text":"못 받은 월급은 500,000원이에요."},{"key":"OVERTIME","text":"연장수당은 {{OVERTIME.amount}}이에요."}],
                 "summary":"모두 {{TOTAL.unpaid}}이에요."}""");
        var e = new ExplanationService(c, JSON).explain(RESULT, "ko");
        assertThat(bodies).hasSize(3);
        assertThat(e.fallback()).isTrue();
        assertThat(e.items().get(0).text()).contains("456,880원").doesNotContain("500,000");
        assertThat(e.summary()).contains("618,856원");
    }

    @Test
    void 해설에_자리표시자만_쓰면_서버가_금액을_채운다() throws IOException {
        OpenAiCompatLlmClient c = client("", true, 200, """
                {"items":[{"key":"BASE_WAGE","text":"{{BASE_WAGE.month}} 월급에서 {{BASE_WAGE.amount}}을 아직 못 받았어요."},
                  {"key":"OVERTIME","text":"더 일한 시간의 수당 {{OVERTIME.amount}}도 받아야 해요."}],
                 "summary":"모두 {{TOTAL.unpaid}}을 받아야 해요."}""");
        var e = new ExplanationService(c, JSON).explain(RESULT, "ko");
        assertThat(e.fallback()).isFalse();
        assertThat(e.items().get(0).text()).isEqualTo("2026년 9월 월급에서 456,880원을 아직 못 받았어요.");
        assertThat(e.summary()).isEqualTo("모두 618,856원을 받아야 해요.");
        // 금액은 LLM 에 가지 않는다
        assertThat(bodies.get(0)).doesNotContain("456880").doesNotContain("456,880").doesNotContain("618856").doesNotContain("618,856");
    }

    @Test
    void 진정서_글에서_이름_주소_연락처는_보내지_않고_새_숫자가_생기면_원문을_쓴다() throws IOException {
        OpenAiCompatLlmClient c = client("", true, 200, "진정인은 9월부터 급여 300만원을 받지 못하였습니다.");
        String facts = "홍길동은 9월부터 급여 일부만 받았고 김사장이 다음 달에 주겠다고 했습니다.";
        var d = new ComplaintService(c, JSON).draft(RESULT, new Request(new Person("홍길동", "010-1111-2222", "서울시 어딘가"),
                new Company("○○식당", "김사장", "서울시 가게", "02-000-0000"), "주방 보조", false, facts, "ko"));
        assertThat(bodies).isNotEmpty();
        for (String body : bodies) {
            assertThat(body).doesNotContain("홍길동").doesNotContain("김사장").doesNotContain("010-1111-2222").doesNotContain("서울시")
                    .doesNotContain("○○식당").doesNotContain("02-000-0000");
        }
        // 답에 원문에 없던 300 이 생겼으므로 사용자가 쓴 글 그대로
        assertThat(d.draftKo().sections().get(4).body()).endsWith("\n" + facts).doesNotContain("300");
    }
}
