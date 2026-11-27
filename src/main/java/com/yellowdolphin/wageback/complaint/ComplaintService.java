package com.yellowdolphin.wageback.complaint;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.yellowdolphin.wageback.common.Words;
import com.yellowdolphin.wageback.common.llm.LlmClient;
import com.yellowdolphin.wageback.common.llm.LlmException;
import com.yellowdolphin.wageback.complaint.ComplaintController.Company;
import com.yellowdolphin.wageback.complaint.ComplaintController.LetterRequest;
import com.yellowdolphin.wageback.complaint.ComplaintController.Person;
import com.yellowdolphin.wageback.complaint.ComplaintController.Request;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

// 진정서 초안. 인적사항 · 체불 내역은 서버 템플릿, LLM 은 '진정 내용' 문단만 다듬고 번역한다
@Service
public class ComplaintService {

    static final String DISCLAIMER = "이 문서는 법률 자문이 아닌 서식 작성 보조 자료입니다.";

    private static final String POLISH = "다음 글을 노동청 임금체불 진정서의 '진정 내용' 문단에 맞게 공손한 문어체 한국어(~습니다)로 다듬어라. "
            + "사실을 더하거나 빼지 말고, 원문에 없는 요청 · 결론 문장도 덧붙이지 마라. 숫자와 날짜는 원문 표기 그대로 두고 연도를 붙이지 마라. 다듬은 문단만 답한다.";
    private static final String AGAIN = "\n\n(직전 답에 원문에 없는 숫자가 있었다. 숫자는 원문 그대로만 쓴다.)";
    // 금액은 같은 값이면 그 나라 표기로 바꿔도 된다 (140만 원 → 1.400.000 won). hasNewNumber 는 같은 값을 새 숫자로 보지 않는다
    private static final String TRANSLATE = "다음 한국어 진정서 문단을 %s(으)로 번역해라. 금액은 같은 값으로 쓰되 읽는 사람 나라의 숫자 표기로 바꿔도 된다(예: 140만 원 → 1.400.000 won). "
            + "날짜(2025. 3. 1.)는 원문 그대로 둔다. 새 숫자를 만들지 마라. 번역문만 답한다.";

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
                + (blank(req.job()) ? "" : req.job() + " 업무로 ") + "근무하였습니다.";
        Polished p = blank(req.extraFacts()) ? new Polished("", true) : polish(POLISH, req.extraFacts(), me, co);
        boolean severance = false;
        for (JsonNode item : result.path("items")) {
            severance |= "SEVERANCE".equals(item.path("code").asString()) && item.path("amount").isNumber() && item.path("amount").asLong() > 0;
        }
        // 못 받았다는 말은 한 번만: 직접 적은 사정이 있으면 그 문장이 맡고, 없으면 체불 내역을 가리키는 문장을 넣는다
        String content = opening + (p.text().isEmpty() ? " 위 체불 내역의 금품을 지급받지 못하였습니다." : "\n" + p.text())
                + (severance && !employed ? "\n퇴직 후 14일이 지나도록 퇴직금도 지급받지 못하였습니다." : "");

        List<Section> ko = List.of(
                new Section("진정인", or(me.name(), "[이름]") + ", " + or(me.phone(), "[휴대폰 번호]") + opt("\n", me.address())),
                new Section("피진정인", or(co.companyName(), "[회사 이름]") + " (대표 " + or(co.ownerName(), "[사장님 이름]") + ")\n"
                        + or(co.address(), "[사업장 주소]") + opt(", ", co.phone())),
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
        return new Draft(new DraftKo("임금체불 진정서", ko), translation, DISCLAIMER, !p.ok());
    }

