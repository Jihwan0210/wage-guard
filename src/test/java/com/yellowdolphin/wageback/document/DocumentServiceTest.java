package com.yellowdolphin.wageback.document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
}
