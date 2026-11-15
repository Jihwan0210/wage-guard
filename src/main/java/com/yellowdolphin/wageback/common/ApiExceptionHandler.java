package com.yellowdolphin.wageback.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.yellowdolphin.wageback.calculation.InvalidCalculationException;

// 모든 오류를 ProblemDetail + code · field 로 돌려준다 (API.md 오류 형식). 요청 본문은 로그에 남기지 않는다
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ProblemDetail handleApi(ApiException e) {
        return problem(e.getStatus(), e.getMessage(), e.getCode(), e.getField());
    }

    @ExceptionHandler(InvalidCalculationException.class)
    public ProblemDetail handleCalculation(InvalidCalculationException e) {
        return problem(HttpStatus.BAD_REQUEST, e.getMessage(), e.getCode(), e.getField());
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnknown(Exception e) {
        // 메시지에 입력값이 섞일 수 있어 종류만 남긴다
        log.error("처리 못 한 오류: {}", e.getClass().getName());
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "잠시 후 다시 시도해 주세요.", "INTERNAL_ERROR", null);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        FieldError error = ex.getBindingResult().getFieldError();
        ProblemDetail body = problem(HttpStatus.BAD_REQUEST, error == null ? "입력값을 확인해 주세요." : error.getDefaultMessage(),
                "INVALID_INPUT", error == null ? null : error.getField());
        return handleExceptionInternal(ex, body, headers, status, request);
    }

    // Spring 기본 오류(413 업로드 초과, 404 결과 없음 등)에도 code · field 를 붙인다
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        if (body == null && ex instanceof ErrorResponse er) {
            body = er.getBody();
        }
        if (body instanceof ProblemDetail pd && (pd.getProperties() == null || !pd.getProperties().containsKey("code"))) {
            pd.setProperty("code", codeFor(status.value(), ex));
            pd.setProperty("field", null);
        }
        return super.handleExceptionInternal(ex, body, headers, status, request);
    }

    static String codeFor(int status, Exception ex) {
        return switch (status) {
            case 400 -> "INVALID_INPUT";
            case 404 -> ex instanceof ResponseStatusException ? "CALCULATION_EXPIRED" : "NOT_FOUND";
            case 413 -> "FILE_TOO_LARGE";
            case 415 -> "UNSUPPORTED_FILE";
            default -> "ERROR";
        };
    }

    static ProblemDetail problem(HttpStatus status, String detail, String code, String field) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setProperty("code", code);
        pd.setProperty("field", field);
        return pd;
    }
}
