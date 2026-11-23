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

    static ComplaintController.LetterRequest letterRequest(String lang, String account) {
        Request r = request(lang);
        return new ComplaintController.LetterRequest(r.complainant(), r.respondent(), r.job(), false, r.extraFacts(), account, null, lang);
    }

    @Test
    void 내용증명_인적사항_계좌는_LLM에_보내지_않고_금액은_계산값_그대로() {
        List<String> sent = new ArrayList<>();
        var service = new ComplaintService((s, p, f) -> {
            sent.add(s + p);
            return "다듬은 문단입니다.";
        }, new JsonMapper());
        var d = service.letter(RESULT, letterRequest("en", "국민은행 123-45-6789"));

        for (String p : sent) {
            assertFalse(p.contains("홍길동") || p.contains("김사장") || p.contains("010-1111-2222") || p.contains("서울시") || p.contains("123-45-6789"), p);
        }
        var ko = d.draftKo().sections();
        assertEquals("내용증명", d.draftKo().title());
        assertEquals(8, ko.size());
        assertEquals("홍길동 · 010-1111-2222\n서울시 어딘가", ko.get(0).body());
        assertEquals("○○식당 (대표 김사장)\n서울시 가게", ko.get(1).body());
        assertEquals("2026년 9월분 미지급 임금 456,880원\n2026년 9월분 연장근로수당 161,976원\n합계 금 육십일만팔천팔백오십육원(₩618,856)", ko.get(4).body());
        assertTrue(ko.get(3).body().startsWith("발신인은 2025. 3. 3.부터 2026. 10. 31.까지 수신인의 사업장에서 주방 보조 업무로 근무하고 퇴직하였으나"), ko.get(3).body());
        assertTrue(ko.get(3).body().endsWith("\n다듬은 문단입니다."));
        assertTrue(ko.get(5).body().contains("근로기준법 제36조") && ko.get(5).body().contains("제37조 제1항 제1호") && ko.get(5).body().contains("연 100분의 20")
                && ko.get(5).body().contains("근로기준법 제43조"), ko.get(5).body());
        assertTrue(ko.get(6).body().contains("이 내용증명을 받은 날부터 7일 이내") && ko.get(6).body().contains("국민은행 123-45-6789"), ko.get(6).body());
        assertFalse(String.valueOf(ko).contains("징역") || String.valueOf(ko).contains("형사"));
        assertEquals(8, d.translation().sections().size());
        assertTrue(d.translation().sections().get(4).body().endsWith("Total 618,856 KRW"));
    }

    @Test
    void 내용증명_계좌를_안_쓰면_급여_받던_계좌_재직중이면_제2호() {
        var service = new ComplaintService((s, p, f) -> "진정인은 500000원을 받지 못하였습니다.", new JsonMapper());
        Request r = request("ko");
        var d = service.letter(RESULT, new ComplaintController.LetterRequest(r.complainant(), r.respondent(), r.job(), true, r.extraFacts(), " ", 10, "ko"));
        var ko = d.draftKo().sections();
        assertTrue(ko.get(6).body().contains("발신인이 급여를 받던 발신인 명의 계좌") && ko.get(6).body().contains("받은 날부터 10일 이내"));
        assertTrue(ko.get(5).body().contains("제37조 제1항 제2호") && !ko.get(5).body().contains("제36조"));
        assertTrue(ko.get(3).body().contains("현재까지") && ko.get(3).body().endsWith(r.extraFacts())); // 새 숫자를 만든 답은 버림
    }

    @Test
    void 한글_금액() {
        assertEquals("금 육백팔십일만오천오백육십일원(₩6,815,561)", com.yellowdolphin.wageback.common.Words.wonHangul(6815561));
        assertEquals("금 삼억원(₩300,000,000)", com.yellowdolphin.wageback.common.Words.wonHangul(300000000));
        assertEquals("금 일억일만원(₩100,010,000)", com.yellowdolphin.wageback.common.Words.wonHangul(100010000));
        assertEquals("금 십만원(₩100,000)", com.yellowdolphin.wageback.common.Words.wonHangul(100000));
        assertEquals("금 영원(₩0)", com.yellowdolphin.wageback.common.Words.wonHangul(0));
    }

    @Test
    void LLM이_숫자를_만들면_원문_그대로() {
        var service = new ComplaintService((s, p, f) -> "진정인은 500000원을 받지 못하였습니다.", new JsonMapper());
        var d = service.draft(RESULT, request("ko"));
        assertTrue(d.draftKo().sections().get(4).body().endsWith(request("ko").extraFacts()));
    }
}