    // ---------- 회사에 보내는 내용증명 초안 ----------
    // 인적사항 · 금액 · 법 조문은 모두 서버 템플릿(금액은 룰 엔진 결과 그대로). LLM 은 사용자가 쓴 '있었던 일' 문단만 다듬는다
    // 법 조문은 law.go.kr 현행 원문과 대조 (2026-11-21): 근로기준법 제36조 · 제37조 제1항 · 제43조, 시행령 제17조(연 100분의 20),
    // 근로자퇴직급여 보장법 제9조 제1항
    private static final String LETTER_POLISH = "다음 글을 회사에 보내는 임금 지급 청구 내용증명의 '사실관계' 문단에 맞게 감정 표현 없이 공손한 문어체 한국어(~습니다)로 다듬어라. "
            + "사실을 더하거나 빼지 말고, 원문에 없는 요청 · 결론 문장도 덧붙이지 마라. 숫자와 날짜는 원문 표기 그대로 두고 연도를 붙이지 마라. 다듬은 문단만 답한다.";
    static final int PAY_DAYS = 7;

    public Draft letter(String resultJson, LetterRequest req) {
        JsonNode result = mapper.readTree(resultJson);
        String lang = req.lang() == null ? "ko" : req.lang();
        Person me = req.sender() == null ? new Person(null, null, null) : req.sender();
        Company co = req.recipient() == null ? new Company(null, null, null, null) : req.recipient();
        String end = result.path("period").path("end").asString("");
        boolean employed = req.employed() != null ? req.employed() : end.isEmpty();
        Polished p = blank(req.extraFacts()) ? new Polished("", true) : polish(LETTER_POLISH, req.extraFacts(), me, co);
        String facts = p.text();
        String today = LocalDate.now(ZoneId.of("Asia/Seoul")).toString();
        List<Section> ko = letterSections(result, req, me, co, employed, facts, today, 0);
        Translation translation = null;
        if (!"ko".equals(lang)) {
            String trFacts = facts.isEmpty() ? "" : translate(mask(facts, me, co), lang);
            translation = new Translation(lang, letterSections(result, req, me, co, employed, trFacts, today, Words.langIndex(lang)));
        }
        return new Draft(new DraftKo("내용증명", ko), translation, DISCLAIMER, !p.ok());
    }

