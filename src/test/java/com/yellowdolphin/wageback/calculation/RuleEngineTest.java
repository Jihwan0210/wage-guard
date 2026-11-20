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
import com.yellowdolphin.wageback.calculation.CalculationRequest.WorkDay;
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
        assertThat(s.formula()).contains("1일 통상임금보다 적어").doesNotContain("멘토");
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

    // ---- v2 하루 기록 ----

    @Test
    void 하루기록_10시간_4일은_연장8시간() {
        // 2026-09-07(월) ~ 10(목) 09:00~20:00, 쉬는 시간 1시간 → 하루 10시간, 주 40시간
        CalculationResult r = calc(withDays(monthly("FIVE_OR_MORE", "2025-01-01", null, MONTHLY_11000, 0, null,
                month("2026-09", MONTHLY_11000, 0, 0, 0, 0)),
                day("2026-09-07", "09:00", "20:00", 60, false), day("2026-09-08", "09:00", "20:00", 60, false),
                day("2026-09-09", "09:00", "20:00", 60, false), day("2026-09-10", "09:00", "20:00", 60, false)));
        Item ot = item(r, "OVERTIME", "2026-09");
        assertThat(ot.amount()).isEqualTo(132_000L); // 11,000 × 8 × 1.5
        assertThat(ot.formula()).contains("하루 기록 4일");
        assertThat(item(r, "NIGHT", "2026-09").status()).isEqualTo("NO_DATA");
        assertThat(r.workWeeks()).hasSize(1);
        assertThat(r.workWeeks().get(0).weekStart()).isEqualTo("2026-09-07");
        assertThat(r.workWeeks().get(0).workedHours()).isEqualTo("40");
        assertThat(r.workWeeks().get(0).overtimeHours()).isEqualTo("8");
        assertThat(r.warnings()).extracting("code").contains("DAILY_RECORDS");
    }

    @Test
    void 하루기록_8시간_6일은_주40시간_넘은_8시간이_연장() {
        CalculationResult r = calc(withDays(monthly("FIVE_OR_MORE", "2025-01-01", null, MONTHLY_11000, 0, null,
                month("2026-09", MONTHLY_11000, 0, 0, 0, 0)),
                day("2026-09-07", "09:00", "18:00", 60, false), day("2026-09-08", "09:00", "18:00", 60, false),
                day("2026-09-09", "09:00", "18:00", 60, false), day("2026-09-10", "09:00", "18:00", 60, false),
                day("2026-09-11", "09:00", "18:00", 60, false), day("2026-09-12", "09:00", "18:00", 60, false)));
        assertThat(item(r, "OVERTIME", "2026-09").amount()).isEqualTo(132_000L);
    }

    @Test
    void 하루기록_밤샘은_다음날_퇴근과_야간시간() {
        // 18:00 ~ 다음 날 03:00, 쉬는 1시간 → 8시간 근무, 그중 22:00~03:00 = 5시간 야간
        int[] m = RuleEngine.minutes(day("2026-09-07", "18:00", "03:00", 60, false));
        assertThat(m).containsExactly(480, 300);
        // 04:00 ~ 07:00 → 3시간 중 04:00~06:00 = 2시간 야간
        assertThat(RuleEngine.minutes(day("2026-09-07", "04:00", "07:00", 0, false))).containsExactly(180, 120);
        CalculationResult r = calc(withDays(monthly("FIVE_OR_MORE", "2025-01-01", null, MONTHLY_11000, 0, null,
                month("2026-09", MONTHLY_11000, 0, 0, 0, 0)), day("2026-09-07", "18:00", "03:00", 60, false)));
        assertThat(item(r, "NIGHT", "2026-09").amount()).isEqualTo(27_500L); // 11,000 × 5 × 0.5
        assertThat(item(r, "OVERTIME", "2026-09").status()).isEqualTo("NO_DATA");
    }

    @Test
    void 하루기록_휴일10시간은_주40시간에_넣지_않고_휴일근로() {
        // 월~금 8시간 + 일요일(휴일) 10시간 → 연장 0, 휴일 8시간 × 1.5 + 2시간 × 2.0
        CalculationResult r = calc(withDays(monthly("FIVE_OR_MORE", "2025-01-01", null, MONTHLY_11000, 0, null,
                month("2026-09", MONTHLY_11000, 0, 0, 0, 0)),
                day("2026-09-07", "09:00", "18:00", 60, false), day("2026-09-08", "09:00", "18:00", 60, false),
                day("2026-09-09", "09:00", "18:00", 60, false), day("2026-09-10", "09:00", "18:00", 60, false),
                day("2026-09-11", "09:00", "18:00", 60, false), day("2026-09-13", "09:00", "20:00", 60, true)));
        assertThat(item(r, "OVERTIME", "2026-09").status()).isEqualTo("NO_DATA");
        assertThat(item(r, "HOLIDAY", "2026-09").amount()).isEqualTo(176_000L);
        assertThat(r.workWeeks().get(0).holidayHoursOver8()).isEqualTo("2");
    }

    @Test
    void 하루기록이_있으면_그_달의_월합계_시간_대신_쓴다() {
        CalculationResult r = calc(withDays(monthly("FIVE_OR_MORE", "2025-01-01", null, MONTHLY_11000, 0, null,
                month("2026-09", MONTHLY_11000, 30, 0, 0, 0)), day("2026-09-07", "09:00", "20:30", 60, false)));
        assertThat(item(r, "OVERTIME", "2026-09").amount()).isEqualTo(41_250L); // 2.5시간 × 11,000 × 1.5
    }

    @Test
    void 하루기록_잘못된_입력은_400_예외() {
        CalculationRequest base = monthly("FIVE_OR_MORE", "2025-01-01", null, MONTHLY_11000, 0, null, month("2026-09", 0, 0, 0, 0, 0));
        assertThatThrownBy(() -> calc(withDays(base, day("2026-08-03", "09:00", "18:00", 60, false))))
                .isInstanceOfSatisfying(InvalidCalculationException.class, e -> {
                    assertThat(e.getCode()).isEqualTo("INVALID_MONTH");
                    assertThat(e.getField()).isEqualTo("workDays[0].date");
                });
        assertThatThrownBy(() -> calc(withDays(base, day("2026-09-07", "9시", "18:00", 60, false))))
                .isInstanceOfSatisfying(InvalidCalculationException.class, e -> assertThat(e.getField()).isEqualTo("workDays[0].start"));
        assertThatThrownBy(() -> calc(withDays(base, day("2026-09-07", "09:00", "10:00", 60, false))))
                .isInstanceOfSatisfying(InvalidCalculationException.class, e -> assertThat(e.getCode()).isEqualTo("INVALID_HOURS"));
        assertThatThrownBy(() -> calc(withDays(base, day("2026-09-07", "09:00", "18:00", 0, false), day("2026-09-07", "09:00", "18:00", 0, false))))
                .isInstanceOfSatisfying(InvalidCalculationException.class, e -> assertThat(e.getField()).isEqualTo("workDays[1].date"));
    }

    @Test
    void 최저임금표는_설정_파일에서_읽는다() {
        assertThat(RuleEngine.MIN_WAGES).containsEntry(2024, new BigDecimal("9860"))
                .containsEntry(2025, new BigDecimal("10030")).containsEntry(2026, new BigDecimal("10320"));
    }

    // ---- 도우미 ----

    // ---- v2 6단계: 해고예고수당 · 연차수당 · 수습 ----

    static CalculationRequest extra(CalculationRequest r, CalculationRequest.Dismissal d, CalculationRequest.AnnualLeave a, CalculationRequest.Probation p) {
        return new CalculationRequest(r.workplace(), r.employment(), r.schedule(), r.wage(), r.months(), r.lastThreeMonthsWages(), null, d, a, p);
    }

    @Test
    void 해고예고수당은_1일_통상임금_30일분() {
        CalculationRequest base = monthly("FIVE_OR_MORE", "2025-01-01", "2026-09-30", MONTHLY_11000, 0, null, month("2026-09", MONTHLY_11000, 0, 0, 0, 0));
        // 11,000 × 8시간 = 88,000 × 30 = 2,640,000
        Item none = item(calc(extra(base, new CalculationRequest.Dismissal(true, 0), null, null)), "DISMISSAL_NOTICE", null);
        assertThat(none.amount()).isEqualTo(2_640_000L);
        assertThat(none.estimated()).isFalse();
        Item short10 = item(calc(extra(base, new CalculationRequest.Dismissal(true, 10), null, null)), "DISMISSAL_NOTICE", null);
        assertThat(short10.amount()).isEqualTo(2_640_000L);
        assertThat(short10.estimated()).isTrue();
        assertThat(item(calc(extra(base, new CalculationRequest.Dismissal(true, 30), null, null)), "DISMISSAL_NOTICE", null).amount()).isZero();
        assertThat(calc(base).notApplied()).extracting("code").contains("DISMISSAL_NOTICE");
    }

    @Test
    void 해고예고수당_3개월_미만은_0원() {
        CalculationRequest r = monthly("FIVE_OR_MORE", "2026-08-01", "2026-10-15", MONTHLY_11000, 0, null, month("2026-09", MONTHLY_11000, 0, 0, 0, 0));
        assertThat(item(calc(extra(r, new CalculationRequest.Dismissal(true, 0), null, null)), "DISMISSAL_NOTICE", null).amount()).isZero();
    }

    @Test
    void 연차수당_1년_이상은_15일_3년부터_2년마다_1일() {
        Month m = month("2026-09", MONTHLY_11000, 0, 0, 0, 0);
        // 1년 일함: 15일 − 쓴 3일 = 12일 × 88,000 = 1,056,000
        CalculationResult r = calc(extra(monthly("FIVE_OR_MORE", "2025-01-01", "2026-09-30", MONTHLY_11000, 0, null, m), null, new CalculationRequest.AnnualLeave(3), null));
        assertThat(item(r, "ANNUAL_LEAVE", null).amount()).isEqualTo(1_056_000L);
        assertThat(item(r, "ANNUAL_LEAVE", null).estimated()).isTrue();
        assertThat(r.warnings()).extracting("code").contains("ANNUAL_LEAVE_CHECK");
        // 4년 일함: 15 + (4 − 1) ÷ 2 = 16일 × 88,000 = 1,408,000
        assertThat(item(calc(extra(monthly("FIVE_OR_MORE", "2022-01-01", "2026-09-30", MONTHLY_11000, 0, null, m), null, new CalculationRequest.AnnualLeave(0), null)),
                "ANNUAL_LEAVE", null).amount()).isEqualTo(1_408_000L);
    }

    @Test
    void 연차수당_1년_미만은_개근한_달마다_1일_딱_1년은_11일() {
        Month m = month("2026-09", MONTHLY_11000, 0, 0, 0, 0);
        // 2026-01-01 ~ 2026-09-30: 다음 날 근무가 없어 9월분은 안 생김 → 8일 × 88,000 = 704,000
        assertThat(item(calc(extra(monthly("FIVE_OR_MORE", "2026-01-01", "2026-09-30", MONTHLY_11000, 0, null, m), null, new CalculationRequest.AnnualLeave(0), null)),
                "ANNUAL_LEAVE", null).amount()).isEqualTo(704_000L);
        // 2025-10-01 ~ 2026-09-30 (딱 1년): 15일은 안 생기고 11일 × 88,000 = 968,000
        assertThat(item(calc(extra(monthly("FIVE_OR_MORE", "2025-10-01", "2026-09-30", MONTHLY_11000, 0, null, m), null, new CalculationRequest.AnnualLeave(0), null)),
                "ANNUAL_LEAVE", null).amount()).isEqualTo(968_000L);
        // 5인 미만은 계산 안 함
        assertThat(calc(extra(monthly("UNDER_FIVE", "2025-01-01", "2026-09-30", MONTHLY_11000, 0, null, m), null, new CalculationRequest.AnnualLeave(0), null))
                .notApplied()).extracting("code").contains("ANNUAL_LEAVE");
    }

    @Test
    void 수습_3개월은_최저임금_90퍼센트_단순노무는_100퍼센트() {
        // 2026년 최저임금 10,320 × 0.9 = 9,288원. 7/1 입사라 수습은 9/30 까지
        CalculationRequest r = request("FIVE_OR_MORE", "2026-07-01", "2026-10-31", schedule(40, 8, 5), new Wage("HOURLY", 9_288L, null, null, 0L), null,
                month("2026-09", 0, 0, 0, 0, 0), month("2026-10", 0, 0, 0, 0, 0));
        CalculationResult ok = calc(extra(r, null, null, new CalculationRequest.Probation(true, true, false)));
        assertThat(ok.items()).noneMatch(i -> i.code().equals("MIN_WAGE_SHORTFALL") && "2026-09".equals(i.month()));
        assertThat(item(ok, "MIN_WAGE_SHORTFALL", "2026-10").formula()).startsWith("(10,320원 − 9,288.00원)");
        assertThat(ok.warnings()).extracting("code").contains("PROBATION_MIN_WAGE");
        CalculationResult simple = calc(extra(r, null, null, new CalculationRequest.Probation(true, true, true)));
        assertThat(item(simple, "MIN_WAGE_SHORTFALL", "2026-09").amount()).isPositive();
        assertThat(simple.warnings()).extracting("code").contains("PROBATION_FULL_MIN_WAGE");
    }

    static CalculationRequest more(CalculationRequest r, CalculationRequest.Dismissal d, CalculationRequest.DelayInterest i, CalculationRequest.Shutdown s) {
        return new CalculationRequest(r.workplace(), r.employment(), r.schedule(), r.wage(), r.months(), r.lastThreeMonthsWages(), null, d, null, null, i, s);
    }

    @Test
    void 지연이자는_그만두고_14일_지난_다음_날부터_연_20퍼센트() {
        // 2026-01-01 ~ 09-30 (1년 미만이라 퇴직금 0), 9월 월급 2,299,000원 못 받음
        CalculationRequest r = monthly("FIVE_OR_MORE", "2026-01-01", "2026-09-30", MONTHLY_11000, 0, null, month("2026-09", 0, 0, 0, 0, 0));
        // 그만둔 날 10/1, 14일 안 = 10/15 까지 → 10/16 ~ 11/14 = 30일. 2,299,000 × 20% × 30 ÷ 365 = 37,791.78 → 37,791
        CalculationResult res = calc(more(r, null, new CalculationRequest.DelayInterest(LocalDate.parse("2026-11-14")), null));
        assertThat(item(res, "DELAY_INTEREST", null).amount()).isEqualTo(37_791L);
        assertThat(item(res, "DELAY_INTEREST", null).estimated()).isTrue();
        assertThat(res.warnings()).extracting("code").contains("DELAY_INTEREST_CHECK");
        // 해고예고수당(2,640,000)은 원금에서 빼므로 이자는 같다
        assertThat(item(calc(more(r, new CalculationRequest.Dismissal(true, 0), new CalculationRequest.DelayInterest(LocalDate.parse("2026-11-14")), null)),
                "DELAY_INTEREST", null).amount()).isEqualTo(37_791L);
        // 14일이 안 지났으면 0원
        assertThat(item(calc(more(r, null, new CalculationRequest.DelayInterest(LocalDate.parse("2026-10-15")), null)), "DELAY_INTEREST", null).amount()).isZero();
        // 재직 중이면 계산 안 함
        CalculationRequest stay = monthly("FIVE_OR_MORE", "2026-01-01", null, MONTHLY_11000, 0, null, month("2026-09", 0, 0, 0, 0, 0));
        assertThat(calc(more(stay, null, new CalculationRequest.DelayInterest(null), null)).notApplied()).extracting("code").contains("DELAY_INTEREST");
    }

    @Test
    void 휴업수당은_평균임금_70퍼센트_5인_미만은_적용_안_함() {
        CalculationRequest r = monthly("FIVE_OR_MORE", "2025-01-01", null, MONTHLY_11000, 0, null, month("2026-09", MONTHLY_11000, 0, 0, 0, 0));
        // 1일 평균임금 2,299,000 × 12 ÷ 365 = 75,583.56 → 75,583. 75,583 × 10일 × 70% = 529,081
        Item it = item(calc(more(r, null, null, new CalculationRequest.Shutdown(10, 0L))), "SHUTDOWN_ALLOWANCE", null);
        assertThat(it.amount()).isEqualTo(529_081L);
        assertThat(it.estimated()).isTrue();
        // 쉬는 동안 100,000원 받음: (755,830 − 100,000) × 70% = 459,081
        assertThat(item(calc(more(r, null, null, new CalculationRequest.Shutdown(10, 100_000L))), "SHUTDOWN_ALLOWANCE", null).amount()).isEqualTo(459_081L);
        CalculationRequest small = monthly("UNDER_FIVE", "2025-01-01", null, MONTHLY_11000, 0, null, month("2026-09", MONTHLY_11000, 0, 0, 0, 0));
        CalculationResult under = calc(more(small, null, null, new CalculationRequest.Shutdown(10, 0L)));
        assertThat(under.items()).noneMatch(i -> i.code().equals("SHUTDOWN_ALLOWANCE"));
        assertThat(under.notApplied()).extracting("code").contains("SHUTDOWN_ALLOWANCE");
    }

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
                schedule, wage, List.of(months), lastThree, null);
    }

    static CalculationRequest withDays(CalculationRequest r, WorkDay... days) {
        return new CalculationRequest(r.workplace(), r.employment(), r.schedule(), r.wage(), r.months(), r.lastThreeMonthsWages(), List.of(days));
    }

    static WorkDay day(String date, String start, String end, int breakMinutes, boolean holiday) {
        return new WorkDay(LocalDate.parse(date), start, end, breakMinutes, holiday);
    }

    static Month month(String month, long paid, double overtime, double night, double holiday8, double holidayOver8) {
        return new Month(month, paid, BigDecimal.valueOf(overtime), BigDecimal.valueOf(night),
                BigDecimal.valueOf(holiday8), BigDecimal.valueOf(holidayOver8), 0);
    }
}
