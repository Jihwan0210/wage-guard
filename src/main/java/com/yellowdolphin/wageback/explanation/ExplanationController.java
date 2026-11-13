package com.yellowdolphin.wageback.explanation;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.yellowdolphin.wageback.calculation.CalculationController;
import com.yellowdolphin.wageback.common.Samples;

@RestController
@RequestMapping(value = "/api/v1/calculations/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
public class ExplanationController {

    // 임시: LLM 연결 전까지 예시 해설을 돌려준다
    @PostMapping("/explanation")
    public String explain(@PathVariable String id) {
        CalculationController.find(id);
        return Samples.read("explanation");
    }
}
