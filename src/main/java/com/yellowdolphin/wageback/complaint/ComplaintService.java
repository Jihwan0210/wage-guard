package com.yellowdolphin.wageback.complaint;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.yellowdolphin.wageback.common.Words;
import com.yellowdolphin.wageback.common.llm.LlmClient;
import com.yellowdolphin.wageback.common.llm.LlmException;
import com.yellowdolphin.wageback.complaint.ComplaintController.Company;
import com.yellowdolphin.wageback.complaint.ComplaintController.Person;
import com.yellowdolphin.wageback.complaint.ComplaintController.Request;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

// 진정서 초안. 인적사항 · 체불 내역은 서버 템플릿, LLM 은 '진정 내용' 문단만 다듬고 번역한다
@Service
public class ComplaintService {

    static final String DISCLAIMER = "이 문서는 법률 자문이 아닌 서식 작성 보조 자료입니다.";

    private static final String POLISH = "다음 글을 노동청 임금체불 진정서의 '진정 내용' 문단에 맞게 공손한 문어체 한국어로 다듬어라. "
            + "사실을 더하거나 빼지 말고, 숫자는 원문 그대로 둔다. 다듬은 문단만 답한다.";
    private static final String TRANSLATE = "다음 한국어 진정서 문단을 %s(으)로 번역해라. 숫자는 그대로 둔다. 번역문만 답한다.";

    private static final Map<String, String> LANG_NAMES = Map.of("en", "English", "vi", "Tiếng Việt");

    private static final Map<String, String[]> HEADINGS = Map.of( // ko 제목 → en, vi
            "진정인", new String[] { "Complainant", "Người khiếu nại" },
            "피진정인", new String[] { "Respondent (employer)", "Người bị khiếu nại (chủ sử dụng lao động)" },
            "근로 내용", new String[] { "Employment", "Thông tin làm việc" },
            "체불 내역", new String[] { "Unpaid amounts", "Các khoản chưa được trả" },
            "진정 내용", new String[] { "Details of the complaint", "Nội dung khiếu nại" },
            "요구 사항", new String[] { "Request", "Yêu cầu" });

    private static final String[] REQUEST = { "위 체불 금품을 지급받을 수 있도록 조사하여 주시기 바랍니다.",
            "Please investigate so that the unpaid amounts above can be paid.",
            "Đề nghị điều tra để tôi được trả các khoản tiền chưa được trả nêu trên." };
    private static final String[][] STATE = { { "퇴직", "재직 중" }, { "left the job", "still employed" }, { "đã nghỉ việc", "đang làm việc" } };
    private static final String[] TOTAL = { "합계", "Total", "Tổng cộng" };
    private static final String[] NOW = { "현재", "present", "hiện tại" };

    private final LlmClient llm;
    private final ObjectMapper mapper;

    public ComplaintService(LlmClient llm, ObjectMapper mapper) {
        this.llm = llm;
        this.mapper = mapper;
    }

    public Draft draft(String resultJson, Request req) {
        JsonNode result = mapper.readTree(resultJson);
        String lang = req.lang() == null ? "ko" : req.lang();
        Person me = req.complainant() == null ? new Person(null, null, null) : req.complainant();
        Company co = req.respondent() == null ? new Company(null, null, null, null) : req.respondent();
        String start = result.path("period").path("start").asString("");
        String end = result.path("period").path("end").asString("");
        boolean employed = req.employed() != null ? req.employed() : end.isEmpty();

        String opening = "진정인은 " + (start.isEmpty() ? "[입사일]" : Words.dateKo(start)) + "부터 "
                + (employed || end.isEmpty() ? "현재" : Words.dateKo(end)) + "까지 피진정인 사업장에서 "
                + (blank(req.job()) ? "" : req.job() + " 업무로 ") + "근무하였으며, 아래 체불 내역의 금품을 지급받지 못하였습니다.";
        String facts = blank(req.extraFacts()) ? "" : "\n" + polish(req.extraFacts(), me, co);
        String content = opening + facts;

        List<Section> ko = List.of(
                new Section("진정인", or(me.name(), "[이름]") + " · " + or(me.phone(), "[휴대폰 번호]") + "\n" + or(me.address(), "[사는 곳 주소]")),
                new Section("피진정인", or(co.companyName(), "[회사 이름]") + " (대표 " + or(co.ownerName(), "[사장님 이름]") + ")\n"
                        + or(co.address(), "[사업장 주소]") + " · " + or(co.phone(), "[사업장 전화번호]")),
                new Section("근로 내용", work(start, end, employed, req.job(), "ko")),
                new Section("체불 내역", unpaid(result, "ko")),
                new Section("진정 내용", content),
                new Section("요구 사항", REQUEST[0]));

        Translation translation = null;
        if (!"ko".equals(lang)) {
            int li = Words.langIndex(lang);
            List<Section> tr = new ArrayList<>();
            for (Section s : ko) {
                String body = switch (s.heading()) {
                    case "근로 내용" -> work(start, end, employed, req.job(), lang);
                    case "체불 내역" -> unpaid(result, lang);
                    case "진정 내용" -> translate(mask(content, me, co), lang);
                    case "요구 사항" -> REQUEST[li];
                    default -> s.body(); // 이름 · 주소는 그대로 (LLM 에 보내지 않음)
                };
                tr.add(new Section(HEADINGS.get(s.heading())[li - 1], body));
            }
            translation = new Translation(lang, tr);
        }
        return new Draft(new DraftKo("임금체불 진정서", ko), translation, DISCLAIMER);
    }

