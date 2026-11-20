package com.yellowdolphin.wageback.calculation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

class ContractCheckTest {

    static final List<String> ALL = ContractCheck.REQUIRED;

    static ContractCheck.Request req(String type, Long wage, Integer weekly, String size, List<String> written) {
        return new ContractCheck.Request(2026, type, wage, weekly == null ? null : BigDecimal.valueOf(weekly), BigDecimal.valueOf(8), size, written);
    }

    static List<String> codes(ContractCheck.Result r) {
        return r.findings().stream().map(f -> f.code() + ":" + f.level()).toList();
    }

    @Test
    void 다_적혀_있고_월급_2156880원_주40시간이면_문제_없음() {
        // 2,156,880 ÷ 209 = 10,320원 = 2026년 최저임금 → 미달 아님
        ContractCheck.Result r = ContractCheck.check(req("MONTHLY", 2156880L, 40, "FIVE_OR_MORE", ALL));
        assertThat(r.findings()).isEmpty();
        assertThat(r.hourlyWage()).isEqualTo("10320");
        assertThat(r.hourlyFormula()).isEqualTo("2,156,880원 ÷ 209시간");
        assertThat(r.minimumWage()).isEqualTo(10320L);
    }

    @Test
    void 월급_2000000원이면_시급_9569원으로_최저임금_미달() {
        // 2,000,000 ÷ 209 = 9,569.37
        ContractCheck.Result r = ContractCheck.check(req("MONTHLY", 2000000L, 40, "FIVE_OR_MORE", ALL));
        assertThat(codes(r)).containsExactly("MIN_WAGE:PROBLEM");
        assertThat(r.hourlyWage()).isEqualTo("9569.37");
    }

    @Test
    void 빠진_기재_사항은_노랑으로_하나씩() {
        ContractCheck.Result r = ContractCheck.check(req("HOURLY", 10320L, 40, "FIVE_OR_MORE", List.of("WAGE", "HOURS", "JOB")));
        assertThat(codes(r)).containsExactly("HOLIDAY:MISSING", "ANNUAL_LEAVE:MISSING", "PLACE:MISSING");
        assertThat(r.ok()).containsExactly("WAGE", "HOURS", "JOB");
        assertThat(r.findings().get(0).basis().article()).isEqualTo("제17조");
    }

    @Test
    void 주_53시간_약정은_문제_5인_미만은_비교_안_함() {
        assertThat(codes(ContractCheck.check(req("HOURLY", 11000L, 53, "FIVE_OR_MORE", ALL)))).containsExactly("OVER_52:PROBLEM");
        assertThat(codes(ContractCheck.check(req("HOURLY", 11000L, 52, "FIVE_OR_MORE", ALL)))).isEmpty();
        ContractCheck.Result under = ContractCheck.check(req("HOURLY", 11000L, 60, "UNDER_FIVE", ALL));
        assertThat(under.findings()).isEmpty();
        assertThat(under.notChecked()).anyMatch(s -> s.contains("5인 미만"));
    }

    @Test
    void 연차가_빠져도_5인_미만이면_빠짐으로_보지_않음() {
        List<String> noLeave = List.of("WAGE", "HOURS", "HOLIDAY", "PLACE", "JOB");
        assertThat(codes(ContractCheck.check(req("HOURLY", 10320L, 40, "UNDER_FIVE", noLeave)))).isEmpty();
        assertThat(codes(ContractCheck.check(req("HOURLY", 10320L, 40, "FIVE_OR_MORE", noLeave)))).containsExactly("ANNUAL_LEAVE:MISSING");
    }

    @Test
    void 일급_80000원_8시간이면_시급_10000원으로_미달() {
        ContractCheck.Result r = ContractCheck.check(req("DAILY", 80000L, 40, "FIVE_OR_MORE", ALL));
        assertThat(r.hourlyWage()).isEqualTo("10000");
        assertThat(codes(r)).containsExactly("MIN_WAGE:PROBLEM");
    }

    @Test
    void 임금이_없으면_최저임금은_비교하지_않고_잘못된_값은_400() {
        ContractCheck.Result r = ContractCheck.check(req(null, null, null, null, null));
        assertThat(r.findings()).hasSize(6);
        assertThat(r.notChecked()).hasSize(2);
        assertThatThrownBy(() -> ContractCheck.check(req("WEEKLY", 1L, 40, null, ALL))).isInstanceOf(InvalidCalculationException.class);
        assertThatThrownBy(() -> ContractCheck.check(req("HOURLY", 10320L, 40, null, List.of("ETC")))).isInstanceOf(InvalidCalculationException.class);
    }
}
