package com.yellowdolphin.wageback.calculation;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;

import com.yellowdolphin.wageback.calculation.CalculationRequest.Allowance;
import com.yellowdolphin.wageback.calculation.CalculationRequest.Month;
import com.yellowdolphin.wageback.calculation.CalculationRequest.WorkDay;
import com.yellowdolphin.wageback.calculation.CalculationResult.Basis;
import com.yellowdolphin.wageback.calculation.CalculationResult.Item;
import com.yellowdolphin.wageback.calculation.CalculationResult.NotApplied;
import com.yellowdolphin.wageback.calculation.CalculationResult.Notice;
import com.yellowdolphin.wageback.calculation.CalculationResult.Period;
import com.yellowdolphin.wageback.calculation.CalculationResult.Summary;
import com.yellowdolphin.wageback.calculation.CalculationResult.Value;
import com.yellowdolphin.wageback.calculation.CalculationResult.WorkWeek;

// 룰 엔진: 항목별 체불액을 계산한다. 금액은 여기서만 만든다
final class RuleEngine {

    static final String VERSION = "1.2.0";
    static final Map<Integer, BigDecimal> MIN_WAGES = loadMinWages();
    // 재직 중 지연이자 시행일 (근로기준법 제37조 개정, 2024. 10. 22. 공포)
    static final LocalDate EMPLOYED_INTEREST_FROM = LocalDate.of(2025, 10, 23);

    private static final BigDecimal SEVEN = dec(7), EIGHT = dec(8), FIFTEEN = dec(15), FORTY = dec(40), SIXTY = dec(60);

    private static final Map<String, String> LABELS = Map.ofEntries(
            Map.entry("BASE_WAGE", "못 받은 월급"),
            Map.entry("OVERTIME", "연장근로수당"),
            Map.entry("NIGHT", "야간근로수당"),
            Map.entry("HOLIDAY", "휴일근로수당"),
            Map.entry("WEEKLY_HOLIDAY", "주휴수당"),
            Map.entry("SEVERANCE", "퇴직금"),
            Map.entry("MIN_WAGE_SHORTFALL", "최저임금 차액"),
            Map.entry("DISMISSAL_NOTICE", "해고예고수당"),
            Map.entry("ANNUAL_LEAVE", "못 쓴 연차수당"),
            Map.entry("SHUTDOWN_ALLOWANCE", "휴업수당"),
            Map.entry("DELAY_INTEREST", "늦게 준 돈의 이자(지연이자)"));

    private static final Map<String, List<Basis>> BASIS_V2 = Map.of("SHUTDOWN_ALLOWANCE", List.of(
                    basis("근로기준법", "제46조 제1항", "회사 사정으로 쉬게 하면 평균임금의 70% 이상을 줘야 해요. 70%가 통상임금보다 많으면 통상임금을 줘도 돼요."),
                    basis("근로기준법 시행령", "제26조", "쉬는 동안 일부를 받았으면, 그만큼 빼고 계산해요.")),
            "DELAY_INTEREST", List.of(
                    basis("근로기준법", "제37조 제1항", "그만둔 뒤 14일 안에 주지 않은 임금·퇴직금에는 그다음 날부터 이자가 붙어요. 2025년 10월 23일부터는 일하는 중에 월급날 주지 않은 임금에도 붙어요."),
                    basis("근로기준법 시행령", "제17조", "그 이자는 1년에 20%예요.")));

    private static final Map<String, List<Basis>> BASIS = Map.of(
            "BASE_WAGE", List.of(basis("근로기준법", "제43조", "임금은 매달 정해진 날에 전부 직접 줘야 해요.")),
            "OVERTIME", List.of(basis("근로기준법", "제56조 제1항", "연장근로에는 통상임금의 50% 이상을 더 줘야 해요.")),
            "NIGHT", List.of(basis("근로기준법", "제56조 제3항", "오후 10시~오전 6시 근로에는 통상임금의 50% 이상을 더 줘야 해요.")),
            "HOLIDAY", List.of(basis("근로기준법", "제56조 제2항", "휴일근로 8시간까지는 50%, 넘는 시간은 100%를 더 줘야 해요.")),
            "WEEKLY_HOLIDAY", List.of(
                    basis("근로기준법", "제55조 제1항", "한 주를 빠짐없이 일하면 하루치 유급휴일이 생겨요."),
                    basis("근로기준법", "제18조 제3항", "한 주 15시간 미만 일하면 주휴가 없어요.")),
            "SEVERANCE", List.of(
                    basis("근로자퇴직급여 보장법", "제8조 제1항", "1년 일할 때마다 30일분 이상의 평균임금을 줘야 해요."),
                    basis("근로자퇴직급여 보장법", "제4조 제1항", "1년 미만이거나 한 주 15시간 미만이면 퇴직금이 없어요."),
                    basis("근로기준법", "제2조 제2항", "평균임금이 통상임금보다 적으면 통상임금으로 계산해요.")),
            "MIN_WAGE_SHORTFALL", List.of(basis("최저임금법", "제6조", "최저임금보다 적게 정한 임금은 최저임금으로 정한 것으로 봐요.")),
            "DISMISSAL_NOTICE", List.of(basis("근로기준법", "제26조", "해고하려면 30일 전에 알려야 하고, 안 그러면 30일분 이상의 통상임금을 줘야 해요. 3개월 미만 일한 사람은 빼요.")),
            "ANNUAL_LEAVE", List.of(
                    basis("근로기준법", "제60조 제1항", "1년 동안 80% 이상 출근하면 연차 15일이 생겨요."),
                    basis("근로기준법", "제60조 제2항", "1년 미만이면 한 달 개근할 때마다 1일이 생겨요(최대 11일)."),
                    basis("근로기준법", "제60조 제4항", "3년 이상 일하면 2년마다 1일씩 늘어요(최대 25일).")));

    private final CalculationRequest req;
    private final LocalDate start, end;
    private final String type;
    private final boolean underFive;
    private final long base, allowances;
    private final BigDecimal weekly, weeklyHolidayHours, monthlyHours, hourly, dailyHours;
    // 1일 통상임금 = 시간급 × 하루 소정시간. 전(소수 둘째 자리)까지 두고 마지막 금액에서만 원 미만을 버린다 (고용노동부 계산 예시와 같게)
    private final BigDecimal ordinaryDaily;
    private final String hourlyFormula;
    private final Map<String, Notice> warnings = new LinkedHashMap<>();
    private final List<WorkWeek> workWeeks = new ArrayList<>();
    private final Map<String, Integer> recordedDays = new LinkedHashMap<>(); // 달 → 하루 기록 수
    private final List<Month> months;

