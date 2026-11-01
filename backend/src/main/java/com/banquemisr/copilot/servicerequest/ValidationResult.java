package com.banquemisr.copilot.servicerequest;

import com.banquemisr.copilot.common.FieldError;
import java.util.List;

public record ValidationResult(boolean valid, List<Issue> issues, String checkedAt) {

    public enum Severity { ERROR, WARNING }

    public record Issue(String field, String message, Severity severity) {
    }

    public List<FieldError> errors() {
        return issues.stream()
                .filter(issue -> issue.severity() == Severity.ERROR)
                .map(issue -> new FieldError(issue.field(), issue.message()))
                .toList();
    }
}
