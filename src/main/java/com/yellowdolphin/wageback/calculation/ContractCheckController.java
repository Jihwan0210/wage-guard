package com.yellowdolphin.wageback.calculation;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

// v2 8단계: 근로계약서 점검. 저장하지 않고 바로 답한다
@RestController
public class ContractCheckController {

    @PostMapping(value = "/api/v1/contract-checks", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ContractCheck.Result check(@RequestBody ContractCheck.Request request) {
        return ContractCheck.check(request);
    }
}