    private RuleEngine(CalculationRequest req) {
        this.req = req;
        start = req.employment().startDate();
        end = req.employment().endDate();
        type = req.wage().type();
        underFive = req.workplace().size().equals("UNDER_FIVE");
        base = req.wage().baseAmount();
        allowances = sum(req.wage().ordinaryAllowances());
        weekly = req.schedule().weeklyContractHours();
        weeklyHolidayHours = weeklyHolidayHours(weekly);
        monthlyHours = monthlyHours(weekly);

        String allowanceText = allowanceText(req.wage().ordinaryAllowances());
        BigDecimal perHour;
        if (type.equals("MONTHLY")) {
            perHour = div(dec(base + allowances), monthlyHours);
            hourlyFormula = (allowances > 0 ? "(" + won(base) + allowanceText + ")" : won(base)) + " ÷ " + hours(monthlyHours) + "시간";
        } else {
            perHour = type.equals("HOURLY") ? dec(base) : div(dec(base), req.schedule().dailyContractHours());
            String f = type.equals("HOURLY") ? won(base) : won(base) + " ÷ " + hours(req.schedule().dailyContractHours()) + "시간";
            if (allowances > 0) {
                perHour = perHour.add(div(dec(allowances), monthlyHours));
                f += " + (" + allowanceText.substring(3) + ") ÷ " + hours(monthlyHours) + "시간";
            }
            hourlyFormula = f;
        }
        hourly = perHour.setScale(2, RoundingMode.DOWN);
        BigDecimal perDay = req.schedule().dailyContractHours() != null ? req.schedule().dailyContractHours()
                : div(weekly, dec(req.schedule().workDaysPerWeek() == null ? 5 : req.schedule().workDaysPerWeek())).min(EIGHT);
        if (weekly.compareTo(FORTY) < 0) {
            // 단시간: 4주 소정근로시간 ÷ 통상근로자 4주 소정근로일수(20일) (근로기준법 시행령 제9조 별표2)
            BigDecimal legal = div(weekly.multiply(dec(4)), dec(20));
            if (legal.compareTo(perDay) != 0) {
                warn("PART_TIME_DAILY_HOURS", "주 40시간보다 적게 일해서 하루 소정근로시간을 주 " + hours(weekly) + "시간 × 4 ÷ 20 = "
                        + hours(legal) + "시간으로 계산했어요. 연차수당 · 해고예고수당 같은 하루치 금액에 쓰여요. (근로기준법 시행령 별표2)");
            }
            perDay = legal;
        }
        dailyHours = perDay;
        ordinaryDaily = hourly.multiply(dailyHours).setScale(2, RoundingMode.DOWN);
        months = withWorkDays();
    }

    static CalculationResult calculate(CalculationRequest req, String id) {
        validate(req);
        return new RuleEngine(req).run(id);
    }

    private CalculationResult run(String id) {
        List<Item> items = new ArrayList<>();
        List<NotApplied> notApplied = new ArrayList<>();
        long due = 0, paid = 0;
        if (underFive) {
            warn("UNDER_FIVE_EMPLOYEES", "5인 미만 사업장은 연장·야간·휴일 가산수당이 적용되지 않아 가산분을 0원으로 계산했어요. (근로기준법 제11조)");
        }
        if (type.equals("MONTHLY") && weekly.compareTo(FORTY) != 0) {
            warn("MONTHLY_HOURS_CHECK", "주 40시간이 아니어서 월 기준시간을 " + hours(monthlyHours) + "시간으로 계산했어요. 계약서의 월 기준시간과 다르면 확인이 필요해요.");
        }
        for (Month m : months()) {
            List<Item> monthItems = monthItems(YearMonth.parse(m.month()), m);
            due += total(monthItems);
            long p = nz(m.paidAmount());
            paid += p;
            items.addAll(subtractPaid(monthItems, p));
        }
        Value dailyAverage = null;
        if (end == null) {
            notApplied.add(new NotApplied("SEVERANCE", "재직 중이라 퇴직금은 계산하지 않아요."));
        } else {
            Severance s = severance();
            dailyAverage = s.dailyAverage();
            due += s.item().amount();
            items.add(s.item());
        }
        for (Item extra : new Item[] {annualLeave(notApplied), dismissal(notApplied), shutdown(notApplied), delayInterest(items, notApplied)}) {
            if (extra != null) {
                due += extra.amount();
                items.add(extra);
            }
        }

        long unpaid = total(items);
        return new CalculationResult(id, VERSION, minimumWageYear(),
                new Period(start.toString(), end == null ? null : end.toString()),
                new Summary(due, paid, unpaid),
                new Value(hourly.toPlainString(), hourlyFormula),
                dailyAverage, items, List.copyOf(warnings.values()), notApplied, List.copyOf(workWeeks));
    }

