package com.yellowdolphin.wageback.calculation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.yellowdolphin.wageback.calculation.CalculationResult.Basis;

// 근로계약서 점검 (v2 8단계). 금액은 만들지 않고 시급 비교 · 시간 비교만 한다
// - 빠진 것: 근로기준법 제17조 ① · 시행령 제8조 필수 기재 사항
// - 문제: 시급 < 그 해 최저임금(minimum-wage.properties), 약속한 주 시간 > 52 (제50조 40 + 제53조 12, 5인 미만은 제외)
public final class ContractCheck {

    // 계약서에 적혀 있어야 할 것 (화면 체크 목록과 같은 순서)
    static final List<String> REQUIRED = List.of("WAGE", "HOURS", "HOLIDAY", "ANNUAL_LEAVE", "PLACE", "JOB");
    private static final Set<String> TYPES = Set.of("MONTHLY", "HOURLY", "DAILY");
    private static final BigDecimal FIFTY_TWO = BigDecimal.valueOf(52);

    public record Request(Integer year, String wageType, Long wage, BigDecimal weeklyHours, BigDecimal dailyHours, String size,
            List<String> written) {
    }

    // level: MISSING(빠짐, 노랑) / PROBLEM(문제, 빨강)
    public record Finding(String code, String level, String message, Basis basis) {
    }

    public record Result(int minimumWageYear, Long minimumWage, String hourlyWage, String hourlyFormula, List<String> ok,
            List<Finding> findings, List<String> notChecked, String disclaimer) {
    }

    private ContractCheck() {
    }

    static Result check(Request r) {
        validate(r);
        List<String> written = r.written() == null ? List.of() : r.written();
        List<String> ok = new ArrayList<>();
        List<Finding> findings = new ArrayList<>();
        List<String> notChecked = new ArrayList<>();
        // 제17조 ① · 시행령 제8조 (law.go.kr lsiSeq=285279 · 270551, 2026-11-15 확인)
        Basis art17 = RuleEngine.basis("근로기준법", "제17조", "계약할 때 임금 · 일하는 시간 · 휴일 · 연차 · 일하는 곳과 할 일을 적어 줘야 해요");
        for (String code : REQUIRED) {
            if (code.equals("ANNUAL_LEAVE") && "UNDER_FIVE".equals(r.size()) && !written.contains(code)) {
                // 시행령 별표1: 5인 미만에는 제60조(연차)가 적용되지 않는다
                notChecked.add("연차 휴가: 5인 미만 사업장에는 연차 휴가(제60조)가 적용되지 않아요.");
            } else if (written.contains(code)) {
                ok.add(code);
            } else {
                findings.add(new Finding(code, "MISSING", "계약서에 '" + name(code) + "'이(가) 안 보여요.", art17));
            }
        }

        int year = r.year() == null ? RuleEngine.today(Clock.systemUTC()).getYear() : r.year();
        BigDecimal min = RuleEngine.MIN_WAGES.get(year);
        String hourly = null, formula = null;
        if (r.wage() == null || r.wageType() == null) {
            notChecked.add("최저임금: 임금이 없어 비교하지 않았어요.");
        } else if (min == null) {
            notChecked.add("최저임금: " + year + "년 최저임금이 아직 등록되지 않았어요.");
        } else if (r.wageType().equals("MONTHLY") && r.weeklyHours() == null) {
            notChecked.add("최저임금: 일주일에 일하는 시간이 없어 월급을 시급으로 바꾸지 못했어요.");
        } else {
            BigDecimal per;
            switch (r.wageType()) {
                case "HOURLY" -> {
                    per = BigDecimal.valueOf(r.wage());
                    formula = "시급 " + RuleEngine.won(r.wage());
                }
                case "DAILY" -> {
                    per = BigDecimal.valueOf(r.wage()).divide(r.dailyHours(), 2, RoundingMode.DOWN);
                    formula = RuleEngine.won(r.wage()) + " ÷ " + RuleEngine.hours(r.dailyHours()) + "시간";
                }
                default -> {
                    // 월급은 주휴 시간을 넣은 월 기준시간으로 나눈다 (룰 엔진과 같은 식)
                    BigDecimal monthly = RuleEngine.monthlyHours(r.weeklyHours().min(BigDecimal.valueOf(40)));
                    per = BigDecimal.valueOf(r.wage()).divide(monthly, 2, RoundingMode.DOWN);
                    formula = RuleEngine.won(r.wage()) + " ÷ " + RuleEngine.hours(monthly) + "시간";
                }
            }
            per = per.setScale(2, RoundingMode.DOWN);
            hourly = per.stripTrailingZeros().toPlainString();
            if (per.compareTo(min) < 0) {
                findings.add(new Finding("MIN_WAGE", "PROBLEM", "시급이 " + RuleEngine.won2(per) + "으로 " + year + "년 최저임금 "
                        + RuleEngine.won(min.longValue()) + "보다 적어요.",
                        RuleEngine.basis("최저임금법", "제6조", "최저임금보다 적게 정한 부분은 무효이고 최저임금으로 정한 것으로 봐요")));
            }
        }

        if (r.weeklyHours() == null) {
            notChecked.add("주 52시간: 일주일에 일하는 시간이 없어 비교하지 않았어요.");
        } else if ("UNDER_FIVE".equals(r.size())) {
            notChecked.add("주 52시간: 5인 미만 사업장에는 연장 근로 한도(제53조)가 적용되지 않아요.");
        } else if (r.weeklyHours().compareTo(FIFTY_TWO) > 0) {
            findings.add(new Finding("OVER_52", "PROBLEM", "약속한 시간이 일주일 " + RuleEngine.hours(r.weeklyHours())
                    + "시간으로 52시간(기본 40 + 연장 12)을 넘어요.",
                    RuleEngine.basis("근로기준법", "제53조", "서로 합의해도 연장 근로는 일주일 12시간까지예요")));
        }
        return new Result(year, min == null ? null : min.longValue(), hourly, formula, ok, findings, notChecked,
                "법률 자문이 아니에요. 계약서 내용은 직접 확인한 대로 판단했어요.");
    }

