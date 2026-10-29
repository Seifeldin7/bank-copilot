package com.banquemisr.copilot.common;

import com.banquemisr.copilot.audit.RunContext;
import java.net.URI;
import java.util.List;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

public final class Problems {

    private Problems() {
    }

    public static ProblemDetail of(HttpStatusCode status, String detail, String path, List<FieldError> errors) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setInstance(URI.create(path));
        problem.setProperty("runId", RunContext.currentRunId());
        if (!errors.isEmpty()) {
            problem.setProperty("errors", errors);
        }
        return problem;
    }
}
