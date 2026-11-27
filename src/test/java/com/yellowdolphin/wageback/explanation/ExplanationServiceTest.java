package com.yellowdolphin.wageback.explanation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
    void 자리표시자_앞뒤에_겹쳐_쓴_단위는_지운다() {
        var ko = Map.of("{{A.amount}}", "206,698원", "{{A.month}}", "2026년 8월");
        assertEquals("2026년 8월에 206,698원이에요.", ExplanationService.dropRepeatedUnits("{{A.month}} 월에 {{A.amount}}원이에요.", ko)
                .replace("{{A.month}}", "2026년 8월").replace("{{A.amount}}", "206,698원"));
        assertEquals("{{A.amount}} 이며", ExplanationService.dropRepeatedUnits("{{A.amount}} 원 이며", ko));
        // 단위로 시작하는 낱말은 그대로 ("월급")
        assertEquals("{{A.month}} 월급", ExplanationService.dropRepeatedUnits("{{A.month}} 월급", ko));
        var vi = Map.of("{{A.amount}}", "206,698 KRW", "{{A.month}}", "tháng 8/2026");
        assertEquals("cho {{A.month}} là {{A.amount}}.", ExplanationService.dropRepeatedUnits("cho tháng {{A.month}} là {{A.amount}} KRW.", vi));
    }

    @Test
    void 자리표시자_뒤_조사를_값에_맞춘다() {
        var ko = Map.of("{{A.amount}}", "924,000원", "{{A.month}}", "2026년 9월");
        assertEquals("{{A.amount}}이 {{A.month}}에 해당해요.", ExplanationService.fixParticles("{{A.amount}}가 {{A.month}}에 해당해요.", ko));
        assertEquals("{{A.amount}}을 받아요. {{A.amount}}은 {{A.month}}로", ExplanationService.fixParticles("{{A.amount}}를 받아요. {{A.amount}}는 {{A.month}}로", ko));
        assertEquals("{{A.amount}}으로 계산해요", ExplanationService.fixParticles("{{A.amount}}로 계산해요", ko));
        // 조사가 아닌 글자(가는 · 가장)와 다른 언어 값은 그대로
        assertEquals("{{A.amount}}가장 큰", ExplanationService.fixParticles("{{A.amount}}가장 큰", ko));
        assertEquals("{{A.amount}}가 ", ExplanationService.fixParticles("{{A.amount}}가 ", Map.of("{{A.amount}}", "924,000 KRW")));
    }

    @Test
    void 한국어_답에_한자가_섞이면_재생성한다() {
        // 작은 모델이 「일한工的 금액」 처럼 중국어 글자를 섞어 쓴다
        String han = GOOD.replace("더 일한 시간은", "더 일한工的 시간은");
        var e = service(han, GOOD).explain(RESULT, "ko");
        assertEquals(2, prompts.size());
        assertFalse(e.fallback());
        assertEquals("2026년 9월에 더 일한 시간은 161,976원이에요.", e.items().get(0).text());
    }

    @Test
    void 단위가_아닌_글자는_그대로_둔다() {
        // "원래" · "월급" 처럼 원 · 월로 시작하는 낱말은 지우지 않는다
        String words = GOOD.replace("{{OVERTIME.amount}}이에요", "{{OVERTIME.amount}} 원래 받을 돈이에요")
                .replace("{{OVERTIME.month}}에", "{{OVERTIME.month}} 월급에");
        var e = service(words).explain(RESULT, "ko");
        assertEquals("2026년 9월 월급에 더 일한 시간은 161,976원 원래 받을 돈이에요.", e.items().get(0).text());
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
        assertEquals("2026년 9월 연장근로수당: 받아야 할 돈은 161,976원이에요.", e.items().get(0).text());
        assertTrue(e.items().get(1).text().contains("입력한 정보가 없어"));
        assertTrue(e.items().get(2).text().contains("월급 안에"));
        assertEquals("아직 받지 못한 돈은 모두 2,721,300원이에요.", e.summary());
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
