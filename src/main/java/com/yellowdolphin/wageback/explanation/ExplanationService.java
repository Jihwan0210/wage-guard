package com.yellowdolphin.wageback.explanation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

// 산정 결과 해설. LLM 은 자리표시자로 문장만 쓰고 숫자는 서버가 채운다
@Service
public class ExplanationService {

    static final int MAX_TRIES = 3; // 처음 1회 + 재생성 2회

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{[A-Z0-9_]+\\.[a-z]+\\}\\}");
    private static final Pattern DIGIT = Pattern.compile("\\d");
    // 단위 뒤에 바로 붙어도 되는 글자 (조사 · 문장부호). 이 앞의 겹친 단위만 지운다 ("월급" 의 "월" 은 안 지움)
    private static final String AFTER_UNIT = "(?=$|[\\s.,!?)·:;]|[이을은에의으로과와까부입분치씩도만])";
    // 한국어 답에 섞인 한자(중국어 글자). 작은 모델이 가끔 섞는다. 화면 글은 한자를 쓰지 않으니 나오면 다시 시킨다
    private static final Pattern HAN = Pattern.compile("\\p{IsHan}");

    private static final String SYSTEM = """
            너는 임금 계산 결과를 근로자에게 쉬운 말로 설명한다.
            - 항목마다 한 문장, 언어는 %s, 쉬운 존댓말
            - 숫자(0-9)를 절대 쓰지 않는다. 금액 · 달은 주어진 자리표시자를 글자 그대로 쓴다
            - 자리표시자에는 단위(원, 년 · 월, KRW, tháng)가 이미 들어 있으니 앞뒤에 단위를 또 쓰지 않는다
            - status 가 NO_DATA 면 정보가 없어 계산하지 않았으니 해당되면 입력해 달라고, INCLUDED_IN_WAGE 면 월급에 이미 들어 있다고 쓴다
            - estimated 가 true 면 추정한 금액이라 실제와 다를 수 있다고 밝힌다
            - 한국어면 해요체로 쓰고, 달이 있으면 달을 먼저 쓴다. 예: "{{X.month}}에 못 받은 월급은 {{X.amount}}이에요."
            - label 의 어려운 한자어(미지급, 가산 등)는 쉬운 말(못 받은, 더 얹어 주는)로 풀어 쓴다
            - 법률 판단이나 주어지지 않은 사실을 지어내지 않는다
            - summary 는 summaryPlaceholder 를 넣은 한 문장
            답은 JSON 한 개만: {"items":[{"key":"OVERTIME","text":"..."}],"summary":"..."}
            """;

    private static final Map<String, String> LANG_NAMES = Map.of("ko", "한국어", "en", "English", "vi", "Tiếng Việt");

    private static final String[][] FIXED = { // CALCULATED, NO_DATA, INCLUDED_IN_WAGE, 합계 (ko, en, vi)
            { "%s: 받아야 할 돈은 %s이에요.", "%s: calculated as %s.", "%s: được tính là %s." },
            { "%s: 입력한 정보가 없어 계산하지 않았어요. 해당되면 입력해 주세요.", "%s: not calculated because no information was entered. Please enter it if it applies.",
                    "%s: chưa tính vì chưa có thông tin. Nếu có, hãy nhập thêm." },
            { "%s: 월급 안에 이미 들어 있어요.", "%s: already included in your monthly wage.", "%s: đã bao gồm trong lương tháng." },
            { "아직 받지 못한 돈은 모두 %s이에요.", "In total, %s is calculated as unpaid.", "Tổng cộng %s được tính là chưa được trả." } };

    private static final String[] ESTIMATED = { " (추정)", " (estimated)", " (ước tính)" };

    private final LlmClient llm;
    private final ObjectMapper mapper;

    public ExplanationService(LlmClient llm, ObjectMapper mapper) {
        this.llm = llm;
        this.mapper = mapper;
    }

    public Explanation explain(String resultJson, String lang) {
        JsonNode result = mapper.readTree(resultJson);

        // 항목마다 자리표시자 키를 붙인다 (같은 코드가 여러 달이면 OVERTIME_2 …)
        Map<String, JsonNode> slots = new LinkedHashMap<>();
        Map<String, Integer> seen = new HashMap<>();
        Map<String, String> values = new HashMap<>();
        ArrayNode promptItems = mapper.createArrayNode();
        for (JsonNode item : result.path("items")) {
            String code = item.path("code").asString();
            int n = seen.merge(code, 1, Integer::sum);
            String key = n == 1 ? code : code + "_" + n;
            slots.put(key, item);
            ObjectNode p = promptItems.addObject().put("key", key).put("code", code).put("label", Words.label(code, "ko", item.path("label").asString()))
                    .put("status", status(item)).put("estimated", item.path("estimated").asBoolean(false));
            ArrayNode ph = p.putArray("placeholders");
            if (item.path("amount").isNumber()) {
                values.put("{{" + key + ".amount}}", Words.won(item.path("amount").asLong(), lang));
                ph.add("{{" + key + ".amount}}");
            }
            if (item.path("month").isString()) {
                values.put("{{" + key + ".month}}", Words.month(item.path("month").asString(), lang));
                ph.add("{{" + key + ".month}}");
            }
        }
        values.put("{{TOTAL.unpaid}}", Words.won(result.path("summary").path("totalUnpaid").asLong(), lang));

        ObjectNode prompt = mapper.createObjectNode().put("lang", lang);
        prompt.set("items", promptItems);
        prompt.put("summaryPlaceholder", "{{TOTAL.unpaid}}");
        String system = SYSTEM.formatted(LANG_NAMES.get(lang));
        String text = mapper.writeValueAsString(prompt);

        for (int i = 0; i < MAX_TRIES; i++) {
            String answer;
            try {
                answer = llm.ask(system, i == 0 ? text : text + "\n직전 답에 자리표시자 밖 숫자가 있었다. 숫자를 쓰지 마라.", null);
            } catch (LlmException e) {
                throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI_UNAVAILABLE", null, "지금은 해설을 만들 수 없어요. 계산 결과는 그대로 볼 수 있어요.");
            }
            Explanation filled = fill(Words.json(mapper, answer), slots, values, lang);
            if (filled != null) {
                return filled;
            }
        }
        return fixed(slots, values, lang);
    }

    // LLM 답 검사 후 숫자 채우기. 자리표시자 밖 숫자 · 모르는 자리표시자 · 빠진 항목이 있으면 null
    private Explanation fill(JsonNode answer, Map<String, JsonNode> slots, Map<String, String> values, String lang) {
        if (answer == null) {
            return null;
        }
        Map<String, String> texts = new HashMap<>();
        for (JsonNode a : answer.path("items")) {
            texts.put(a.path("key").asString(""), a.path("text").asString(""));
        }
        List<Explanation.Item> items = new ArrayList<>();
        for (var slot : slots.entrySet()) {
            String t = replace(texts.get(slot.getKey()), values);
            if (t == null || "ko".equals(lang) && HAN.matcher(t).find()) {
                return null;
            }
            items.add(new Explanation.Item(slot.getValue().path("code").asString(), t));
        }
        String summary = replace(answer.path("summary").asString(""), values);
        return summary == null || "ko".equals(lang) && HAN.matcher(summary).find() ? null : new Explanation(lang, items, summary, false);
    }

    private static String replace(String text, Map<String, String> values) {
        if (text == null || text.isBlank()) {
            return null;
        }
        text = fixParticles(dropRepeatedUnits(text, values), values);
        Matcher m = PLACEHOLDER.matcher(text);
        StringBuilder out = new StringBuilder();
        int last = 0;
        while (m.find()) {
            String outside = text.substring(last, m.start());
            if (DIGIT.matcher(outside).find() || !values.containsKey(m.group())) {
                return null;
            }
            out.append(outside).append(values.get(m.group()));
            last = m.end();
        }
        String rest = text.substring(last);
        if (DIGIT.matcher(rest).find() || rest.contains("{{")) {
            return null;
        }
        return out.append(rest).toString();
    }

    // 자리표시자 값에 이미 있는 단위를 LLM 이 앞뒤에 또 쓴 것을 지운다
    // "{{X.amount}}원" → 206,698원원, "{{X.month}} 월에" → 8월 월에, "tháng {{X.month}}" → tháng tháng 8/2026
    static String dropRepeatedUnits(String text, Map<String, String> values) {
        for (Map.Entry<String, String> v : values.entrySet()) {
            String ph = Pattern.quote(v.getKey()), value = v.getValue();
            String head = value.split(" ")[0];
            if (!DIGIT.matcher(head).find()) {
                text = text.replaceAll("(?iu)" + Pattern.quote(head) + "\\s?(?=" + ph + ")", "");
            }
            String tail = value.replaceAll("^.*\\d", "").strip();
            if (!tail.isEmpty()) {
                text = text.replaceAll("(?iu)(" + ph + ")\\s?" + Pattern.quote(tail) + AFTER_UNIT, "$1");
            }
        }
        return text;
    }

    // 자리표시자 뒤 조사를 채울 값의 끝 글자에 맞춘다. LLM 은 숫자를 모르고 쓰니 "{{X.amount}}가" → 924,000원가 가 된다.
    // 값이 받침 있는 한글로 끝나면(원 · 월) 가→이, 는→은, 를→을, 와→과, 로→으로(ㄹ 받침은 로 그대로). 한국어 값에만
    private static final Pattern PARTICLE = Pattern.compile("(가|는|를|와|로)(?=$|[\\s.,!?)·:;])");

    static String fixParticles(String text, Map<String, String> values) {
        for (Map.Entry<String, String> v : values.entrySet()) {
            String value = v.getValue();
            char last = value.isEmpty() ? ' ' : value.charAt(value.length() - 1);
            if (last < '가' || last > '힣' || (last - '가') % 28 == 0) {
                continue; // 한글이 아니거나 받침이 없다
            }
            boolean rieul = (last - '가') % 28 == 8;
            Matcher m = Pattern.compile(Pattern.quote(v.getKey()) + PARTICLE.pattern()).matcher(text);
            StringBuilder out = new StringBuilder();
            while (m.find()) {
                String to = switch (m.group(1)) {
                    case "가" -> "이";
                    case "는" -> "은";
                    case "를" -> "을";
                    case "와" -> "과";
                    default -> rieul ? "로" : "으로";
                };
                m.appendReplacement(out, Matcher.quoteReplacement(v.getKey() + to));
            }
            m.appendTail(out);
            text = out.toString();
        }
        return text;
    }

    // 재생성까지 실패하면 항목별 고정 문구
    private static Explanation fixed(Map<String, JsonNode> slots, Map<String, String> values, String lang) {
        int li = Words.langIndex(lang);
        List<Explanation.Item> items = new ArrayList<>();
        for (var slot : slots.entrySet()) {
            JsonNode item = slot.getValue();
            String code = item.path("code").asString();
            String name = Words.label(code, lang, item.path("label").asString());
            String month = values.get("{{" + slot.getKey() + ".month}}");
            if (month != null) {
                name = month + " " + name;
            }
            String text = switch (status(item)) {
                case "NO_DATA" -> FIXED[1][li].formatted(name);
                case "INCLUDED_IN_WAGE" -> FIXED[2][li].formatted(name);
                default -> FIXED[0][li].formatted(name, values.get("{{" + slot.getKey() + ".amount}}"))
                        + (item.path("estimated").asBoolean(false) ? ESTIMATED[li] : "");
            };
            items.add(new Explanation.Item(code, text));
        }
        return new Explanation(lang, items, FIXED[3][li].formatted(values.get("{{TOTAL.unpaid}}")), true);
    }

    // status 가 없으면 금액 유무로 정한다
    private static String status(JsonNode item) {
        if (item.path("status").isString()) {
            return item.path("status").asString();
        }
        return item.path("amount").isNumber() ? "CALCULATED" : "NO_DATA";
    }

    public record Explanation(String lang, List<Item> items, String summary, boolean fallback) {

        public record Item(String code, String text) {
        }
    }
}
