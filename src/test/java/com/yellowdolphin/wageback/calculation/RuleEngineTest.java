package com.yellowdolphin.wageback.calculation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.yellowdolphin.wageback.calculation.CalculationRequest.Employment;
import com.yellowdolphin.wageback.calculation.CalculationRequest.Month;
import com.yellowdolphin.wageback.calculation.CalculationRequest.Schedule;
import com.yellowdolphin.wageback.calculation.CalculationRequest.Wage;
import com.yellowdolphin.wageback.calculation.CalculationRequest.Workplace;
import com.yellowdolphin.wageback.calculation.CalculationResult.Item;

// 경계 사례 시험. 기대값은 모두 손계산 값
class RuleEngineTest {

    // 월급 2,299,000원 ÷ 209시간 = 시간급 11,000.00원
    static final long MONTHLY_11000 = 2_299_000;

    @Test
    void 시간급_통상임금은_209시간_기준_소수_셋째_자리_버림() {
        CalculationResult r = calc(monthly("FIVE_OR_MORE", "2025-03-03", "2026-10-31", 2_156_880, 100_000, null,
                month("2026-09", 1_800_000, 10, 0, 0, 0)));
        // (2,156,880 + 100,000) ÷ 209 = 10,798.4689… → 10,798.46
        assertThat(r.hourlyOrdinaryWage().value()).isEqualTo("10798.46");
        assertThat(r.hourlyOrdinaryWage().formula()).isEqualTo("(2,156,880원 + 100,000원) ÷ 209시간");
        // 10,798.46 × 10 × 1.5 = 161,976.9 → 161,976
        assertThat(item(r, "OVERTIME", "2026-09").amount()).isEqualTo(161_976L);
        assertThat(item(r, "BASE_WAGE", "2026-09").formula()).isEqualTo("2,256,880원 − 받은 1,800,000원");
    }

    @Test
    void 경계1_5인_미만은_가산수당_0원과_경고() {
        CalculationResult r = calc(monthly("UNDER_FIVE", "2025-01-01", null, MONTHLY_11000, 0, null,
                month("2026-09", MONTHLY_11000, 10, 4, 8, 2)));
        assertThat(item(r, "BASE_WAGE", "2026-09").amount()).isZero();
        assertThat(item(r, "OVERTIME", "2026-09").amount()).isEqualTo(110_000L); // 11,000 × 10 × 1.0
        assertThat(item(r, "NIGHT", "2026-09").amount()).isZero(); // 가산 0
        assertThat(item(r, "HOLIDAY", "2026-09").amount()).isEqualTo(110_000L); // 11,000 × (8 + 2) × 1.0
        assertThat(r.warnings()).extracting("code").contains("UNDER_FIVE_EMPLOYEES");
        assertThat(r.summary().totalUnpaid()).isEqualTo(220_000L);
        assertThat(r.summary().totalPaid()).isEqualTo(MONTHLY_11000);
        assertThat(r.summary().totalDue()).isEqualTo(MONTHLY_11000 + 220_000L);
        assertThat(r.notApplied()).extracting("code").contains("SEVERANCE"); // 재직 중
    }

    @Test
    void 경계2_주14시간은_주휴0_퇴직금_없음() {
        CalculationResult r = calc(request("FIVE_OR_MORE", "2025-01-01", "2026-09-30", schedule(14, 7, 2),
                new Wage("HOURLY", 11_000L, null, null, 0L), null, month("2026-09", 0, 0, 0, 0, 0)));
        assertThat(item(r, "BASE_WAGE", "2026-09").amount()).isEqualTo(660_000L); // 11,000 × 14 × 30 ÷ 7
        assertThat(item(r, "WEEKLY_HOLIDAY", "2026-09").amount()).isZero();
        assertThat(item(r, "SEVERANCE", null).amount()).isZero();
    }

    @Test
    void 주20시간_시급제는_주휴_4시간_비례() {
        CalculationResult r = calc(request("FIVE_OR_MORE", "2025-01-01", null, schedule(20, 4, 5),
                new Wage("HOURLY", 11_000L, null, null, 0L), null, month("2026-09", 0, 0, 0, 0, 0)));
        Item w = item(r, "WEEKLY_HOLIDAY", "2026-09");
        assertThat(w.amount()).isEqualTo(188_571L); // 11,000 × 4 × 30 ÷ 7 = 188,571.4
        assertThat(w.estimated()).isTrue();
    }