    // LLM 실패 · 새 숫자가 생기면 사용자가 쓴 원문 그대로
    private String polish(String facts, Person me, Company co) {
        String masked = mask(facts, me, co);
        try {
            String answer = llm.ask(POLISH, masked, null).strip();
            return answer.isEmpty() || Words.hasNewNumber(answer, masked) ? facts : answer;
        } catch (LlmException e) {
            return facts;
        }
    }

    // 번역 실패 시 한국어 문단 그대로
    private String translate(String text, String lang) {
        try {
            String answer = llm.ask(TRANSLATE.formatted(LANG_NAMES.get(lang)), text, null).strip();
            return answer.isEmpty() || Words.hasNewNumber(answer, text) ? text : answer;
        } catch (LlmException e) {
            return text;
        }
    }

    // LLM 에 보내기 전 사용자가 쓴 글 속 인적사항을 역할 이름으로 바꾼다
    private static String mask(String text, Person me, Company co) {
        String[][] pairs = { { me.name(), "진정인" }, { me.phone(), "[연락처]" }, { me.address(), "[주소]" },
                { co.ownerName(), "피진정인 대표" }, { co.companyName(), "피진정인" }, { co.address(), "[주소]" }, { co.phone(), "[연락처]" } };
        for (String[] p : pairs) {
            if (!blank(p[0])) {
                text = text.replace(p[0].strip(), p[1]);
            }
        }
        return text;
    }

    private static String work(String start, String end, boolean employed, String job, String lang) {
        int li = Words.langIndex(lang);
        String from = start.isEmpty() ? "?" : "ko".equals(lang) ? Words.dateKo(start) : start;
        String to = employed || end.isEmpty() ? NOW[li] : "ko".equals(lang) ? Words.dateKo(end) : end;
        return from + " ~ " + to + " (" + STATE[li][employed ? 1 : 0] + ")" + (blank(job) ? "" : "\n" + job);
    }

    // 금액이 계산된 항목만 줄마다, 마지막에 합계
    private static String unpaid(JsonNode result, String lang) {
        StringBuilder sb = new StringBuilder();
        for (JsonNode item : result.path("items")) {
            if (!item.path("amount").isNumber()) {
                continue;
            }
            if (item.path("month").isString()) {
                sb.append(Words.month(item.path("month").asString(), lang)).append("ko".equals(lang) ? "분 " : " · ");
            }
            String code = item.path("code").asString();
            sb.append(Words.label(code, lang, item.path("label").asString())).append(' ')
                    .append(Words.won(item.path("amount").asLong(), lang)).append('\n');
        }
        return sb.append(TOTAL[Words.langIndex(lang)]).append(' ')
                .append(Words.won(result.path("summary").path("totalUnpaid").asLong(), lang)).toString();
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static String or(String s, String otherwise) {
        return blank(s) ? otherwise : s.strip();
    }

    public record Draft(DraftKo draftKo, Translation translation, String disclaimer) {
    }

    public record DraftKo(String title, List<Section> sections) {
    }

    public record Translation(String lang, List<Section> sections) {
    }

    public record Section(String heading, String body) {
    }
}
