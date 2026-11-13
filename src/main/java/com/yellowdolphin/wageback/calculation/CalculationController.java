package com.yellowdolphin.wageback.calculation;

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

import com.yellowdolphin.wageback.common.Samples;

@RestController
@RequestMapping(value = "/api/v1/calculations", produces = MediaType.APPLICATION_JSON_VALUE)
public class CalculationController {

    // 임시: 룰 엔진 연결 전까지 입력과 상관없이 예시 결과를 돌려준다. 30분 만료도 아직 없음
    private static final Map<String, String> RESULTS = new ConcurrentHashMap<>();

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public String create(@RequestBody String request) {
        String id = "calc_" + UUID.randomUUID().toString().substring(0, 8);
        String result = Samples.read("calculation").replace("{{id}}", id);
        RESULTS.put(id, result);
        return result;
    }

    @GetMapping("/{id}")
    public String get(@PathVariable String id) {
        return find(id);
    }

    public static String find(String id) {
        String result = RESULTS.get(id);
        if (result == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "계산 결과가 없거나 30분이 지나 지워졌어요. 다시 계산해 주세요.");
        }
        return result;
    }
}
