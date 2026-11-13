package com.yellowdolphin.wageback.complaint;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.yellowdolphin.wageback.calculation.CalculationController;
import com.yellowdolphin.wageback.common.Samples;

@RestController
@RequestMapping(value = "/api/v1/calculations/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
public class ComplaintController {

    // 임시: 템플릿·LLM 연결 전까지 예시 진정서를 돌려준다. 요청 본문(인적사항)은 아직 쓰지 않음
    @PostMapping("/complaint-draft")
    public String draft(@PathVariable String id) {
        CalculationController.find(id);
        return Samples.read("complaint-draft");
    }
}
