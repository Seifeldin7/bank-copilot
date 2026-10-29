package com.banquemisr.copilot.common;

import java.util.List;
import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final List<FieldError> errors;

    public ApiException(HttpStatus status, String detail, List<FieldError> errors) {
        super(detail);
        this.status = status;
        this.errors = errors;
    }

    public static ApiException badRequest(String detail, FieldError... errors) {
        return new ApiException(HttpStatus.BAD_REQUEST, detail, List.of(errors));
    }

    public static ApiException forbidden(String detail) {
        return new ApiException(HttpStatus.FORBIDDEN, detail, List.of());
    }

    public static ApiException notFound(String detail) {
        return new ApiException(HttpStatus.NOT_FOUND, detail, List.of());
    }

    public static ApiException conflict(String detail) {
        return new ApiException(HttpStatus.CONFLICT, detail, List.of());
    }

    public static ApiException unprocessable(String detail, List<FieldError> errors) {
        return new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, detail, errors);
    }

    public HttpStatus status() {
        return status;
    }

    public List<FieldError> errors() {
        return errors;
    }
}
