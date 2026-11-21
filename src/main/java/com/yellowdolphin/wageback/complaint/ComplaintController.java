package com.yellowdolphin.wageback.complaint;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.yellowdolphin.wageback.calculation.CalculationController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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

    // 회사에 보내는 내용증명 초안. 금액은 계산 id 로만 정해지고, 계좌 · 인적사항은 템플릿에만 들어간다 (로그 · 저장 없음)
    @PostMapping("/demand-letter")
    public ComplaintService.Draft letter(@PathVariable String id, @Valid @RequestBody LetterRequest request) {
        return service.letter(CalculationController.find(id), request);
    }

    public record LetterRequest(@Valid Person sender, @Valid Company recipient, @Size(max = 50) String job, Boolean employed,
            @Size(max = 2000, message = "추가 내용은 2000자까지 쓸 수 있어요.") String extraFacts,
            @Size(max = 80, message = "계좌는 80자까지 쓸 수 있어요.") String account,
            @Min(value = 3, message = "지급 기한은 3~30일로 골라 주세요.") @Max(value = 30, message = "지급 기한은 3~30일로 골라 주세요.") Integer payDays,
            @Pattern(regexp = "ko|en|vi", message = "언어는 ko, en, vi 중 하나예요.") String lang) {
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