    // li: 0 한국어(우체국에 내는 원문) · 1 영어 · 2 베트남어(읽기용 번역)
    private static List<Section> letterSections(JsonNode result, LetterRequest req, Person me, Company co, boolean employed, String facts,
            String today, int li) {
        String lang = new String[] { "ko", "en", "vi" }[li];
        String start = result.path("period").path("start").asString("");
        String end = result.path("period").path("end").asString("");
        String from = start.isEmpty() ? "[입사일]" : li == 0 ? Words.dateKo(start) : start;
        String to = end.isEmpty() ? "" : li == 0 ? Words.dateKo(end) : end;
        String job = blank(req.job()) ? "" : req.job().strip();
        long total = result.path("summary").path("totalUnpaid").asLong();
        int days = req.payDays() == null ? PAY_DAYS : req.payDays();
        boolean severance = false, interest = false;
        List<String> other = new ArrayList<>();
        for (JsonNode item : result.path("items")) {
            if (!item.path("amount").isNumber() || item.path("amount").asLong() == 0) {
                continue;
            }
            severance |= "SEVERANCE".equals(item.path("code").asString());
            interest |= "DELAY_INTEREST".equals(item.path("code").asString());
            // 항목마다 첫 근거(지급 의무 조문)만. 뒤의 근거는 '받을 수 없는 경우' 같은 예외 조문이다
            for (JsonNode b : List.of(item.path("legalBasis").path(0))) {
                String cite = b.path("law").asString("") + " " + b.path("article").asString("");
                if (!cite.isBlank() && !other.contains(cite.strip()) && !cite.contains("제36조") && !cite.contains("제37조") && !cite.contains("제43조")
                        && !cite.contains("시행령 제17조")) {
                    other.add(cite.strip());
                }
            }
        }

        String opening = switch (li) {
            case 1 -> "The sender " + (employed ? "has been working at the recipient's workplace" + (job.isEmpty() ? "" : " as " + job) + " since " + from
                    : "worked at the recipient's workplace" + (job.isEmpty() ? "" : " as " + job) + " from " + from + " to " + to + " and has left the job")
                    + ", but has not been paid the amounts below.";
            case 2 -> "Người gửi " + (employed ? "đang làm việc tại nơi làm việc của người nhận" + (job.isEmpty() ? "" : " (" + job + ")") + " từ " + from
                    : "đã làm việc tại nơi làm việc của người nhận" + (job.isEmpty() ? "" : " (" + job + ")") + " từ " + from + " đến " + to + " và đã nghỉ việc")
                    + ", nhưng chưa được trả các khoản dưới đây.";
            default -> "발신인은 " + from + "부터 " + (employed ? "현재까지" : to + "까지") + " 수신인의 사업장에서 " + (job.isEmpty() ? "" : job + " 업무로 ")
                    + (employed ? "근무하고 있으나" : "근무하고 퇴직하였으나") + ", 아래 임금 등을 아직 지급받지 못하였습니다.";
        };

        String claim = unpaidLines(result, lang) + (li == 0 ? "합계 " + Words.wonHangul(total) : TOTAL[li] + " " + Words.won(total, lang))
                + (interest ? "\n" + new String[] { "(지연이자는 " + Words.dateKo(today) + "까지 센 금액이며, 실제로 지급하는 날까지 연 20%로 계속 붙습니다.)",
                        "(Late-payment interest is counted up to " + today + " and keeps adding up at 20% a year until the day of payment.)",
                        "(Tiền lãi trả chậm được tính đến " + today + " và tiếp tục cộng thêm 20% mỗi năm cho đến ngày trả.)" }[li] : "");

        List<String> basis = new ArrayList<>();
        if (!employed) {
            basis.add(new String[] { "근로기준법 제36조: 근로자가 퇴직한 경우 사용자는 그 지급 사유가 발생한 때부터 14일 이내에 임금, 보상금, 그 밖의 모든 금품을 지급하여야 합니다.",
                    "Labor Standards Act Article 36: when a worker leaves, the employer must pay wages and all other money within 14 days.",
                    "Luật Tiêu chuẩn Lao động Điều 36: khi người lao động nghỉ việc, chủ phải trả lương và mọi khoản tiền khác trong vòng 14 ngày." }[li]);
            if (severance) {
                basis.add(new String[] { "근로자퇴직급여 보장법 제9조 제1항: 근로자가 퇴직한 경우 사용자는 그 지급 사유가 발생한 날부터 14일 이내에 퇴직금을 지급하여야 합니다.",
                        "Employee Retirement Benefit Security Act Article 9(1): the employer must pay severance within 14 days after the worker leaves.",
                        "Luật Bảo đảm Trợ cấp Hưu trí Điều 9 khoản 1: chủ phải trả trợ cấp thôi việc trong vòng 14 ngày sau khi người lao động nghỉ việc." }[li]);
            }
        }
        basis.add(new String[] { "근로기준법 제43조: 임금은 통화로 직접 근로자에게 그 전액을 매월 1회 이상 일정한 날짜를 정하여 지급하여야 합니다.",
                "Labor Standards Act Article 43: wages must be paid in full, directly to the worker, at least once a month on a fixed day.",
                "Luật Tiêu chuẩn Lao động Điều 43: tiền lương phải được trả đủ, trực tiếp cho người lao động, ít nhất mỗi tháng một lần vào ngày cố định." }[li]);
        basis.add(employed
                ? new String[] { "근로기준법 제37조 제1항 제2호 및 같은 법 시행령 제17조: 정해진 임금 지급일까지 지급하지 않은 임금에는 그 다음 날부터 지급하는 날까지 연 100분의 20의 지연이자를 지급하여야 합니다.",
                        "Labor Standards Act Article 37(1)2 and its Enforcement Decree Article 17: wages not paid by the regular payday carry 20% yearly interest from the next day until payment.",
                        "Luật Tiêu chuẩn Lao động Điều 37 khoản 1 điểm 2 và Nghị định thi hành Điều 17: lương không trả đúng ngày trả lương phải chịu lãi 20% mỗi năm từ ngày hôm sau đến ngày trả." }[li]
                : new String[] { "근로기준법 제37조 제1항 제1호 및 같은 법 시행령 제17조: 퇴직 후 14일 이내에 지급하지 않은 임금과 퇴직금에는 그 다음 날부터 지급하는 날까지 연 100분의 20의 지연이자를 지급하여야 합니다.",
                        "Labor Standards Act Article 37(1)1 and its Enforcement Decree Article 17: wages and severance not paid within 14 days after leaving carry 20% yearly interest from the next day until payment.",
                        "Luật Tiêu chuẩn Lao động Điều 37 khoản 1 điểm 1 và Nghị định thi hành Điều 17: lương và trợ cấp thôi việc không trả trong 14 ngày sau khi nghỉ phải chịu lãi 20% mỗi năm từ ngày hôm sau đến ngày trả." }[li]);
        if (!other.isEmpty()) {
            basis.add(new String[] { "그 밖에 각 항목의 계산 근거: ", "Basis for each item: ", "Căn cứ tính từng khoản: " }[li] + String.join(", ", other));
        }

        String account = blank(req.account()) ? new String[] { "발신인이 급여를 받던 발신인 명의 계좌", "the sender's own bank account where wages were paid",
                "tài khoản đứng tên người gửi đã dùng để nhận lương" }[li] : req.account().strip();
        String ask = switch (li) {
            case 1 -> "The sender asks the recipient as follows.\nA. Amount: " + Words.won(total, lang) + "\nB. Deadline: within " + days
                    + " days after this letter arrives\nC. Account: " + account + "\nD. Late-payment interest of 20% a year keeps adding up until the day of payment.";
            case 2 -> "Người gửi yêu cầu người nhận như sau.\nA. Số tiền: " + Words.won(total, lang) + "\nB. Hạn trả: trong vòng " + days
                    + " ngày kể từ khi nhận thư này\nC. Tài khoản: " + account + "\nD. Tiền lãi trả chậm 20% mỗi năm tiếp tục cộng thêm cho đến ngày trả.";
            default -> "이에 발신인은 수신인에게 다음과 같이 요청합니다.\n가. 지급할 금액: " + Words.wonHangul(total) + "\n나. 지급 기한: 이 내용증명을 받은 날부터 " + days
                    + "일 이내\n다. 입금 계좌: " + account + "\n라. 법정 지연이자(연 20%)는 실제로 지급하는 날까지 계속 붙습니다.";
        };
        String warn = new String[] { "위 기한까지 지급하지 않으면 발신인은 관할 지방고용노동관서에 임금체불 진정을 제기하고, 필요하면 민사소송 등 법적 절차를 진행할 예정임을 알려 드립니다.",
                "If the amount is not paid by the deadline, the sender will file a wage complaint with the local labor office and may take further legal steps such as a civil lawsuit.",
                "Nếu không trả đúng hạn, người gửi sẽ khiếu nại nợ lương tại văn phòng lao động địa phương và có thể tiến hành các thủ tục pháp lý khác như kiện dân sự." }[li];

        String[][] h = { { "발신인", "Sender", "Người gửi" }, { "수신인", "Recipient", "Người nhận" }, { "제목", "Subject", "Tiêu đề" },
                { "1. 사실관계", "1. Facts", "1. Sự việc" }, { "2. 청구 금액", "2. Amounts claimed", "2. Số tiền yêu cầu" },
                { "3. 법적 근거", "3. Legal basis", "3. Căn cứ pháp lý" }, { "4. 요청 사항", "4. Request", "4. Yêu cầu" },
                { "5. 기한을 넘기면", "5. If not paid", "5. Nếu không trả" } };
        return List.of(
                new Section(h[0][li], or(me.name(), "[이름]") + ", " + or(me.phone(), "[휴대폰 번호]") + opt("\n", me.address())),
                new Section(h[1][li], or(co.companyName(), "[회사 이름]") + new String[] { " (대표 ", " (CEO ", " (Đại diện " }[li] + or(co.ownerName(), "[사장님 이름]") + ")\n" + or(co.address(), "[사업장 주소]")),
                new Section(h[2][li], new String[] { "미지급 임금 등 지급 청구", "Demand for payment of unpaid wages", "Yêu cầu trả tiền lương chưa trả" }[li]),
                new Section(h[3][li], opening + (facts.isEmpty() ? "" : "\n" + facts)),
                new Section(h[4][li], claim),
                new Section(h[5][li], String.join("\n", basis)),
                new Section(h[6][li], ask),
                new Section(h[7][li], warn));
    }

