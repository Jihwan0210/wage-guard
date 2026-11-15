package com.yellowdolphin.wageback.common;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

// 해설 · 진정서가 같이 쓰는 금액 · 날짜 표기, 항목 이름, LLM 답 읽기
public final class Words {

    private static final Pattern NUMBER = Pattern.compile("\\d+");

    private static final Map<String, String[]> LABELS = Map.of( // ko, en, vi
            "BASE_WAGE", new String[] { "미지급 임금", "Unpaid wages", "Tiền lương chưa trả" },
            "OVERTIME", new String[] { "연장근로수당", "Overtime pay", "Tiền làm thêm giờ" },
            "NIGHT", new String[] { "야간근로수당", "Night work pay", "Tiền làm ca đêm" },
            "HOLIDAY", new String[] { "휴일근로수당", "Holiday work pay", "Tiền làm ngày nghỉ" },
            "WEEKLY_HOLIDAY", new String[] { "주휴수당", "Weekly holiday pay", "Tiền nghỉ hằng tuần" },
            "SEVERANCE", new String[] { "퇴직금", "Severance pay", "Trợ cấp thôi việc" },
            "MIN_WAGE_SHORTFALL", new String[] { "최저임금 차액", "Minimum wage shortfall", "Tiền chênh lệch lương tối thiểu" });

    private Words() {
    }

    public static int langIndex(String lang) {
        return switch (lang) {
            case "en" -> 1;
            case "vi" -> 2;
            default -> 0;
        };
    }

    public static String label(String code, String lang, String otherwise) {
        String[] names = LABELS.get(code);
        return names == null ? otherwise : names[langIndex(lang)];
    }

    public static String won(long amount, String lang) {
        return String.format(Locale.ROOT, "ko".equals(lang) ? "%,d원" : "%,d KRW", amount);
    }

    // "2026-09" → 2026년 9월 / September 2026 / tháng 9/2026
    public static String month(String yearMonth, String lang) {
        YearMonth ym = YearMonth.parse(yearMonth);
        return switch (lang) {
            case "en" -> ym.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + ym.getYear();
            case "vi" -> "tháng " + ym.getMonthValue() + "/" + ym.getYear();
            default -> ym.getYear() + "년 " + ym.getMonthValue() + "월";
        };
    }

    // "2025-03-03" → 2025. 3. 3.
    public static String dateKo(String date) {
        LocalDate d = LocalDate.parse(date);
        return d.getYear() + ". " + d.getMonthValue() + ". " + d.getDayOfMonth() + ".";
    }

    // LLM 답 앞뒤 설명을 버리고 첫 { ~ 마지막 } 만 읽는다. 못 읽으면 null
    public static JsonNode json(ObjectMapper mapper, String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end < start) {
            return null;
        }
        try {
            return mapper.readTree(text.substring(start, end + 1));
        } catch (RuntimeException e) {
            return null;
        }
    }

    // 원문에 없던 숫자가 생겼는지 (LLM 이 숫자를 만들지 못하게)
    public static boolean hasNewNumber(String result, String source) {
        Matcher m = NUMBER.matcher(result);
        while (m.find()) {
            if (!source.contains(m.group())) {
                return true;
            }
        }
        return false;
    }
}