    static String name(String code) {
        return switch (code) {
            case "WAGE" -> "임금(구성 · 계산 · 주는 방법)";
            case "HOURS" -> "일하는 시간";
            case "HOLIDAY" -> "쉬는 날(주휴일)";
            case "ANNUAL_LEAVE" -> "연차 휴가";
            case "PLACE" -> "일하는 곳";
            default -> "하는 일";
        };
    }

    private static void validate(Request r) {
        if (r.wageType() != null && !TYPES.contains(r.wageType())) {
            throw new InvalidCalculationException("INVALID_FIELD", "wageType", "임금 종류는 MONTHLY / HOURLY / DAILY 중 하나예요.");
        }
        if (r.wage() != null && r.wage() <= 0) {
            throw new InvalidCalculationException("INVALID_FIELD", "wage", "임금은 0보다 커야 해요.");
        }
        if (r.weeklyHours() != null && (r.weeklyHours().signum() <= 0 || r.weeklyHours().compareTo(BigDecimal.valueOf(168)) > 0)) {
            throw new InvalidCalculationException("INVALID_FIELD", "weeklyHours", "일주일에 일하는 시간은 0보다 크고 168시간 이하예요.");
        }
        if ("DAILY".equals(r.wageType()) && r.wage() != null
                && (r.dailyHours() == null || r.dailyHours().signum() <= 0 || r.dailyHours().compareTo(BigDecimal.valueOf(24)) > 0)) {
            throw new InvalidCalculationException("INVALID_FIELD", "dailyHours", "일급이면 하루에 일하는 시간(24시간 이하)이 필요해요.");
        }
        if (r.written() != null && !REQUIRED.containsAll(r.written())) {
            throw new InvalidCalculationException("INVALID_FIELD", "written", "written 은 " + REQUIRED + " 중에서 골라요.");
        }
    }
}