    // 한 달 치 항목 (받은 돈을 빼기 전 받아야 할 금액)
    private List<Item> monthItems(YearMonth ym, Month m) {
        String mo = ym.toString();
        int days = employedDays(ym), dim = ym.lengthOfMonth();
        List<Item> items = new ArrayList<>();
        BigDecimal paidHours; // 최저임금 비교에 쓰는 유급 시간

        if (type.equals("MONTHLY")) {
            long monthly = base + allowances;
            long amount = monthly;
            String f = won(monthly);
            if (days < dim) {
                amount = trunc(div(dec(monthly).multiply(dec(days)), dec(dim)));
                f += " × " + days + "일 ÷ " + dim + "일 (일할 계산)";
            }
            items.add(item("BASE_WAGE", mo, false, amount, f));
            paidHours = div(monthlyHours.multiply(dec(days)), dec(dim));
        } else {
            boolean hourlyType = type.equals("HOURLY");
            BigDecimal perWeek = hourlyType ? dec(base).multiply(weekly) : dec(base).multiply(dec(req.schedule().workDaysPerWeek()));
            BigDecimal amount = div(perWeek.multiply(dec(days)), SEVEN);
            String f = won(base) + (hourlyType ? " × 주 " + hours(weekly) + "시간" : " × 주 " + req.schedule().workDaysPerWeek() + "일")
                    + " × " + days + "일 ÷ 7";
            if (allowances > 0) {
                amount = amount.add(div(dec(allowances).multiply(dec(days)), dec(dim)));
                f += " + 수당 " + won(allowances) + (days < dim ? " × " + days + "일 ÷ " + dim + "일" : "");
            }
            items.add(item("BASE_WAGE", mo, true, trunc(amount), f + " (일한 날 ÷ 7로 주 수를 추정)"));
            paidHours = div(weekly.multiply(dec(days)), SEVEN);
        }

        String rateNote = (underFive ? " (5인 미만: 가산 없음)" : "")
                + (recordedDays.containsKey(mo) ? " (하루 기록 " + recordedDays.get(mo) + "일에서 나눈 시간)" : "");
        items.add(rateItem("OVERTIME", mo, m.overtimeHours(), underFive ? "1.0" : "1.5", rateNote, "입력한 연장 근무시간이 없어요"));
        items.add(rateItem("NIGHT", mo, m.nightHours(), underFive ? "0" : "0.5", rateNote, "입력한 야간 근무시간이 없어요"));

        BigDecimal within8 = nz(m.holidayHoursWithin8()), over8 = nz(m.holidayHoursOver8());
        if (within8.signum() == 0 && over8.signum() == 0) {
            items.add(noData("HOLIDAY", mo, "입력한 휴일 근무시간이 없어요"));
        } else {
            String r1 = underFive ? "1.0" : "1.5", r2 = underFive ? "1.0" : "2.0";
            long amount = trunc(hourly.multiply(within8).multiply(new BigDecimal(r1)).add(hourly.multiply(over8).multiply(new BigDecimal(r2))));
            String f = won2(hourly) + " × " + hours(within8) + "시간 × " + r1 + " + " + won2(hourly) + " × " + hours(over8) + "시간 × " + r2;
            items.add(item("HOLIDAY", mo, false, amount, f + rateNote));
        }

        if (weekly.compareTo(FIFTEEN) < 0) {
            items.add(item("WEEKLY_HOLIDAY", mo, false, 0L, "주 15시간 미만이라 주휴수당이 없어요"));
        } else if (type.equals("MONTHLY")) {
            items.add(new Item("WEEKLY_HOLIDAY", LABELS.get("WEEKLY_HOLIDAY"), mo, "INCLUDED_IN_WAGE", false, null,
                    "월 " + hours(monthlyHours) + "시간 안에 주휴 시간이 포함돼요", BASIS.get("WEEKLY_HOLIDAY")));
        } else {
            int absent = m.absentWeeks() == null ? 0 : m.absentWeeks();
            int weekDays = Math.max(0, days - 7 * absent);
            BigDecimal holidayHours = div(weeklyHolidayHours.multiply(dec(weekDays)), SEVEN);
            String f = won2(hourly) + " × 주휴 " + hours(weeklyHolidayHours) + "시간 × " + days + "일 ÷ 7"
                    + (absent > 0 ? " (결근 " + absent + "주 뺌)" : "") + " (일한 날 ÷ 7로 주 수를 추정)";
            items.add(item("WEEKLY_HOLIDAY", mo, true, trunc(hourly.multiply(holidayHours)), f));
            paidHours = paidHours.add(holidayHours);
        }

        BigDecimal min = MIN_WAGES.get(ym.getYear());
        int probation = probationDays(ym);
        String minText = min == null ? "" : won(min.longValue());
        if (min != null && probation > 0) {
            // 수습 날은 90%: 최저임금 × (1 − 0.1 × 수습 날 ÷ 그 달 일한 날)
            min = min.multiply(dec(days * 10L - probation)).divide(dec(days * 10L), 2, RoundingMode.DOWN);
            minText = won2(min) + "(수습 " + probation + "일은 90%)";
            warn("PROBATION_MIN_WAGE", "수습 3개월 동안은 최저임금의 90%까지만 줘도 돼서, 그 날들은 90%로 비교했어요. (최저임금법 제5조 제2항)");
        }
        if (min == null) {
            warn("MIN_WAGE_UNKNOWN_" + ym.getYear(), ym.getYear() + "년 최저임금 기준이 없어 그 해는 최저임금 비교를 하지 않았어요.");
        } else if (hourly.compareTo(min) < 0) {
            String f = "(" + minText + " − " + won2(hourly) + ") × " + hours(paidHours.setScale(2, RoundingMode.DOWN)) + "시간";
            items.add(item("MIN_WAGE_SHORTFALL", mo, false, trunc(min.subtract(hourly).multiply(paidHours)), f));
            warn("BELOW_MINIMUM_WAGE_" + ym.getYear(), ym.getYear() + "년 최저임금(시간급 " + won(min.longValue()) + ")보다 적게 받은 달이 있어요.");
        }
        return items;
    }

    private record Severance(Item item, Value dailyAverage) {
    }

