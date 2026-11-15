package com.yellowdolphin.wageback.calculation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

// POST /api/v1/calculations 요청 (API.md). 금액은 원 단위 정수, 시간은 소수 허용
public record CalculationRequest(
        Workplace workplace,
        Employment employment,
        Schedule schedule,
        Wage wage,
        List<Month> months,
        Long lastThreeMonthsWages,
        List<WorkDay> workDays) {

    public record Workplace(String size) {
    }

    public record Employment(LocalDate startDate, LocalDate endDate) {
    }

    public record Schedule(BigDecimal weeklyContractHours, BigDecimal dailyContractHours, Integer workDaysPerWeek) {
    }

    public record Wage(String type, Long baseAmount, List<Allowance> ordinaryAllowances, List<Allowance> otherAllowances, Long annualBonus) {
    }

    public record Allowance(String name, Long amount) {
    }

    public record Month(
            String month,
            Long paidAmount,
            BigDecimal overtimeHours,
            BigDecimal nightHours,
            BigDecimal holidayHoursWithin8,
            BigDecimal holidayHoursOver8,
            Integer absentWeeks) {
    }

    // v2: 달력에서 넣은 하루 기록. start · end 는 "HH:mm", end 가 start 보다 이르면 다음 날로 본다
    public record WorkDay(LocalDate date, String start, String end, Integer breakMinutes, Boolean holiday) {
    }
}
