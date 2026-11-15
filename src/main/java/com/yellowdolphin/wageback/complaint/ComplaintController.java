package com.yellowdolphin.wageback.complaint;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.yellowdolphin.wageback.calculation.CalculationController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping(value = "/api/v1/calculations/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
public class ComplaintController {

    private final ComplaintService service;

    public ComplaintController(ComplaintService service) {
        this.service = service;
    }

    // 요청 본문(인적사항)은 로그에 남기지 않는다
    @PostMapping("/complaint-draft")
    public ComplaintService.Draft draft(@PathVariable String id, @Valid @RequestBody Request request) {
        return service.draft(CalculationController.find(id), request);
    }

    public record Request(@Valid Person complainant, @Valid Company respondent, @Size(max = 50) String job, Boolean employed,
            @Size(max = 2000, message = "추가 내용은 2000자까지 쓸 수 있어요.") String extraFacts,
            @Pattern(regexp = "ko|en|vi", message = "언어는 ko, en, vi 중 하나예요.") String lang) {
    }

    public record Person(@Size(max = 50) String name, @Size(max = 30) String phone, @Size(max = 200) String address) {
    }

    public record Company(@Size(max = 100) String companyName, @Size(max = 50) String ownerName, @Size(max = 200) String address,
            @Size(max = 30) String phone) {
    }
}