    private Severance severance() {
        long serviceDays = ChronoUnit.DAYS.between(start, end) + 1;
        if (weekly.compareTo(FIFTEEN) < 0) {
            return new Severance(item("SEVERANCE", null, false, 0L, "주 15시간 미만이라 퇴직금이 없어요"), null);
        }
        if (end.isBefore(start.plusYears(1).minusDays(1))) {
            return new Severance(item("SEVERANCE", null, false, 0L, "1년 미만(" + serviceDays + "일) 일해서 퇴직금이 없어요"), null);
        }
        // 평균임금: 퇴직일(마지막 근무일 다음 날) 전 3개월 임금 ÷ 그 기간 날수
        LocalDate retire = end.plusDays(1), from = retire.minusMonths(3);
        long windowDays = ChronoUnit.DAYS.between(from, retire);
        BigDecimal wages;
        List<String> assumed = new ArrayList<>();
        if (req.lastThreeMonthsWages() != null) {
            wages = dec(req.lastThreeMonthsWages());
        } else {
            wages = BigDecimal.ZERO;
            for (YearMonth ym = YearMonth.from(from); !ym.isAfter(YearMonth.from(end)); ym = ym.plusMonths(1)) {
                LocalDate a = max(from, ym.atDay(1)), b = min(end, ym.atEndOfMonth());
                String key = ym.toString();
                Month given = months().stream().filter(x -> x.month().equals(key)).findFirst().orElse(null);
                if (given == null) {
                    assumed.add(ym.getMonthValue() + "월");
                    given = new Month(ym.toString(), 0L, null, null, null, null, 0);
                }
                long overlap = ChronoUnit.DAYS.between(a, b) + 1;
                wages = wages.add(div(dec(total(monthItems(ym, given))).multiply(dec(overlap)), dec(employedDays(ym))));
            }
        }
        long bonus = nz(req.wage().annualBonus());
        BigDecimal total = wages.add(div(dec(bonus).multiply(dec(3)), dec(12)));
        // 평균임금도 전까지 둔다 (고용노동부 퇴직금 계산 예시: 88,641원 31전)
        BigDecimal average = div(total, dec(windowDays)).setScale(2, RoundingMode.DOWN);
        String averageFormula = "3개월 임금 총액 " + won(trunc(wages)) + (bonus > 0 ? " + 연간 상여금 " + won(bonus) + " × 3 ÷ 12" : "")
                + " ÷ " + windowDays + "일";

        BigDecimal daily = average;
        String note = "";
        if (average.compareTo(ordinaryDaily) < 0) {
            daily = ordinaryDaily;
            note = " (평균임금 " + won2(average) + "이 1일 통상임금보다 적어 " + won2(hourly) + " × " + hours(dailyHours)
                    + "시간으로 계산)";
            warn("SEVERANCE_ORDINARY_WAGE", "평균임금이 통상임금보다 적어 퇴직금을 1일 통상임금으로 계산했어요 (근로기준법 제2조 제2항).");
        }
        if (!assumed.isEmpty()) {
            note += " (" + String.join("·", assumed) + "은 계약대로 일하고 받았다고 가정)";
        }
        long amount = trunc(div(daily.multiply(dec(30)).multiply(dec(serviceDays)), dec(365)));
        Item item = item("SEVERANCE", null, !assumed.isEmpty(), amount, won2(daily) + " × 30일 × " + serviceDays + "일 ÷ 365" + note);
        return new Severance(item, new Value(average.toPlainString(), averageFormula));
    }

    // ---- 해고예고수당 · 연차수당 · 수습 (v2) ----

    // 해고예고수당: 30일 전에 알리지 않고 해고하면 1일 통상임금 × 30일 (근로기준법 제26조)
    private Item dismissal(List<NotApplied> notApplied) {
        if (req.dismissal() == null || !Boolean.TRUE.equals(req.dismissal().dismissed())) {
            notApplied.add(new NotApplied("DISMISSAL_NOTICE", "해고되었다고 입력하지 않아 해고예고수당은 계산하지 않았어요."));
            return null;
        }
        if (end == null) {
            notApplied.add(new NotApplied("DISMISSAL_NOTICE", "해고된 날(마지막으로 일한 날)을 넣어야 해고예고수당을 계산할 수 있어요."));
            return null;
        }
        int notice = req.dismissal().noticeDaysBefore() == null ? 0 : req.dismissal().noticeDaysBefore();
        if (end.isBefore(start.plusMonths(3).minusDays(1))) {
            return item("DISMISSAL_NOTICE", null, false, 0L, "3개월 미만 일해서 해고예고수당이 없어요");
        }
        if (notice >= 30) {
            return item("DISMISSAL_NOTICE", null, false, 0L, notice + "일 전에 알려서 해고예고수당이 없어요");
        }
        warn("DISMISSAL_CHECK", "천재지변 같은 일이나, 일하는 사람이 일부러 회사에 큰 손해를 끼쳐 해고된 경우에는 해고예고수당을 못 받을 수 있어요. 확인이 필요해요.");
        String f = won2(ordinaryDaily) + " × 30일 (1일 통상임금 " + won2(hourly) + " × " + hours(dailyHours) + "시간)"
                + (notice > 0 ? " (" + notice + "일 전에 알림 — 30일보다 짧아 30일분 전부로 계산, 확인 필요)" : " (미리 알리지 않음)");
        return item("DISMISSAL_NOTICE", null, notice > 0, trunc(ordinaryDaily.multiply(dec(30))), f);
    }

    // 못 쓴 연차수당: 마지막 1년(1년 미만이면 전체)에 생긴 연차 − 쓴 날 × 1일 통상임금 (근로기준법 제60조)
    private Item annualLeave(List<NotApplied> notApplied) {
        String reason = end == null ? "재직 중이라 연차수당은 계산하지 않아요."
                : underFive ? "5인 미만 사업장은 연차휴가 규정이 적용되지 않아요. (근로기준법 제11조)"
                : req.annualLeave() == null ? "쓴 연차 일수를 넣지 않아 연차수당은 계산하지 않았어요." : null;
        if (reason != null) {
            notApplied.add(new NotApplied("ANNUAL_LEAVE", reason));
            return null;
        }
        if (weekly.compareTo(FIFTEEN) < 0) {
            return item("ANNUAL_LEAVE", null, false, 0L, "주 15시간 미만이라 연차가 없어요");
        }
        // 기간을 다 채우고 다음 날에도 일해야 연차가 생긴다고 보고 마지막 근무일까지로 센다
        long years = ChronoUnit.YEARS.between(start, end);
        long earned = years == 0 ? Math.min(11, ChronoUnit.MONTHS.between(start, end)) : Math.min(25, 15 + (years - 1) / 2);
        int used = req.annualLeave().usedDays() == null ? 0 : req.annualLeave().usedDays();
        long unused = Math.max(0, earned - used);
        warn("ANNUAL_LEAVE_CHECK", "연차수당은 출근율 80% 이상(1년 미만은 매달 빠짐없이 출근)이라고 보고, 마지막 1년에 생긴 연차만 계산했어요. "
                + "그 전 해에 남은 연차나 회사의 연차 사용 권유(사용 촉진)는 반영하지 않았어요. 확인이 필요해요.");
        String f = won2(ordinaryDaily) + " × 못 쓴 " + unused + "일 (" + (years == 0 ? "1년 미만, 개근한 달 수" : years + "년 일함")
                + " → 생긴 연차 " + earned + "일 − 쓴 " + used + "일)";
        return item("ANNUAL_LEAVE", null, true, trunc(ordinaryDaily.multiply(dec(unused))), f);
    }