    @Test
    void 경계3_364일은_퇴직금_없음_365일은_있음() {
        assertThat(item(calc(monthly("FIVE_OR_MORE", "2025-01-01", "2025-12-30", MONTHLY_11000, 0, null)), "SEVERANCE", null).amount())
                .isZero();
        CalculationResult r = calc(monthly("FIVE_OR_MORE", "2025-01-01", "2025-12-31", MONTHLY_11000, 0, null));
        assertThat(item(r, "SEVERANCE", null).amount()).isPositive();
    }

    @Test
    void 경계4_평균임금이_통상임금보다_적으면_통상임금() {
        CalculationResult r = calc(monthly("FIVE_OR_MORE", "2025-01-01", "2025-12-31", MONTHLY_11000, 0, null));
        // 평균임금 2,299,000 × 3 ÷ 92일 = 74,967.39 → 74,967 < 1일 통상임금 11,000 × 8 = 88,000
        assertThat(r.dailyAverageWage().value()).isEqualTo("74967");
        Item s = item(r, "SEVERANCE", null);
        assertThat(s.amount()).isEqualTo(2_640_000L); // 88,000 × 30 × 365 ÷ 365
        assertThat(s.formula()).contains("멘토 확인 필요");
        assertThat(s.estimated()).isTrue(); // 10~12월 월급을 가정

        // 3개월 임금 9,200,000원 ÷ 92일 = 100,000 > 88,000 → 평균임금 그대로
        CalculationResult r2 = calc(monthly("FIVE_OR_MORE", "2025-01-01", "2025-12-31", MONTHLY_11000, 0, 9_200_000L));
        assertThat(item(r2, "SEVERANCE", null).amount()).isEqualTo(3_000_000L);
        assertThat(item(r2, "SEVERANCE", null).estimated()).isFalse();
    }

    @Test
    void 경계5_하루10시간_4일은_연장8시간() {
        BigDecimal ten = BigDecimal.TEN;
        assertThat(RuleEngine.weeklyOvertimeHours(ten, ten, ten, ten)).isEqualByComparingTo("8");
        // 하루 8시간 × 6일 = 48시간 → 주 40시간 초과 8시간
        BigDecimal eight = BigDecimal.valueOf(8);
        assertThat(RuleEngine.weeklyOvertimeHours(eight, eight, eight, eight, eight, eight)).isEqualByComparingTo("8");
    }

    @Test
    void 경계6_야간과_연장이_겹치면_2배() {
        CalculationResult r = calc(monthly("FIVE_OR_MORE", "2025-01-01", null, MONTHLY_11000, 0, null,
                month("2026-09", MONTHLY_11000, 2, 2, 0, 0)));
        long ot = item(r, "OVERTIME", "2026-09").amount(), night = item(r, "NIGHT", "2026-09").amount();
        assertThat(ot).isEqualTo(33_000L); // 11,000 × 2 × 1.5
        assertThat(night).isEqualTo(11_000L); // 11,000 × 2 × 0.5
        assertThat(ot + night).isEqualTo(44_000L); // 11,000 × 2 × 2.0
        assertThat(item(r, "HOLIDAY", "2026-09").status()).isEqualTo("NO_DATA");
        assertThat(item(r, "HOLIDAY", "2026-09").amount()).isNull();
        assertThat(item(r, "WEEKLY_HOLIDAY", "2026-09").status()).isEqualTo("INCLUDED_IN_WAGE");
    }

    @Test
    void 경계7_휴일10시간은_8시간_15배_더하기_2시간_2배() {
        CalculationResult r = calc(monthly("FIVE_OR_MORE", "2025-01-01", null, MONTHLY_11000, 0, null,
                month("2026-09", MONTHLY_11000, 0, 0, 8, 2)));
        assertThat(item(r, "HOLIDAY", "2026-09").amount()).isEqualTo(176_000L); // 11,000×8×1.5 + 11,000×2×2.0
    }

