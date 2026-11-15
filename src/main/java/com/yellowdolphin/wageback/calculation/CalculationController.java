package com.yellowdolphin.wageback.calculation;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import tools.jackson.databind.json.JsonMapper;

@RestController
@RequestMapping(value = "/api/v1/calculations", produces = MediaType.APPLICATION_JSON_VALUE)
public class CalculationController {

    static final Duration KEEP = Duration.ofMinutes(30);

    // 결과 JSON 을 30분 보관한다. 만료된 것은 새 계산 때와 조회 때 지운다
    private static final Map<String, Stored> RESULTS = new ConcurrentHashMap<>();

    private record Stored(String json, Instant expiresAt) {
    }

    private final JsonMapper json;

    public CalculationController(JsonMapper json) {
        this.json = json;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public String create(@RequestBody CalculationRequest request) {
        String id = "calc_" + UUID.randomUUID().toString().substring(0, 8);
        String result = json.writeValueAsString(RuleEngine.calculate(request, id));
        Instant now = Instant.now();
        RESULTS.values().removeIf(s -> !now.isBefore(s.expiresAt()));
        RESULTS.put(id, new Stored(result, now.plus(KEEP)));
        return result;
    }

    @GetMapping("/{id}")
    public String get(@PathVariable String id) {
        return find(id);
    }

    public static String find(String id) {
        return find(id, Instant.now());
    }

    static String find(String id, Instant now) {
        Stored s = RESULTS.get(id);
        if (s == null || !now.isBefore(s.expiresAt())) {
            RESULTS.remove(id);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "계산 결과가 없거나 30분이 지나 지워졌어요. 다시 계산해 주세요.");
        }
        return s.json();
    }
}