    // 휴업수당: (평균임금 × 쉰 날 − 받은 돈) × 70%. 70%가 통상임금보다 많으면 (통상임금 × 쉰 날 − 받은 돈)
    // (근로기준법 제46조 제1항, 시행령 제26조. 5인 미만은 시행령 별표1에 제46조가 없어 적용 안 함)
    // 평균임금은 약속한 월급 × 12 ÷ 365 로 추정한다
    private Item shutdown(List<NotApplied> notApplied) {
        CalculationRequest.Shutdown s = req.shutdown();
        if (s == null || s.days() == null || s.days() <= 0) {
            return null;
        }
        if (underFive) {
            notApplied.add(new NotApplied("SHUTDOWN_ALLOWANCE", "5인 미만 사업장은 휴업수당 규정이 적용되지 않아요. 계약서나 회사 규칙에 따로 정한 것이 있는지 확인해 보세요. (근로기준법 시행령 별표1)"));
            return null;
        }
        long monthlyWage = type.equals("MONTHLY") ? base + allowances : trunc(hourly.multiply(monthlyHours));
        BigDecimal average = div(dec(monthlyWage * 12), dec(365)).setScale(2, RoundingMode.DOWN);
        long days = s.days(), got = nz(s.paidAmount());
        long amount = trunc(average.multiply(dec(days)).subtract(dec(got)).max(BigDecimal.ZERO).multiply(new BigDecimal("0.7")));
        String f = "(1일 평균임금 " + won2(average) + " × " + days + "일" + (got > 0 ? " − 받은 " + won(got) : "") + ") × 70% (평균임금은 월 " + won(monthlyWage) + " × 12 ÷ 365로 추정)";
        if (average.multiply(new BigDecimal("0.7")).compareTo(ordinaryDaily) > 0) {
            amount = Math.max(0, trunc(ordinaryDaily.multiply(dec(days))) - got);
            f = "1일 통상임금 " + won2(ordinaryDaily) + " × " + days + "일" + (got > 0 ? " − 받은 " + won(got) : "") + " (평균임금 70%가 통상임금보다 많아 통상임금으로)";
        }
        warn("SHUTDOWN_CHECK", "휴업수당은 회사 사정으로 쉰 경우에만 받아요. 평균임금은 약속한 월급으로 추정했어요. 노동위원회 승인을 받은 휴업이면 더 적을 수 있어요. 확인이 필요해요.");
        return item("SHUTDOWN_ALLOWANCE", null, true, amount, f);
    }

    // 지연이자: 그만둔 날(마지막 근무일 다음 날)부터 14일이 지나도 못 받은 임금 · 퇴직금 × 20% × 늦은 날 ÷ 365
    // (근로기준법 제37조 제1항, 시행령 제17조). 해고예고수당 · 휴업수당은 임금인지 확실하지 않아 빼고 센다
    private Item delayInterest(List<Item> items, List<NotApplied> notApplied) {
        if (req.delayInterest() == null) {
            return null;
        }
        LocalDate baseDate = req.delayInterest().baseDate() == null ? LocalDate.now() : req.delayInterest().baseDate();
        if (end == null) {
            return employedInterest(items, baseDate, notApplied);
        }
        LocalDate from = end.plusDays(1).plusDays(15); // 그만둔 날 다음 날부터 14일 안 → 15일째부터 이자
        long late = Math.max(0, ChronoUnit.DAYS.between(from, baseDate) + 1);
        long principal = items.stream().filter(i -> !Set.of("DISMISSAL_NOTICE", "SHUTDOWN_ALLOWANCE").contains(i.code()))
                .mapToLong(i -> i.amount() == null ? 0 : i.amount()).sum();
        warn("DELAY_INTEREST_CHECK", "지연이자는 그만둔 날 다음 날부터 14일이 지난 날부터 " + baseDate + "까지 센 추정이에요. 해고예고수당 · 휴업수당은 빼고 셌어요. 확인이 필요해요.");
        String f = won(principal) + " × 20% × " + late + "일 ÷ 365 (" + from + "부터 " + baseDate + "까지)";
        return item("DELAY_INTEREST", null, true, trunc(div(dec(principal).multiply(dec(late)).multiply(dec(20)), dec(36500))), f);
    }

