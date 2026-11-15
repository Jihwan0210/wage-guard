package com.yellowdolphin.wageback.complaint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.yellowdolphin.wageback.common.llm.LlmException;
import com.yellowdolphin.wageback.complaint.ComplaintController.Company;
import com.yellowdolphin.wageback.complaint.ComplaintController.Person;
import com.yellowdolphin.wageback.complaint.ComplaintController.Request;

import tools.jackson.databind.json.JsonMapper;

class ComplaintServiceTest {

    static final String RESULT = """
            {"period":{"start":"2025-03-03","end":"2026-10-31"},"summary":{"totalUnpaid":618856},
             "items":[{"code":"BASE_WAGE","label":"못 받은 월급","month":"2026-09","amount":456880},
              {"code":"OVERTIME","label":"연장근로수당","month":"2026-09","amount":161976},
              {"code":"HOLIDAY","label":"휴일근로수당","month":"2026-09","status":"NO_DATA","amount":null}]}
            """;

    static Request request(String lang) {
        return new Request(new Person("홍길동", "010-1111-2222", "서울시 어딘가"), new Company("○○식당", "김사장", "서울시 가게", "02-000-0000"),
                "주방 보조", false, "홍길동은 9월부터 급여 일부만 받았고 김사장이 다음 달에 주겠다고 했습니다.", lang);
    }

    @Test
    void 인적사항은_LLM에_보내지_않고_템플릿에_넣는다() {
        List<String> sent = new ArrayList<>();
        var service = new ComplaintService((s, p, f) -> {
            sent.add(p);
            return "다듬은 문단입니다.";
        }, new JsonMapper());
        var d = service.draft(RESULT, request("vi"));

        for (String p : sent) {
            assertFalse(p.contains("홍길동") || p.contains("김사장") || p.contains("010-1111-2222") || p.contains("서울시"), p);
        }
        assertEquals("홍길동 · 010-1111-2222\n서울시 어딘가", d.draftKo().sections().get(0).body());
        assertEquals("2026년 9월분 미지급 임금 456,880원\n2026년 9월분 연장근로수당 161,976원\n합계 618,856원", d.draftKo().sections().get(3).body());
        assertTrue(d.draftKo().sections().get(4).body().endsWith("\n다듬은 문단입니다."));
        assertEquals("vi", d.translation().lang());
        assertEquals(6, d.translation().sections().size());
        assertEquals(ComplaintService.DISCLAIMER, d.disclaimer());
    }

    @Test
    void 새_항목도_체불_내역_표에_빠짐없이_나온다() {
        String result = """
                {"period":{"start":"2025-01-02","end":"2026-10-31"},"summary":{"totalUnpaid":3929081},
                 "items":[{"code":"DISMISSAL_NOTICE","label":"해고예고수당","month":null,"amount":2640000},
                  {"code":"ANNUAL_LEAVE","label":"못 쓴 연차수당","month":null,"amount":704000},
                  {"code":"SHUTDOWN_ALLOWANCE","label":"휴업수당","month":null,"amount":529081},
                  {"code":"DELAY_INTEREST","label":"늦게 준 돈의 이자(지연이자)","month":null,"amount":56000},
                  {"code":"MIN_WAGE_SHORTFALL","label":"최저임금 차액","month":"2026-10","amount":0}]}
                """;
        var service = new ComplaintService((s, p, f) -> "다듬은 문단입니다.", new JsonMapper());
        assertEquals("해고예고수당 2,640,000원\n못 쓴 연차수당 704,000원\n휴업수당 529,081원\n지연이자(연 20%) 56,000원\n2026년 10월분 최저임금 차액 0원\n합계 3,929,081원",
                service.draft(result, request("ko")).draftKo().sections().get(3).body());
        String en = service.draft(result, request("en")).translation().sections().get(3).body();
        assertTrue(en.contains("Dismissal notice pay 2,640,000 KRW") && en.contains("Unused paid leave pay") && en.contains("Pay for days the company stopped work")
                && en.contains("Interest for late payment (20% a year) 56,000 KRW"), en);
    }

    @Test
    void LLM_실패면_원문_그대로() {
        var service = new ComplaintService((s, p, f) -> {
            throw new LlmException("키 없음");
        }, new JsonMapper());
        var d = service.draft(RESULT, request("ko"));
        assertTrue(d.draftKo().sections().get(4).body().endsWith(request("ko").extraFacts()));
        assertNull(d.translation());
    }

    @Test
    void LLM이_숫자를_만들면_원문_그대로() {
        var service = new ComplaintService((s, p, f) -> "진정인은 500000원을 받지 못하였습니다.", new JsonMapper());
        var d = service.draft(RESULT, request("ko"));
        assertTrue(d.draftKo().sections().get(4).body().endsWith(request("ko").extraFacts()));
    }
}
