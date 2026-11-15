package com.yellowdolphin.wageback.document;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.yellowdolphin.wageback.common.ApiException;
import com.yellowdolphin.wageback.common.Words;
import com.yellowdolphin.wageback.common.llm.LlmClient;
import com.yellowdolphin.wageback.common.llm.LlmException;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

// 서류 사진 · PDF 를 LLM 에 보내 API.md 추출 키만 받는다. 결과는 로그에 남기지 않는다
@Service
public class DocumentService {

    static final List<String> KEYS = List.of("companyName", "employeeCount", "startDate", "endDate", "weeklyContractHours",
            "dailyContractHours", "workDaysPerWeek", "wageType", "baseWage", "allowances", "payMonth", "paidAmount",
            "overtimeHours", "nightHours", "holidayHours");

    // 주민등록번호: 생년월일 6자리 + (-) + 7자리
    static final Pattern RRN = Pattern.compile("(?<!\\d)\\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])\\s*-?\\s*[1-8]\\d{6}(?!\\d)");

    private static final String SYSTEM = """
            너는 한국 근로계약서 · 급여명세서에서 값을 읽는다. 아래 키만 읽고, 보이지 않거나 확실하지 않은 키는 unreadable 에 넣는다.
            키: %s
            - 금액 · 시간 · 인원은 숫자(원 단위 정수, 시간은 소수 허용), 날짜는 YYYY-MM-DD, payMonth 는 YYYY-MM
            - wageType 은 MONTHLY / HOURLY / DAILY 중 하나
            - allowances 는 [{"name": "수당 이름", "amount": 금액}] 배열
            - confidence 는 0~1, 글자가 흐리거나 추측이면 낮게
            - 주민등록번호 · 외국인등록번호 등 식별번호는 절대 옮겨 적지 말고, 본 개수만 maskedCount 에 적는다
            - 이름 · 주소 · 연락처는 읽지 않는다
            답은 JSON 한 개만: {"fields":[{"key":"baseWage","value":2156880,"confidence":0.9}],"unreadable":["overtimeHours"],"maskedCount":0}
            """.formatted(String.join(", ", KEYS));

    private final LlmClient llm;
    private final ObjectMapper mapper;

    public DocumentService(LlmClient llm, ObjectMapper mapper) {
        this.llm = llm;
        this.mapper = mapper;
    }

    public Result extract(String docType, String mediaType, byte[] data) {
        String answer;
        try {
            answer = llm.ask(SYSTEM, "서류 종류: " + ("CONTRACT".equals(docType) ? "근로계약서" : "급여명세서"),
                    new LlmClient.Attachment(mediaType, data));
        } catch (LlmException e) {
            throw failed();
        }
        // LLM 이 식별번호를 옮겨 적었어도 서버에서 한 번 더 가린다
        Matcher m = RRN.matcher(answer);
        int masked = 0;
        while (m.find()) {
            masked++;
        }
        JsonNode root = Words.json(mapper, m.replaceAll("******-*******"));
        if (root == null) {
            throw failed();
        }

        List<Field> fields = new ArrayList<>();
        Set<String> found = new LinkedHashSet<>();
        for (JsonNode f : root.path("fields")) {
            String key = f.path("key").asString("");
            JsonNode value = f.path("value");
            if (KEYS.contains(key) && !value.isNull() && !value.isMissingNode() && found.add(key)) {
                double confidence = Math.max(0, Math.min(1, f.path("confidence").asDouble(0.5)));
                fields.add(new Field(key, value, confidence));
            }
        }
        Set<String> unreadable = new LinkedHashSet<>();
        for (JsonNode u : root.path("unreadable")) {
            if (KEYS.contains(u.asString("")) && !found.contains(u.asString(""))) {
                unreadable.add(u.asString(""));
            }
        }
        if (fields.isEmpty()) {
            throw failed();
        }
        int reported = Math.max(0, Math.min(20, root.path("maskedCount").asInt(0)));
        return new Result(docType, fields, List.copyOf(unreadable), reported + masked);
    }

    private static ApiException failed() {
        return new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "OCR_FAILED", "file", "서류를 읽지 못했어요. 직접 입력해 주세요.");
    }

    public record Result(String docType, List<Field> fields, List<String> unreadable, int maskedCount) {
    }

    public record Field(String key, JsonNode value, double confidence) {
    }
}