    // 재직 중 지연이자: 달마다 못 받은 돈 × 20% × 월급날 다음 날부터 늦은 날 ÷ 365 (근로기준법 제37조 제1항 제2호, 2025. 10. 23. 시행)
    // 월급날은 그 달 마지막 날로 본다. 부칙 제2조에 따라 시행일 뒤에 월급날이 온 달만 센다
    private Item employedInterest(List<Item> items, LocalDate baseDate, List<NotApplied> notApplied) {
        Map<String, Long> byMonth = new TreeMap<>();
        items.stream().filter(i -> i.month() != null && i.amount() != null).forEach(i -> byMonth.merge(i.month(), i.amount(), Long::sum));
        BigDecimal sum = BigDecimal.ZERO;
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, Long> e : byMonth.entrySet()) {
            LocalDate payday = YearMonth.parse(e.getKey()).atEndOfMonth();
            long late = ChronoUnit.DAYS.between(payday, baseDate);
            if (e.getValue() == 0 || payday.isBefore(EMPLOYED_INTEREST_FROM) || late <= 0) {
                continue;
            }
            sum = sum.add(dec(e.getValue()).multiply(dec(late)));
            parts.add(won(e.getValue()) + " × " + late + "일(" + e.getKey() + "분)");
        }
        if (parts.isEmpty()) {
            notApplied.add(new NotApplied("DELAY_INTEREST", "재직 중 지연이자는 2025년 10월 23일 뒤 월급날이 지난 못 받은 임금에만 붙어요."));
            return null;
        }
        warn("DELAY_INTEREST_CHECK", "재직 중 지연이자는 월급날을 그 달 마지막 날로 보고 다음 날부터 " + baseDate
                + "까지 센 추정이에요. 실제 월급날이 다르면 금액도 달라져요. 확인이 필요해요.");
        String f = "(" + String.join(" + ", parts) + ") × 20% ÷ 365 (월급날을 그 달 마지막 날로 봄)";
        return item("DELAY_INTEREST", null, true, trunc(div(sum.multiply(dec(20)), dec(36500))), f);
    }

    // 그 달에서 수습 90%를 적용할 날 수. 1년 이상 계약 + 단순노무 아님 + 입사 후 3개월 이내만 (최저임금법 제5조 제2항, 시행령 제3조)
    private int probationDays(YearMonth ym) {
        CalculationRequest.Probation p = req.probation();
        if (p == null || !Boolean.TRUE.equals(p.onProbation())) {
            return 0;
        }
        if (!Boolean.TRUE.equals(p.contractOneYearOrMore()) || !Boolean.FALSE.equals(p.simpleLabor())) {
            warn("PROBATION_FULL_MIN_WAGE", "1년 미만 계약이거나 단순노무 일이면(또는 모르면) 수습이어도 최저임금을 다 줘야 한다고 보고 계산했어요.");
            return 0;
        }
        LocalDate last = min(start.plusMonths(3).minusDays(1), end == null ? ym.atEndOfMonth() : min(end, ym.atEndOfMonth()));
        LocalDate first = max(start, ym.atDay(1));
        return first.isAfter(last) ? 0 : (int) ChronoUnit.DAYS.between(first, last) + 1;
    }

    // ---- 하루 기록 (v2) ----

    // 하루 기록이 있는 달은 연장 · 야간 · 휴일 시간을 기록에서 나눈 값으로 바꾼다 (그 달에 적은 월 합계 시간 대신)
    private List<Month> withWorkDays() {
        List<Month> given = req.months() == null ? List.of() : req.months();
        List<WorkDay> days = req.workDays() == null ? List.of() : req.workDays();
        if (days.isEmpty()) {
            return given;
        }
        List<Month> out = new ArrayList<>();
        for (Month m : given) {
            List<WorkDay> mine = days.stream().filter(d -> YearMonth.from(d.date()).toString().equals(m.month()))
                    .sorted(Comparator.comparing(WorkDay::date)).toList();
            out.add(mine.isEmpty() ? m : fromDays(m, mine));
        }
        warn("DAILY_RECORDS", "하루 기록이 있는 달은 기록에서 연장·야간·휴일 시간을 나눠 계산했어요. 쉬는 시간은 밤 10시~아침 6시가 아닌 때에 쉬었다고 보고, 달이 바뀌는 주는 그 달 날짜만으로 셌어요.");
        return out;
    }

    // 주(월요일 시작)마다: 평일 연장 = max(하루 8시간 넘은 합, 주 40시간 넘은 시간).
    // 휴일에 일한 시간은 연장 가산과 겹쳐 세지 않고 휴일근로(8시간까지 · 넘은 시간)로만 센다
    // (근로기준법 제56조 제2항, 대법원 2018. 6. 21. 선고 2011다112391 전원합의체 판결 — 가산 중복 없음)
    private Month fromDays(Month m, List<WorkDay> days) {
        Map<LocalDate, List<WorkDay>> weeks = new TreeMap<>();
        for (WorkDay d : days) {
            weeks.computeIfAbsent(d.date().with(DayOfWeek.MONDAY), k -> new ArrayList<>()).add(d);
        }
        BigDecimal ot = BigDecimal.ZERO, night = BigDecimal.ZERO, within8 = BigDecimal.ZERO, over8 = BigDecimal.ZERO;
        for (Map.Entry<LocalDate, List<WorkDay>> w : weeks.entrySet()) {
            List<BigDecimal> weekdays = new ArrayList<>();
            BigDecimal worked = BigDecimal.ZERO, wNight = BigDecimal.ZERO, w8 = BigDecimal.ZERO, wOver = BigDecimal.ZERO, dailyOver = BigDecimal.ZERO;
            for (WorkDay d : w.getValue()) {
                int[] t = minutes(d);
                BigDecimal h = div(dec(t[0]), SIXTY);
                worked = worked.add(h);
                wNight = wNight.add(div(dec(t[1]), SIXTY));
                if (Boolean.TRUE.equals(d.holiday())) {
                    w8 = w8.add(h.min(EIGHT));
                    wOver = wOver.add(h.subtract(EIGHT).max(BigDecimal.ZERO));
                } else {
                    weekdays.add(h);
                    dailyOver = dailyOver.add(h.subtract(EIGHT).max(BigDecimal.ZERO));
                }
            }
            BigDecimal weekdayTotal = weekdays.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal wOt = weeklyOvertimeHours(weekdays.toArray(BigDecimal[]::new));
            String f = "평일 " + weekdays.size() + "일 " + plain(weekdayTotal) + "시간: 하루 8시간 넘은 합 " + plain(dailyOver)
                    + "시간과 주 40시간 넘은 " + plain(weekdayTotal.subtract(FORTY).max(BigDecimal.ZERO)) + "시간 중 큰 값 → 연장 " + plain(wOt) + "시간"
                    + (w8.signum() > 0 ? " · 휴일 " + plain(w8.add(wOver)) + "시간 (8시간까지 " + plain(w8) + " · 넘은 " + plain(wOver) + ")" : "")
                    + " · 밤 10시~아침 6시 " + plain(wNight) + "시간";
            workWeeks.add(new WorkWeek(m.month(), w.getKey().toString(), w.getValue().size(), plain(worked), plain(wOt), plain(wNight),
                    plain(w8), plain(wOver), f));
            ot = ot.add(wOt);
            night = night.add(wNight);
            within8 = within8.add(w8);
            over8 = over8.add(wOver);
        }
        recordedDays.put(m.month(), days.size());
        return new Month(m.month(), m.paidAmount(), h2(ot), h2(night), h2(within8), h2(over8), m.absentWeeks());
    }

    // 하루 기록 → {일한 분, 그중 밤 10시~아침 6시 분}. 퇴근 시각이 출근보다 이르거나 같으면 다음 날 퇴근
    static int[] minutes(WorkDay d) {
        int s = clock(d.start()), e = clock(d.end());
        if (e <= s) {
            e += 1440;
        }
        int worked = e - s - (d.breakMinutes() == null ? 0 : d.breakMinutes());
        int night = 0;
        for (int day = -1440; day <= 1440; day += 1440) { // 전날 · 그날 · 다음 날의 22:00 ~ 다음 날 06:00
            night += Math.max(0, Math.min(e, day + 1800) - Math.max(s, day + 1320));
        }
        return new int[] { worked, Math.min(night, worked) };
    }

    private static int clock(String hhmm) {
        LocalTime t = LocalTime.parse(hhmm);
        return t.getHour() * 60 + t.getMinute();
    }

    private static BigDecimal h2(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    private static String plain(BigDecimal v) {
        return h2(v).stripTrailingZeros().toPlainString();
    }

    // 한 주의 하루별 근무시간 → 연장시간 (1일 8시간 초과 합과 1주 40시간 초과 중 큰 쪽)
    static BigDecimal weeklyOvertimeHours(BigDecimal... dailyHours) {
        BigDecimal daily = BigDecimal.ZERO, week = BigDecimal.ZERO;
        for (BigDecimal h : dailyHours) {
            daily = daily.add(h.subtract(EIGHT).max(BigDecimal.ZERO));
            week = week.add(h);
        }
        return daily.max(week.subtract(FORTY).max(BigDecimal.ZERO));
    }

    private Item rateItem(String code, String mo, BigDecimal hours, String rate, String note, String noData) {
        if (hours == null || hours.signum() == 0) {
            return noData(code, mo, noData);
        }
        long amount = trunc(hourly.multiply(hours).multiply(new BigDecimal(rate)));
        return item(code, mo, false, amount, won2(hourly) + " × " + hours(hours) + "시간 × " + rate + note);
    }

    // 받은 돈은 기본급부터 차례로 뺀다
    private static List<Item> subtractPaid(List<Item> items, long paid) {
        List<Item> out = new ArrayList<>();
        for (Item it : items) {
            long take = it.amount() == null ? 0 : Math.min(it.amount(), paid);
            paid -= take;
            out.add(take == 0 ? it : new Item(it.code(), it.label(), it.month(), it.status(), it.estimated(),
                    it.amount() - take, it.formula() + " − 받은 " + won(take), it.legalBasis()));
        }
        return out;
    }

    private int employedDays(YearMonth ym) {
        LocalDate a = max(start, ym.atDay(1)), b = end == null ? ym.atEndOfMonth() : min(end, ym.atEndOfMonth());
        return (int) ChronoUnit.DAYS.between(a, b) + 1;
    }

    private int minimumWageYear() {
        return months().stream().map(m -> YearMonth.parse(m.month()).getYear()).max(Integer::compare)
                .orElse(end != null ? end.getYear() : LocalDate.now().getYear());
    }

    private List<Month> months() {
        return months;
    }

    private void warn(String key, String message) {
        warnings.putIfAbsent(key, new Notice(key.replaceAll("_\\d+$", ""), message));
    }

    // ---- 입력 검사: 잘못되면 400 ----

    static void validate(CalculationRequest r) {
        need(r.workplace() != null && r.workplace().size() != null, "workplace.size");
        oneOf(r.workplace().size(), Set.of("FIVE_OR_MORE", "UNDER_FIVE"), "workplace.size");
        need(r.employment() != null && r.employment().startDate() != null, "employment.startDate");
        LocalDate s = r.employment().startDate(), e = r.employment().endDate();
        if (e != null && s.isAfter(e)) {
            throw new InvalidCalculationException("INVALID_PERIOD", "employment.endDate", "입사일이 퇴사일보다 늦습니다.");
        }
        need(r.schedule() != null && r.schedule().weeklyContractHours() != null, "schedule.weeklyContractHours");
        if (r.schedule().weeklyContractHours().signum() <= 0) {
            throw new InvalidCalculationException("INVALID_HOURS", "schedule.weeklyContractHours", "주 소정근로시간은 0보다 커야 해요.");
        }
        hours(r.schedule().dailyContractHours(), "schedule.dailyContractHours");
        Integer days = r.schedule().workDaysPerWeek();
        if (days != null && (days < 1 || days > 7)) {
            throw new InvalidCalculationException("INVALID_VALUE", "schedule.workDaysPerWeek", "주 근무일은 1~7일이어야 해요.");
        }
        need(r.wage() != null && r.wage().type() != null, "wage.type");
        oneOf(r.wage().type(), Set.of("MONTHLY", "HOURLY", "DAILY"), "wage.type");
        need(r.wage().baseAmount() != null, "wage.baseAmount");
        if (r.wage().type().equals("DAILY")) {
            need(r.schedule().dailyContractHours() != null && r.schedule().dailyContractHours().signum() > 0, "schedule.dailyContractHours");
            need(days != null, "schedule.workDaysPerWeek");
        }
        amount(r.wage().baseAmount(), "wage.baseAmount");
        amount(r.wage().annualBonus(), "wage.annualBonus");
        amount(r.lastThreeMonthsWages(), "lastThreeMonthsWages");
        if (r.shutdown() != null) {
            amount(r.shutdown().paidAmount(), "shutdown.paidAmount");
            if (r.shutdown().days() != null && r.shutdown().days() < 0) {
                throw new InvalidCalculationException("INVALID_VALUE", "shutdown.days", "쉰 날 수는 0 이상이어야 해요.");
            }
        }
        allowances(r.wage().ordinaryAllowances(), "wage.ordinaryAllowances");
        allowances(r.wage().otherAllowances(), "wage.otherAllowances");
        List<Month> months = r.months() == null ? List.of() : r.months();
        for (int i = 0; i < months.size(); i++) {
            Month m = months.get(i);
            String p = "months[" + i + "].";
            YearMonth ym;
            try {
                ym = YearMonth.parse(String.valueOf(m.month()));
            } catch (DateTimeParseException ex) {
                throw new InvalidCalculationException("INVALID_MONTH", p + "month", "달은 YYYY-MM 형식으로 적어 주세요.");
            }
            if (ym.isBefore(YearMonth.from(s)) || (e != null && ym.isAfter(YearMonth.from(e)))) {
                throw new InvalidCalculationException("INVALID_MONTH", p + "month", "일한 기간 밖의 달이에요.");
            }
            amount(m.paidAmount(), p + "paidAmount");
            hours(m.overtimeHours(), p + "overtimeHours");
            hours(m.nightHours(), p + "nightHours");
            hours(m.holidayHoursWithin8(), p + "holidayHoursWithin8");
            hours(m.holidayHoursOver8(), p + "holidayHoursOver8");
            if (m.absentWeeks() != null && m.absentWeeks() < 0) {
                throw new InvalidCalculationException("INVALID_VALUE", p + "absentWeeks", "결근 주 수는 0 이상이어야 해요.");
            }
        }
        Set<String> monthKeys = new HashSet<>();
        months.forEach(m -> monthKeys.add(m.month()));
        Set<LocalDate> seen = new HashSet<>();
        List<WorkDay> workDays = r.workDays() == null ? List.of() : r.workDays();
        for (int i = 0; i < workDays.size(); i++) {
            WorkDay d = workDays.get(i);
            String p = "workDays[" + i + "].";
            need(d.date() != null, p + "date");
            need(d.start() != null, p + "start");
            need(d.end() != null, p + "end");
            if (!seen.add(d.date())) {
                throw new InvalidCalculationException("INVALID_VALUE", p + "date", "같은 날짜가 두 번 있어요.");
            }
            if (d.date().isBefore(s) || (e != null && d.date().isAfter(e))) {
                throw new InvalidCalculationException("INVALID_PERIOD", p + "date", "일한 기간 밖의 날짜예요.");
            }
            if (!monthKeys.contains(YearMonth.from(d.date()).toString())) {
                throw new InvalidCalculationException("INVALID_MONTH", p + "date", "months 에 없는 달의 기록이에요. 그 달에 받은 돈도 함께 보내 주세요.");
            }
            for (String key : List.of("start", "end")) {
                try {
                    clock(key.equals("start") ? d.start() : d.end());
                } catch (DateTimeParseException ex) {
                    throw new InvalidCalculationException("INVALID_VALUE", p + key, "시각은 HH:mm 형식으로 적어 주세요.");
                }
            }
            if (d.breakMinutes() != null && d.breakMinutes() < 0) {
                throw new InvalidCalculationException("INVALID_HOURS", p + "breakMinutes", "쉬는 시간은 0 이상이어야 해요.");
            }
            if (minutes(d)[0] <= 0) {
                throw new InvalidCalculationException("INVALID_HOURS", p + "breakMinutes", "쉬는 시간을 빼면 일한 시간이 없어요.");
            }
        }
    }

    private static void need(boolean ok, String field) {
        if (!ok) {
            throw new InvalidCalculationException("INVALID_REQUIRED", field, "꼭 입력해야 하는 값이에요.");
        }
    }

    private static void oneOf(String v, Set<String> allowed, String field) {
        if (!allowed.contains(v)) {
            throw new InvalidCalculationException("INVALID_VALUE", field, "허용되지 않는 값이에요: " + allowed);
        }
    }

    private static void hours(BigDecimal v, String field) {
        if (v != null && v.signum() < 0) {
            throw new InvalidCalculationException("INVALID_HOURS", field, "시간은 0 이상이어야 해요.");
        }
    }

    private static void amount(Long v, String field) {
        if (v != null && v < 0) {
            throw new InvalidCalculationException("INVALID_AMOUNT", field, "금액은 0 이상이어야 해요.");
        }
    }

    private static void allowances(List<Allowance> list, String field) {
        for (int i = 0; list != null && i < list.size(); i++) {
            amount(list.get(i).amount(), field + "[" + i + "].amount");
        }
    }

    // ---- 도우미 ----

    private static Item item(String code, String month, boolean estimated, Long amount, String formula) {
        return new Item(code, LABELS.get(code), month, "CALCULATED", estimated, amount, formula, basisOf(code));
    }

    private static Item noData(String code, String month, String formula) {
        return new Item(code, LABELS.get(code), month, "NO_DATA", false, null, formula, basisOf(code));
    }

    private static List<Basis> basisOf(String code) {
        return BASIS.containsKey(code) ? BASIS.get(code) : BASIS_V2.get(code);
    }

    // 주휴시간: 주 15시간 미만 0, 그 외 주 40시간 기준 8시간 비례
    static BigDecimal weeklyHolidayHours(BigDecimal weekly) {
        return weekly.compareTo(FIFTEEN) < 0 ? BigDecimal.ZERO : div(weekly.multiply(EIGHT), FORTY).min(EIGHT);
    }

    // 월 소정근로시간 = (주 소정 + 주휴) × 365 ÷ 7 ÷ 12, 주 40시간이면 209시간
    static BigDecimal monthlyHours(BigDecimal weekly) {
        return div(weekly.add(weeklyHolidayHours(weekly)).multiply(dec(365)), dec(84)).setScale(0, RoundingMode.HALF_UP);
    }

    static Basis basis(String law, String article, String summary) {
        String no = article.split(" ")[0];
        return new Basis(law, article, summary, "https://www.law.go.kr/법령/" + law.replace(" ", "") + "/" + no);
    }

    private static Map<Integer, BigDecimal> loadMinWages() {
        try {
            Properties p = PropertiesLoaderUtils.loadProperties(new ClassPathResource("minimum-wage.properties"));
            Map<Integer, BigDecimal> map = new TreeMap<>();
            p.forEach((k, v) -> map.put(Integer.valueOf(k.toString().trim()), new BigDecimal(v.toString().trim())));
            return map;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String allowanceText(List<Allowance> list) {
        StringBuilder sb = new StringBuilder();
        for (Allowance a : list == null ? List.<Allowance>of() : list) {
            sb.append(" + ").append(won(nz(a.amount())));
        }
        return sb.toString();
    }

    private static long sum(List<Allowance> list) {
        return list == null ? 0 : list.stream().mapToLong(a -> nz(a.amount())).sum();
    }

    private static long total(List<Item> items) {
        return items.stream().mapToLong(i -> i.amount() == null ? 0 : i.amount()).sum();
    }

    private static BigDecimal dec(long v) {
        return BigDecimal.valueOf(v);
    }

    private static BigDecimal div(BigDecimal a, BigDecimal b) {
        return a.divide(b, 10, RoundingMode.HALF_UP);
    }

    // 원 미만 버림
    private static long trunc(BigDecimal v) {
        return v.setScale(0, RoundingMode.DOWN).longValueExact();
    }

    private static long nz(Long v) {
        return v == null ? 0 : v;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static LocalDate max(LocalDate a, LocalDate b) {
        return a.isAfter(b) ? a : b;
    }

    private static LocalDate min(LocalDate a, LocalDate b) {
        return a.isBefore(b) ? a : b;
    }

    static String won(long v) {
        return new DecimalFormat("#,##0").format(v) + "원";
    }

    static String won2(BigDecimal v) {
        return new DecimalFormat("#,##0.00").format(v) + "원";
    }

    static String hours(BigDecimal v) {
        return new DecimalFormat("#,##0.##").format(v);
    }
}
