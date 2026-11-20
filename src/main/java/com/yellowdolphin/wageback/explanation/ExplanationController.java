package com.yellowdolphin.wageback.explanation;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.yellowdolphin.wageback.calculation.CalculationController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;

@RestController
@RequestMapping(value = "/api/v1/calculations/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
public class ExplanationController {

    private final ExplanationService service;

    public ExplanationController(ExplanationService service) {
        this.service = service;
    }

    @PostMapping("/explanation")
    public ExplanationService.Explanation explain(@PathVariable String id, @Valid @RequestBody(required = false) Request request) {
        String result = CalculationController.find(id);
        return service.explain(result, request == null || request.lang() == null ? "ko" : request.lang());
    }

    public record Request(@Pattern(regexp = "ko|en|vi", message = "언어는 ko, en, vi 중 하나예요.") String lang) {
    }
}
