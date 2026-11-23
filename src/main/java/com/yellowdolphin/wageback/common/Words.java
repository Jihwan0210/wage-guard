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

    private static final Map<String, String[]> LABELS = Map.ofEntries( // ko, en, vi
            Map.entry("BASE_WAGE", new String[] { "미지급 임금", "Unpaid wages", "Tiền lương chưa trả" }),
            Map.entry("OVERTIME", new String[] { "연장근로수당", "Overtime pay", "Tiền làm thêm giờ" }),
            Map.entry("NIGHT", new String[] { "야간근로수당", "Night work pay", "Tiền làm ca đêm" }),
            Map.entry("HOLIDAY", new String[] { "휴일근로수당", "Holiday work pay", "Tiền làm ngày nghỉ" }),
            Map.entry("WEEKLY_HOLIDAY", new String[] { "주휴수당", "Weekly holiday pay", "Tiền nghỉ hằng tuần" }),
            Map.entry("SEVERANCE", new String[] { "퇴직금", "Severance pay", "Trợ cấp thôi việc" }),
            Map.entry("MIN_WAGE_SHORTFALL", new String[] { "최저임금 차액", "Minimum wage shortfall", "Tiền chênh lệch lương tối thiểu" }),
            Map.entry("DISMISSAL_NOTICE", new String[] { "해고예고수당", "Dismissal notice pay", "Tiền báo trước khi sa thải" }),
            Map.entry("ANNUAL_LEAVE", new String[] { "못 쓴 연차수당", "Unused paid leave pay", "Tiền phép năm chưa dùng" }),
            Map.entry("SHUTDOWN_ALLOWANCE", new String[] { "휴업수당", "Pay for days the company stopped work", "Tiền ngừng việc (công ty cho nghỉ)" }),
            Map.entry("DELAY_INTEREST", new String[] { "지연이자(연 20%)", "Interest for late payment (20% a year)", "Tiền lãi trả chậm (20% mỗi năm)" }));

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

    // 내용증명 금액 표기: 6815561 → 금 육백팔십일만오천오백육십일원(₩6,815,561)
    public static String wonHangul(long amount) {
        String digits = "영일이삼사오육칠팔구";
        String[] small = { "", "십", "백", "천" }, big = { "", "만", "억", "조" };
        StringBuilder out = new StringBuilder();
        String s = Long.toString(amount);
        for (int i = 0; i < s.length(); i++) {
            int d = s.charAt(i) - '0', pos = s.length() - 1 - i;
            if (d > 0) {
                out.append(d == 1 && pos % 4 > 0 ? "" : digits.charAt(d)).append(small[pos % 4]);
            }
            // 만 · 억 자리: 그 네 자리 중 하나라도 0 이 아니면 단위를 붙인다
            if (pos % 4 == 0 && pos > 0 && Long.parseLong(s.substring(Math.max(0, i - 3), i + 1)) > 0) {
                out.append(big[pos / 4]);
            }
        }
        return "금 " + (amount == 0 ? "영" : out) + "원(₩" + String.format(Locale.ROOT, "%,d", amount) + ")";
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
