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
        List<WorkDay> workDays,
        Dismissal dismissal,
        AnnualLeave annualLeave,
        Probation probation,
        DelayInterest delayInterest,
        Shutdown shutdown) {

    // v2 이전 필드만 쓰는 호출용
    public CalculationRequest(Workplace workplace, Employment employment, Schedule schedule, Wage wage, List<Month> months,
            Long lastThreeMonthsWages, List<WorkDay> workDays) {
        this(workplace, employment, schedule, wage, months, lastThreeMonthsWages, workDays, null, null, null, null, null);
    }

    // 6단계 필드까지 쓰는 호출용
    public CalculationRequest(Workplace workplace, Employment employment, Schedule schedule, Wage wage, List<Month> months,
            Long lastThreeMonthsWages, List<WorkDay> workDays, Dismissal dismissal, AnnualLeave annualLeave, Probation probation) {
        this(workplace, employment, schedule, wage, months, lastThreeMonthsWages, workDays, dismissal, annualLeave, probation, null, null);
    }

    // v2: 퇴직 후 14일이 지나도 못 받은 돈의 지연이자를 어느 날까지 셀까 (null 이면 오늘)
    public record DelayInterest(LocalDate baseDate) {
    }

    // v2: 회사 사정으로 쉰 날 수 + 그동안 받은 돈
    public record Shutdown(Integer days, Long paidAmount) {
    }

    // v2: 해고됨 + 해고를 며칠 전에 알렸나 (안 알렸으면 0)
    public record Dismissal(Boolean dismissed, Integer noticeDaysBefore) {
    }

    // v2: 그만두기 전 마지막 1년(1년 미만이면 전체) 동안 쓴 연차 일수
    public record AnnualLeave(Integer usedDays) {
    }

    // v2: 수습 중인지 + 1년 이상 계약인지 + 단순노무 일인지
    public record Probation(Boolean onProbation, Boolean contractOneYearOrMore, Boolean simpleLabor) {
    }

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