    // 다듬은 글 · 다듬기에 성공했는지. 새 숫자가 생기면 한 번 더 묻고, 그래도 안 되거나 LLM 이 실패하면 사용자가 쓴 원문 그대로 (화면에 '적은 그대로' 표시)
    record Polished(String text, boolean ok) {
    }

    private Polished polish(String prompt, String facts, Person me, Company co) {
        String masked = mask(facts, me, co);
        for (int i = 0; i < 2; i++) {
            try {
                String answer = llm.ask(prompt, i == 0 ? masked : masked + AGAIN, null).strip();
                if (!answer.isEmpty() && !Words.hasNewNumber(answer, masked)) {
                    return new Polished(answer, true);
                }
            } catch (LlmException e) {
                break;
            }
        }
        return new Polished(facts.strip(), false);
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
        int li = Words.langIndex(lang);
        boolean est = false;
        for (JsonNode item : result.path("items")) {
            est |= item.path("estimated").asBoolean(false) && item.path("amount").isNumber() && item.path("amount").asLong() != 0;
        }
        return unpaidLines(result, lang) + TOTAL[li] + ' ' + Words.won(result.path("summary").path("totalUnpaid").asLong(), lang)
                + (est ? "\n" + new String[] { "(추정) 표시 금액은 입력한 정보로 계산한 참고 금액이며, 실제 금액은 노동청 조사에서 확정됩니다.",
                        "Amounts marked (estimated) are worked out from the information entered; the labor office confirms the final amount.",
                        "Khoản ghi (ước tính) được tính từ thông tin đã nhập; số tiền cuối cùng do Sở Lao động xác nhận." }[li] : "");
    }

