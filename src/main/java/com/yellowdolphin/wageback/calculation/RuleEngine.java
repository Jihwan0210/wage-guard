package com.yellowdolphin.wageback.calculation;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
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
import com.yellowdolphin.wageback.calculation.CalculationResult.Basis;
import com.yellowdolphin.wageback.calculation.CalculationResult.Item;
import com.yellowdolphin.wageback.calculation.CalculationResult.NotApplied;
import com.yellowdolphin.wageback.calculation.CalculationResult.Notice;
import com.yellowdolphin.wageback.calculation.CalculationResult.Period;
import com.yellowdolphin.wageback.calculation.CalculationResult.Summary;
import com.yellowdolphin.wageback.calculation.CalculationResult.Value;

// 룰 엔진: 항목별 체불액을 계산한다. 금액은 여기서만 만든다
final class RuleEngine {

    static final String VERSION = "1.0.0";
    static final Map<Integer, BigDecimal> MIN_WAGES = loadMinWages();

    private static final BigDecimal SEVEN = dec(7), EIGHT = dec(8), FIFTEEN = dec(15), FORTY = dec(40);

    private static final Map<String, String> LABELS = Map.of(
            "BASE_WAGE", "못 받은 월급",
            "OVERTIME", "연장근로수당",
            "NIGHT", "야간근로수당",
            "HOLIDAY", "휴일근로수당",
            "WEEKLY_HOLIDAY", "주휴수당",
            "SEVERANCE", "퇴직금",
            "MIN_WAGE_SHORTFALL", "최저임금 차액");

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
            "MIN_WAGE_SHORTFALL", List.of(basis("최저임금법", "제6조", "최저임금보다 적게 정한 임금은 최저임금으로 정한 것으로 봐요.")));

    private final CalculationRequest req;
    private final LocalDate start, end;
    private final String type;
    private final boolean underFive;
    private final long base, allowances;
    private final BigDecimal weekly, weeklyHolidayHours, monthlyHours, hourly;
    private final String hourlyFormula;
    private final Map<String, Notice> warnings = new LinkedHashMap<>();

    private RuleEngine(CalculationRequest req) {
        this.req = req;
        start = req.employment().startDate();
        end = req.employment().endDate();
        type = req.wage().type();
        underFive = req.workplace().size().equals("UNDER_FIVE");
        base = req.wage().baseAmount();
        allowances = sum(req.wage().ordinaryAllowances());
        weekly = req.schedule().weeklyContractHours();
        // 주휴시간: 주 15시간 미만 0, 그 외 주 40시간 기준 8시간 비례
        weeklyHolidayHours = weekly.compareTo(FIFTEEN) < 0 ? BigDecimal.ZERO : div(weekly.multiply(EIGHT), FORTY).min(EIGHT);
        // 월 소정근로시간 = (주 소정 + 주휴) × 365 ÷ 7 ÷ 12, 주 40시간이면 209시간
        monthlyHours = div(weekly.add(weeklyHolidayHours).multiply(dec(365)), dec(84)).setScale(0, RoundingMode.HALF_UP);

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
        notApplied.add(new NotApplied("ANNUAL_LEAVE", "연차수당은 이번 버전에서 계산하지 않아요."));
        notApplied.add(new NotApplied("DISMISSAL_NOTICE", "해고예고수당은 이번 버전에서 계산하지 않아요."));

        long unpaid = total(items);
        return new CalculationResult(id, VERSION, minimumWageYear(),
                new Period(start.toString(), end == null ? null : end.toString()),
                new Summary(due, paid, unpaid),
                new Value(hourly.toPlainString(), hourlyFormula),
                dailyAverage, items, List.copyOf(warnings.values()), notApplied);
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

        String rateNote = underFive ? " (5인 미만: 가산 없음)" : "";
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
        if (min == null) {
            warn("MIN_WAGE_UNKNOWN_" + ym.getYear(), ym.getYear() + "년 최저임금 기준이 없어 그 해는 최저임금 비교를 하지 않았어요.");
        } else if (hourly.compareTo(min) < 0) {
            String f = "(" + won(min.longValue()) + " − " + won2(hourly) + ") × " + hours(paidHours.setScale(2, RoundingMode.DOWN)) + "시간";
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
        long average = trunc(div(total, dec(windowDays)));
        String averageFormula = "3개월 임금 총액 " + won(trunc(wages)) + (bonus > 0 ? " + 연간 상여금 " + won(bonus) + " × 3 ÷ 12" : "")
                + " ÷ " + windowDays + "일";

        BigDecimal dailyHours = req.schedule().dailyContractHours() != null ? req.schedule().dailyContractHours()
                : div(weekly, dec(req.schedule().workDaysPerWeek() == null ? 5 : req.schedule().workDaysPerWeek())).min(EIGHT);
        long ordinaryDaily = trunc(hourly.multiply(dailyHours));
        long daily = average;
        String note = "";
        if (average < ordinaryDaily) {
            daily = ordinaryDaily;
            note = " (평균임금 " + won(average) + "이 1일 통상임금보다 적어 " + won2(hourly) + " × " + hours(dailyHours)
                    + "시간으로 계산, 멘토 확인 필요)";
            warn("SEVERANCE_ORDINARY_WAGE", "평균임금이 통상임금보다 적어 퇴직금을 1일 통상임금으로 계산했어요. 계산 방식은 멘토 확인이 필요해요.");
        }
        if (!assumed.isEmpty()) {
            note += " (" + String.join("·", assumed) + "은 계약대로 일하고 받았다고 가정)";
        }
        long amount = trunc(div(dec(daily).multiply(dec(30)).multiply(dec(serviceDays)), dec(365)));
        Item item = item("SEVERANCE", null, !assumed.isEmpty(), amount, won(daily) + " × 30일 × " + serviceDays + "일 ÷ 365" + note);
        return new Severance(item, new Value(Long.toString(average), averageFormula));
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
        return req.months() == null ? List.of() : req.months();
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
        return new Item(code, LABELS.get(code), month, "CALCULATED", estimated, amount, formula, BASIS.get(code));
    }

    private static Item noData(String code, String month, String formula) {
        return new Item(code, LABELS.get(code), month, "NO_DATA", false, null, formula, BASIS.get(code));
    }

    private static Basis basis(String law, String article, String summary) {
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
