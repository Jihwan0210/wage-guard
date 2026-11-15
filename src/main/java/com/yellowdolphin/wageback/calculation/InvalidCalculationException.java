package com.yellowdolphin.wageback.calculation;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

// 잘못된 입력. 서버 공통 처리에서 ProblemDetail(code · field)로 바꾼다
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidCalculationException extends RuntimeException {

    private final String code;
    private final String field;

    public InvalidCalculationException(String code, String field, String message) {
        super(message);
        this.code = code;
        this.field = field;
    }

    public String getCode() {
        return code;
    }

    public String getField() {
        return field;
    }
}