    private static String unpaidLines(JsonNode result, String lang) {
        StringBuilder sb = new StringBuilder();
        for (JsonNode item : result.path("items")) {
            if (!item.path("amount").isNumber() || item.path("amount").asLong() == 0) {
                continue;
            }
            if (item.path("month").isString()) {
                sb.append(Words.month(item.path("month").asString(), lang)).append("ko".equals(lang) ? "분 " : " · ");
            }
            String code = item.path("code").asString();
            sb.append(Words.label(code, lang, item.path("label").asString()))
                    .append(item.path("estimated").asBoolean(false) ? new String[] { "(추정)", " (estimated)", " (ước tính)" }[Words.langIndex(lang)] : "").append(' ')
                    .append(Words.won(item.path("amount").asLong(), lang)).append('\n');
        }
        return sb.toString();
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static String or(String s, String otherwise) {
        return blank(s) ? otherwise : s.strip();
    }

    // 선택 칸: 적었으면 앞에 구분자를 붙여 넣고, 비었으면 아무것도 넣지 않는다 ('[사는 곳 주소]' 같은 빈 칸 표시를 남기지 않게)
    private static String opt(String sep, String s) {
        return blank(s) ? "" : sep + s.strip();
    }

    // factsAsWritten: '있었던 일'을 다듬지 못해 사용자가 쓴 그대로 넣었으면 true (화면에서 직접 고치라고 알린다)
    public record Draft(DraftKo draftKo, Translation translation, String disclaimer, boolean factsAsWritten) {
    }

    public record DraftKo(String title, List<Section> sections) {
    }

    public record Translation(String lang, List<Section> sections) {
    }

    public record Section(String heading, String body) {
    }
}
