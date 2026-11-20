package com.yellowdolphin.wageback.explanation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.yellowdolphin.wageback.common.ApiException;
import com.yellowdolphin.wageback.common.llm.LlmClient;
import com.yellowdolphin.wageback.common.llm.LlmException;

import tools.jackson.databind.json.JsonMapper;

class ExplanationServiceTest {

    static final String RESULT = """
            {"id":"calc_1","summary":{"totalUnpaid":2721300},
             "items":[
              {"code":"OVERTIME","label":"연장근로수당","month":"2026-09","status":"CALCULATED","estimated":false,"amount":161976},
              {"code":"HOLIDAY","label":"휴일근로수당","month":"2026-09","status":"NO_DATA","amount":null},
              {"code":"WEEKLY_HOLIDAY","label":"주휴수당","month":"2026-09","status":"INCLUDED_IN_WAGE","amount":null}]}
            """;

    static final String GOOD = """
            {"items":[{"key":"OVERTIME","text":"{{OVERTIME.month}}에 더 일한 시간은 {{OVERTIME.amount}}이에요."},
              {"key":"HOLIDAY","text":"휴일 근무 정보가 없어요."},
              {"key":"WEEKLY_HOLIDAY","text":"주휴수당은 월급에 들어 있어요."}],
             "summary":"모두 {{TOTAL.unpaid}}을 못 받았어요."}
            """;

    static final String WITH_DIGIT = GOOD.replace("더 일한 시간은", "10시간 더 일해서");

    final List<String> prompts = new ArrayList<>();

    ExplanationService service(String... answers) {
        LlmClient fake = (system, prompt, file) -> {
            prompts.add(prompt);
            return answers[Math.min(prompts.size() - 1, answers.length - 1)];
        };
        return new ExplanationService(fake, new JsonMapper());
    }

    @Test
    void 서버가_숫자를_채운다() {
        var e = service(GOOD).explain(RESULT, "ko");
        assertFalse(e.fallback());
        assertEquals("2026년 9월에 더 일한 시간은 161,976원이에요.", e.items().get(0).text());
        assertEquals("모두 2,721,300원을 못 받았어요.", e.summary());
        // LLM 에는 금액이 가지 않는다
        assertFalse(prompts.get(0).contains("161976"));
        assertTrue(prompts.get(0).contains("{{OVERTIME.amount}}"));
    }

    @Test
    void 자리표시자_밖_숫자면_재생성한다() {
        var e = service(WITH_DIGIT, WITH_DIGIT, GOOD).explain(RESULT, "ko");
        assertEquals(3, prompts.size());
        assertFalse(e.fallback());
    }

    @Test
    void 재생성_두번_실패하면_고정문구() {
        var e = service(WITH_DIGIT).explain(RESULT, "ko");
        assertEquals(3, prompts.size());
        assertTrue(e.fallback());
        assertEquals("2026년 9월 연장근로수당 161,976원을 받아야 하는 것으로 계산됐어요.", e.items().get(0).text());
        assertTrue(e.items().get(1).text().contains("입력한 정보가 없어"));
        assertTrue(e.items().get(2).text().contains("월급 안에"));
        assertEquals("모두 합쳐 2,721,300원을 아직 받지 못한 것으로 계산됐어요.", e.summary());
    }

    @Test
    void 모르는_자리표시자나_빠진_항목도_실패로_본다() {
        String unknown = GOOD.replace("{{OVERTIME.amount}}", "{{NIGHT.amount}}");
        String missing = GOOD.replace("\"key\":\"HOLIDAY\"", "\"key\":\"X\"");
        assertTrue(service(unknown).explain(RESULT, "ko").fallback());
        prompts.clear();
        assertTrue(service(missing).explain(RESULT, "ko").fallback());
    }

    @Test
    void 영어_고정문구() {
        var e = service("JSON 아님").explain(RESULT, "en");
        assertTrue(e.fallback());
        assertEquals("September 2026 Overtime pay: calculated as 161,976 KRW.", e.items().get(0).text());
    }

    @Test
    void 새_항목도_고정문구에_나온다() {
        String result = """
                {"id":"calc_2","summary":{"totalUnpaid":3929081},
                 "items":[{"code":"DISMISSAL_NOTICE","label":"해고예고수당","month":null,"status":"CALCULATED","estimated":false,"amount":2640000},
                  {"code":"ANNUAL_LEAVE","label":"못 쓴 연차수당","month":null,"status":"CALCULATED","estimated":true,"amount":704000},
                  {"code":"SHUTDOWN_ALLOWANCE","label":"휴업수당","month":null,"status":"CALCULATED","estimated":true,"amount":529081},
                  {"code":"DELAY_INTEREST","label":"늦게 준 돈의 이자(지연이자)","month":null,"status":"CALCULATED","estimated":true,"amount":56000}]}
                """;
        var e = service("JSON 아님").explain(result, "vi");
        assertTrue(e.fallback());
        assertEquals(4, e.items().size());
        assertTrue(e.items().get(0).text().contains("Tiền báo trước khi sa thải"), e.items().get(0).text());
        assertTrue(e.items().get(1).text().contains("Tiền phép năm chưa dùng"));
        assertTrue(e.items().get(2).text().contains("Tiền ngừng việc"));
        assertTrue(e.items().get(3).text().contains("Tiền lãi trả chậm") && e.items().get(3).text().contains("56,000"), e.items().get(3).text());
    }

    @Test
    void LLM_장애면_503() {
        LlmClient down = (s, p, f) -> {
            throw new LlmException("키 없음");
        };
        var ex = assertThrows(ApiException.class, () -> new ExplanationService(down, new JsonMapper()).explain(RESULT, "ko"));
        assertEquals(503, ex.getStatus().value());
        assertEquals("AI_UNAVAILABLE", ex.getCode());
    }
}
