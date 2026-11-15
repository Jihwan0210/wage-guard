package com.yellowdolphin.wageback.common;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.yellowdolphin.wageback.complaint.ComplaintController;
import com.yellowdolphin.wageback.complaint.ComplaintService;
import com.yellowdolphin.wageback.document.DocumentController;
import com.yellowdolphin.wageback.document.DocumentService;
import com.yellowdolphin.wageback.explanation.ExplanationController;
import com.yellowdolphin.wageback.explanation.ExplanationService;

import tools.jackson.databind.json.JsonMapper;

// 오류 응답이 API.md 형식(ProblemDetail + code · field)인지
class ApiErrorTest {

    final JsonMapper mapper = new JsonMapper();
    final MockMvc mvc = MockMvcBuilders.standaloneSetup(
            new DocumentController(new DocumentService((s, p, f) -> "{}", mapper)),
            new ExplanationController(new ExplanationService((s, p, f) -> "{}", mapper)),
            new ComplaintController(new ComplaintService((s, p, f) -> "", mapper)))
            .setControllerAdvice(new ApiExceptionHandler()).build();

    MockMultipartFile file(String name, int size) {
        return new MockMultipartFile("file", name, "application/octet-stream", new byte[size]);
    }

    @Test
    void 업로드_오류_코드() throws Exception {
        mvc.perform(multipart("/api/v1/documents/extract").file(file("a.hwp", 10)).param("docType", "PAYSLIP"))
                .andExpect(status().is(415)).andExpect(jsonPath("$.code").value("UNSUPPORTED_FILE")).andExpect(jsonPath("$.field").value("file"));
        mvc.perform(multipart("/api/v1/documents/extract").file(file("a.png", 10 * 1024 * 1024 + 1)).param("docType", "PAYSLIP"))
                .andExpect(status().is(413)).andExpect(jsonPath("$.code").value("FILE_TOO_LARGE"));
        mvc.perform(multipart("/api/v1/documents/extract").file(file("a.png", 10)).param("docType", "X"))
                .andExpect(status().is(400)).andExpect(jsonPath("$.code").value("INVALID_DOC_TYPE")).andExpect(jsonPath("$.field").value("docType"));
        mvc.perform(multipart("/api/v1/documents/extract").file(file("a.png", 10)).param("docType", "PAYSLIP"))
                .andExpect(status().is(422)).andExpect(jsonPath("$.code").value("OCR_FAILED"));
    }

    @Test
    void 없는_결과는_404_CALCULATION_EXPIRED() throws Exception {
        mvc.perform(post("/api/v1/calculations/calc_none/explanation").contentType(MediaType.APPLICATION_JSON).content("{\"lang\":\"ko\"}"))
                .andExpect(status().is(404)).andExpect(jsonPath("$.code").value("CALCULATION_EXPIRED"));
        mvc.perform(post("/api/v1/calculations/calc_none/complaint-draft").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().is(404)).andExpect(jsonPath("$.code").value("CALCULATION_EXPIRED"));
    }

    @Test
    void 입력_검증은_400_INVALID_INPUT_과_field() throws Exception {
        mvc.perform(post("/api/v1/calculations/calc_none/explanation").contentType(MediaType.APPLICATION_JSON).content("{\"lang\":\"fr\"}"))
                .andExpect(status().is(400)).andExpect(jsonPath("$.code").value("INVALID_INPUT")).andExpect(jsonPath("$.field").value("lang"));
        mvc.perform(post("/api/v1/calculations/calc_none/complaint-draft").contentType(MediaType.APPLICATION_JSON).content("{ 깨진 JSON"))
                .andExpect(status().is(400)).andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }
}
