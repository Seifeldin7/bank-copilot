package com.banquemisr.copilot.audit;

import org.slf4j.MDC;

public final class RunContext {

    public static final String MDC_KEY = "runId";

    private RunContext() {
    }

    public static String currentRunId() {
        return MDC.get(MDC_KEY);
    }
}
