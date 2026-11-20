package com.yellowdolphin.wageback.calculation;

import java.util.List;

// POST /api/v1/calculations 응답 (API.md, samples/calculation.json 과 같은 형식)
public record CalculationResult(
        String id,
        String engineVersion,
        Integer minimumWageYear,
        Period period,
        Summary summary,
        Value hourlyOrdinaryWage,
        Value dailyAverageWage,
        List<Item> items,
        List<Notice> warnings,
        List<NotApplied> notApplied,
        List<WorkWeek> workWeeks) {

    public record Period(String start, String end) {
    }

    public record Summary(long totalDue, long totalPaid, long totalUnpaid) {
    }

    public record Value(String value, String formula) {
    }

    // status: CALCULATED / NO_DATA / INCLUDED_IN_WAGE. CALCULATED 가 아니면 amount 는 null
    public record Item(String code, String label, String month, String status, boolean estimated,
            Long amount, String formula, List<Basis> legalBasis) {
    }

    public record Basis(String law, String article, String summary, String url) {
    }

    public record Notice(String code, String message) {
    }

    public record NotApplied(String code, String reason) {
    }

    // v2: 하루 기록(workDays)을 주(월요일 시작)별로 나눈 시간. 하루 기록이 없으면 빈 목록
    public record WorkWeek(String month, String weekStart, int days, String workedHours, String overtimeHours,
            String nightHours, String holidayHoursWithin8, String holidayHoursOver8, String formula) {
    }
}
