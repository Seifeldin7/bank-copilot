package com.banquemisr.copilot.common;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ProblemDetail handleApi(ApiException ex, HttpServletRequest request) {
        return Problems.of(ex.status(), ex.getMessage(), request.getRequestURI(), ex.errors());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<FieldError> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldError(error.getField(), error.getDefaultMessage()))
                .toList();
        return Problems.of(HttpStatus.BAD_REQUEST, "Validation failed.", request.getRequestURI(), errors);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestPartException.class})
    ProblemDetail handleUnreadable(Exception ex, HttpServletRequest request) {
        return Problems.of(HttpStatus.BAD_REQUEST, "The request body is missing or malformed.", request.getRequestURI(), List.of());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        return Problems.of(HttpStatus.BAD_REQUEST, "Invalid value for " + ex.getName() + ".", request.getRequestURI(),
                List.of(new FieldError(ex.getName(), "This value is not allowed.")));
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ProblemDetail handleOptimisticLock(ObjectOptimisticLockingFailureException ex, HttpServletRequest request) {
        return Problems.of(HttpStatus.CONFLICT, "This request was changed by someone else. Reload it and try again.",
                request.getRequestURI(), List.of());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ProblemDetail handleTooLarge(MaxUploadSizeExceededException ex, HttpServletRequest request) {
        return Problems.of(HttpStatus.BAD_REQUEST, "File too large.", request.getRequestURI(),
                List.of(new FieldError("file", "Files must be 10 MB or smaller.")));
    }

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return Problems.of(HttpStatus.FORBIDDEN, "You do not have permission to perform this action.", request.getRequestURI(), List.of());
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unexpected error on {}", request.getRequestURI(), ex);
        return Problems.of(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong. Quote the run ID when reporting this.", request.getRequestURI(), List.of());
    }
}
