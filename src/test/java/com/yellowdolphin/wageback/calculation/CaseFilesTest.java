package com.yellowdolphin.wageback.calculation;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import tools.jackson.databind.json.JsonMapper;

// src/test/resources/cases/*.json 사례 파일 하나가 시험 하나. 파일만 넣으면 시험이 늘어난다 (형식은 cases/README.md)
class CaseFilesTest {

    record Case(String name, String source, CalculationRequest request, Expected expected) {
    }

    record Expected(Long totalUnpaid, List<ExpectedItem> items) {
    }

    record ExpectedItem(String code, String month, Long amount) {
    }

    static final JsonMapper JSON = JsonMapper.builder().build();

    @TestFactory
    Stream<DynamicTest> 사례_파일() throws IOException {
        Resource[] files = new PathMatchingResourcePatternResolver().getResources("classpath:cases/*.json");
        assertThat(files).isNotEmpty();
        return Arrays.stream(files).map(f -> DynamicTest.dynamicTest(f.getFilename(), () -> check(f)));
    }

    static void check(Resource file) throws IOException {
        Case c = JSON.readValue(file.getInputStream(), Case.class);
        assertThat(c.source()).as("출처는 꼭 적는다").isNotBlank();
        CalculationResult r = RuleEngine.calculate(c.request(), "calc_case");
        for (ExpectedItem e : c.expected().items()) {
            Long actual = r.items().stream().filter(i -> i.code().equals(e.code()) && Objects.equals(i.month(), e.month()))
                    .findFirst().orElseThrow(() -> new AssertionError(e.code() + " " + e.month() + " 항목 없음")).amount();
            assertThat(actual).as(e.code() + " " + e.month()).isEqualTo(e.amount());
        }
        if (c.expected().totalUnpaid() != null) {
            assertThat(r.summary().totalUnpaid()).as("totalUnpaid").isEqualTo(c.expected().totalUnpaid());
        }
    }
}
