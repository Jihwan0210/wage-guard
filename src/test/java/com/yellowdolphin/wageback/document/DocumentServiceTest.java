package com.yellowdolphin.wageback.document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.yellowdolphin.wageback.common.ApiException;
import com.yellowdolphin.wageback.common.llm.LlmClient;
import com.yellowdolphin.wageback.common.llm.LlmException;

import tools.jackson.databind.json.JsonMapper;

class DocumentServiceTest {

    DocumentService service(String answer) {
        return new DocumentService((s, p, f) -> answer, new JsonMapper());
    }

    @Test
    void 주민번호를_가리고_센다() {
        String answer = """
                읽은 결과입니다.
                {"fields":[{"key":"companyName","value":"가게 900101-1234567","confidence":0.9},
                  {"key":"baseWage","value":2156880,"confidence":1.7},
                  {"key":"residentId","value":"9001011234567","confidence":0.9}],
                 "unreadable":["overtimeHours","baseWage","모름"],"maskedCount":1}
                """;
        var r = service(answer).extract("PAYSLIP", "image/png", new byte[] { 1 });
        assertEquals(3, r.maskedCount()); // LLM 이 본 1개 + 서버가 가린 2개
        assertEquals("가게 ******-*******", r.fields().get(0).value().asString());
        assertEquals(1.0, r.fields().get(1).confidence());
        assertEquals(2, r.fields().size()); // 약속에 없는 키는 버린다
        assertEquals(List.of("overtimeHours"), r.unreadable());
        assertFalse(r.toString().contains("1234567"));
    }

    @Test
    void LLM_실패나_못_읽으면_422() {
        LlmClient down = (s, p, f) -> {
            throw new LlmException("키 없음");
        };
        for (DocumentService s : List.of(new DocumentService(down, new JsonMapper()), service("모르겠어요"), service("{\"fields\":[]}"))) {
            var ex = assertThrows(ApiException.class, () -> s.extract("CONTRACT", "application/pdf", new byte[] { 1 }));
            assertEquals(422, ex.getStatus().value());
            assertEquals("OCR_FAILED", ex.getCode());
        }
    }

    // 사진마다 첫 바이트로 다른 답을 주는 가짜 LLM
    DocumentService byPage(String... answers) {
        return new DocumentService((s, p, f) -> {
            String a = answers[f.data()[0]];
            if (a == null) {
                throw new LlmException("못 읽음");
            }
            return a;
        }, new JsonMapper());
    }

    DocumentService.Page page(String docType, int n) {
        return new DocumentService.Page(docType, "image/jpeg", new byte[] { (byte) n });
    }

    @Test
    void 여러_장은_확신_높은_값으로_합치고_다르면_conflicts() {
        var s = byPage(
                "{\"fields\":[{\"key\":\"payMonth\",\"value\":\"2026-08\",\"confidence\":0.9},{\"key\":\"baseWage\",\"value\":2156880,\"confidence\":0.6}],\"unreadable\":[\"nightHours\",\"startDate\"],\"maskedCount\":1}",
                "{\"fields\":[{\"key\":\"payMonth\",\"value\":\"2026-09\",\"confidence\":0.95},{\"key\":\"baseWage\",\"value\":2156880.0,\"confidence\":0.7}],\"unreadable\":[],\"maskedCount\":0}",
                "{\"fields\":[{\"key\":\"startDate\",\"value\":\"2025-03-02\",\"confidence\":0.9}],\"unreadable\":[],\"maskedCount\":0}",
                null);
        var r = s.extractBatch(List.of(page("PAYSLIP", 0), page("PAYSLIP", 1), page("CONTRACT", 2), page("PAYSLIP", 3)));

        assertEquals(4, r.pages().size());
        assertEquals(List.of(true, true, true, false), r.pages().stream().map(DocumentService.PageResult::ok).toList());
        assertEquals("CONTRACT", r.pages().get(2).docType());
        assertEquals(3, r.pages().get(2).page()); // 1번부터

        var month = r.fields().stream().filter(f -> f.key().equals("payMonth")).findFirst().orElseThrow();
        assertEquals("2026-09", month.value().asString());
        assertEquals(2, month.page());
        var wage = r.fields().stream().filter(f -> f.key().equals("baseWage")).findFirst().orElseThrow();
        assertEquals(2, wage.page());

        // 2156880 과 2156880.0 은 같은 값이라 충돌이 아니다
        assertEquals(List.of("payMonth"), r.conflicts().stream().map(DocumentService.Conflict::key).toList());
        assertEquals(2, r.conflicts().get(0).values().size());
        // 다른 사진에서 읽은 키는 못 읽은 목록에서 빠진다
        assertEquals(List.of("nightHours"), r.unreadable());
        assertEquals(1, r.maskedCount());
    }

    @Test
    void 여러_장_모두_못_읽으면_422() {
        var ex = assertThrows(ApiException.class, () -> byPage(null, "모름").extractBatch(List.of(page("PAYSLIP", 0), page("CONTRACT", 1))));
        assertEquals("OCR_FAILED", ex.getCode());
        assertTrue(ex.getStatus().value() == 422);
    }
}