    @Test
    void 경계8_월_중간_입사_퇴사는_일할_계산() {
        CalculationResult r = calc(monthly("FIVE_OR_MORE", "2026-09-16", "2026-10-10", MONTHLY_11000, 0, null,
                month("2026-09", 0, 0, 0, 0, 0), month("2026-10", 0, 0, 0, 0, 0)));
        assertThat(item(r, "BASE_WAGE", "2026-09").amount()).isEqualTo(1_149_500L); // 2,299,000 × 15 ÷ 30
        assertThat(item(r, "BASE_WAGE", "2026-10").amount()).isEqualTo(741_612L); // 2,299,000 × 10 ÷ 31 = 741,612.9
        assertThat(item(r, "SEVERANCE", null).amount()).isZero();
        assertThat(r.period().start()).isEqualTo("2026-09-16");
        assertThat(r.period().end()).isEqualTo("2026-10-10");
    }

    @Test
    void 경계9_최저임금_미달은_차액과_경고() {
        CalculationResult r = calc(monthly("FIVE_OR_MORE", "2025-01-01", null, 2_090_000, 0, null,
                month("2026-09", 2_090_000, 0, 0, 0, 0)));
        // 2,090,000 ÷ 209 = 10,000 < 2026년 10,320 → (10,320 − 10,000) × 209 = 66,880
        Item s = item(r, "MIN_WAGE_SHORTFALL", "2026-09");
        assertThat(s.amount()).isEqualTo(66_880L);
        assertThat(r.warnings()).extracting("code").contains("BELOW_MINIMUM_WAGE");
        assertThat(r.minimumWageYear()).isEqualTo(2026);
    }

    @Test
    void 경계10_잘못된_입력은_400_예외() {
        assertThatThrownBy(() -> calc(monthly("FIVE_OR_MORE", "2026-10-01", "2026-09-30", MONTHLY_11000, 0, null)))
                .isInstanceOfSatisfying(InvalidCalculationException.class, e -> {
                    assertThat(e.getCode()).isEqualTo("INVALID_PERIOD");
                    assertThat(e.getField()).isEqualTo("employment.endDate");
                });
        assertThatThrownBy(() -> calc(monthly("FIVE_OR_MORE", "2025-01-01", null, MONTHLY_11000, 0, null,
                month("2026-09", 0, -1, 0, 0, 0))))
                .isInstanceOfSatisfying(InvalidCalculationException.class, e -> {
                    assertThat(e.getCode()).isEqualTo("INVALID_HOURS");
                    assertThat(e.getField()).isEqualTo("months[0].overtimeHours");
                });
    }

    @Test
    void 최저임금표는_설정_파일에서_읽는다() {
        assertThat(RuleEngine.MIN_WAGES).containsEntry(2024, new BigDecimal("9860"))
                .containsEntry(2025, new BigDecimal("10030")).containsEntry(2026, new BigDecimal("10320"));
    }

    // ---- 도우미 ----

    static CalculationResult calc(CalculationRequest req) {
        return RuleEngine.calculate(req, "calc_test");
    }

    static Item item(CalculationResult r, String code, String month) {
        return r.items().stream().filter(i -> i.code().equals(code) && java.util.Objects.equals(i.month(), month))
                .findFirst().orElseThrow(() -> new AssertionError(code + " " + month + " 항목 없음"));
    }

    static Schedule schedule(double weekly, double daily, int days) {
        return new Schedule(BigDecimal.valueOf(weekly), BigDecimal.valueOf(daily), days);
    }

    static CalculationRequest monthly(String size, String start, String end, long base, long allowance, Long lastThree, Month... months) {
        List<CalculationRequest.Allowance> allowances = allowance == 0 ? List.of() : List.of(new CalculationRequest.Allowance("직책수당", allowance));
        return request(size, start, end, schedule(40, 8, 5), new Wage("MONTHLY", base, allowances, List.of(), 0L), lastThree, months);
    }

    static CalculationRequest request(String size, String start, String end, Schedule schedule, Wage wage, Long lastThree, Month... months) {
        return new CalculationRequest(new Workplace(size),
                new Employment(LocalDate.parse(start), end == null ? null : LocalDate.parse(end)),
                schedule, wage, List.of(months), lastThree);
    }

    static Month month(String month, long paid, double overtime, double night, double holiday8, double holidayOver8) {
        return new Month(month, paid, BigDecimal.valueOf(overtime), BigDecimal.valueOf(night),
                BigDecimal.valueOf(holiday8), BigDecimal.valueOf(holidayOver8), 0);
    }
}
