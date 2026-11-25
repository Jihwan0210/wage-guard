package com.yellowdolphin.wageback.document;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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

// 서류 사진 · PDF 를 LLM 에 보내 API.md 추출 키만 받는다. 사진 · 읽은 값은 저장 · 로그하지 않고 응답으로만 돌려준다
// 진정서 · 내용증명을 미리 채우려고 이름 · 연락처 · 주소 · 대표자도 읽는다 (정식 기능, 대표 결정 2026-11-21)
@Service
public class DocumentService {

    static final List<String> KEYS = List.of("companyName", "employeeCount", "startDate", "endDate", "weeklyContractHours",
            "dailyContractHours", "workDaysPerWeek", "wageType", "baseWage", "allowances", "payMonth", "paidAmount",
            "overtimeHours", "nightHours", "holidayHours", "jobTitle", "workplaceAddress", "workplacePhone", "workerName", "workerPhone", "workerAddress", "ownerName");

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
            - 진정서를 미리 채우려고 근로자 이름 · 연락처 · 주소(workerName · workerPhone · workerAddress), 대표자 이름(ownerName), 사업장 주소 · 전화(workplaceAddress · workplacePhone), 하는 일(jobTitle)을 글자 그대로 읽는다
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

    // 장마다 따로 읽고(동시에), 같은 키는 확신이 가장 높은 값으로 합친다. 값이 다르면 conflicts 에 모두 적는다
    public BatchResult extractBatch(List<Page> pages) {
        List<PageResult> results = new ArrayList<>();
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Result>> jobs = pages.stream().map(p -> pool.submit(() -> extract(p.docType(), p.mediaType(), p.data()))).toList();
            for (int i = 0; i < jobs.size(); i++) {
                try {
                    Result r = jobs.get(i).get();
                    results.add(new PageResult(i + 1, r.docType(), true, r.fields(), r.unreadable(), r.maskedCount()));
                } catch (ExecutionException e) {
                    results.add(new PageResult(i + 1, pages.get(i).docType(), false, List.of(), List.of(), 0));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw failed();
                }
            }
        }
        if (results.stream().noneMatch(PageResult::ok)) {
            throw failed();
        }

        List<SourcedField> fields = new ArrayList<>();
        List<Conflict> conflicts = new ArrayList<>();
        for (String key : KEYS) {
            List<SourcedField> all = new ArrayList<>();
            results.forEach(r -> r.fields().stream().filter(f -> f.key().equals(key))
                    .forEach(f -> all.add(new SourcedField(key, f.value(), f.confidence(), r.page()))));
            if (all.isEmpty()) {
                continue;
            }
            SourcedField best = all.stream().reduce((a, b) -> b.confidence() > a.confidence() ? b : a).orElseThrow();
            fields.add(best);
            if (all.stream().anyMatch(f -> !same(f.value(), best.value()))) {
                conflicts.add(new Conflict(key, all));
            }
        }
        Set<String> unreadable = new LinkedHashSet<>();
        results.forEach(r -> unreadable.addAll(r.unreadable()));
        fields.forEach(f -> unreadable.remove(f.key()));
        int masked = results.stream().mapToInt(PageResult::maskedCount).sum();
        return new BatchResult(results, fields, List.copyOf(unreadable), conflicts, masked);
    }

    // 2156880 과 2156880.0 은 같은 값
    private static boolean same(JsonNode a, JsonNode b) {
        return a.isNumber() && b.isNumber() ? a.asDouble() == b.asDouble() : a.equals(b);
    }

    private static ApiException failed() {
        return new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "OCR_FAILED", "file", "서류를 읽지 못했어요. 직접 입력해 주세요.");
    }

    public record Result(String docType, List<Field> fields, List<String> unreadable, int maskedCount) {
    }

    public record Field(String key, JsonNode value, double confidence) {
    }

    public record Page(String docType, String mediaType, byte[] data) {
    }

    // page 는 1부터 (화면의 '1번 사진'). 못 읽은 장은 ok=false
    public record PageResult(int page, String docType, boolean ok, List<Field> fields, List<String> unreadable, int maskedCount) {
    }

    public record SourcedField(String key, JsonNode value, double confidence, int page) {
    }

    public record Conflict(String key, List<SourcedField> values) {
    }

    public record BatchResult(List<PageResult> pages, List<SourcedField> fields, List<String> unreadable, List<Conflict> conflicts,
            int maskedCount) {
    }
}
