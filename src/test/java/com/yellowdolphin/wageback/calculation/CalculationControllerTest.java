package com.yellowdolphin.wageback.calculation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class CalculationControllerTest {

    static final String BODY = """
            {
              "workplace": { "size": "FIVE_OR_MORE" },
              "employment": { "startDate": "2025-03-03", "endDate": "%s" },
              "schedule": { "weeklyContractHours": 40, "dailyContractHours": 8, "workDaysPerWeek": 5 },
              "wage": { "type": "MONTHLY", "baseAmount": 2156880,
                        "ordinaryAllowances": [ { "name": "직책수당", "amount": 100000 } ],
                        "otherAllowances": [ { "name": "출장비(실비)", "amount": 50000 } ], "annualBonus": 0 },
              "months": [ { "month": "2026-09", "paidAmount": 1800000, "overtimeHours": 10, "nightHours": 4,
                            "holidayHoursWithin8": 0, "holidayHoursOver8": 0, "absentWeeks": 0 } ],
              "lastThreeMonthsWages": null
            }
            """;

    final JsonMapper json = JsonMapper.builder().build();
    final MockMvc mvc = MockMvcBuilders.standaloneSetup(new CalculationController(json)).build();

    @Test
    void 계산하고_id로_다시_조회하고_30분_뒤엔_404() throws Exception {
        String body = mvc.perform(post("/api/v1/calculations").contentType(MediaType.APPLICATION_JSON).content(BODY.formatted("2026-10-31")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode result = json.readTree(body);
        String id = result.get("id").asString();
        assertThat(id).startsWith("calc_");
        assertThat(result.get("items").get(0).get("amount").asLong()).isEqualTo(456_880L);
        assertThat(result.get("items").get(3).get("amount").isNull()).isTrue(); // HOLIDAY NO_DATA

        String again = mvc.perform(get("/api/v1/calculations/" + id)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(again).isEqualTo(body);
        assertThat(CalculationController.find(id)).isEqualTo(body);

        assertThatThrownBy(() -> CalculationController.find(id, Instant.now().plus(CalculationController.KEEP).plusSeconds(1)))
                .isInstanceOf(ResponseStatusException.class);
        mvc.perform(get("/api/v1/calculations/" + id)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/calculations/calc_none")).andExpect(status().isNotFound());
    }

    @Test
    void 입사일이_퇴사일보다_늦으면_400() throws Exception {
        mvc.perform(post("/api/v1/calculations").contentType(MediaType.APPLICATION_JSON).content(BODY.formatted("2025-01-01")))
                .andExpect(status().isBadRequest());
    }
}
