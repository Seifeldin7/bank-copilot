package com.banquemisr.copilot.assistant.run;

import com.banquemisr.copilot.assistant.CopilotDtos.AgentName;
import com.banquemisr.copilot.assistant.CopilotDtos.AgentStep;
import com.banquemisr.copilot.assistant.CopilotDtos.Mode;
import com.banquemisr.copilot.assistant.CopilotDtos.UsageTotals;
import com.banquemisr.copilot.audit.AuditEventView;
import com.banquemisr.copilot.identity.UserDtos.UserRef;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class RunDtos {

    private RunDtos() {
    }

    public record RunSummary(String runId, UserRef user, String question, Mode mode, boolean refused, long latencyMs,
                             int totalTokens, double estimatedCostUsd, Instant createdAt) {

        public static RunSummary of(CopilotRun run) {
            return new RunSummary(run.getRunId(), UserRef.of(run.getUser()), run.getQuestion(), run.getMode(),
                    run.isRefused(), run.getLatencyMs(), run.getPromptTokens() + run.getCompletionTokens(),
                    run.getCostUsd(), run.getCreatedAt());
        }
    }

    public record ModelCall(String id, int step, AgentName agent, String provider, String model, int promptTokens,
                            int completionTokens, double estimatedCostUsd, long latencyMs, Instant at) {

        public static ModelCall of(AiUsage usage) {
            return new ModelCall("mc-" + usage.getId(), usage.getStepNo(), usage.getAgent(), usage.getProvider(),
                    usage.getModel(), usage.getPromptTokens(), usage.getCompletionTokens(), usage.getCostUsd(),
                    usage.getLatencyMs(), usage.getAt());
        }
    }

    public record RunTrace(RunSummary run, List<AgentStep> steps, List<ModelCall> modelCalls, List<AuditEventView> events) {
    }

    public record UsageByDay(LocalDate date, long runs, int promptTokens, int completionTokens, double estimatedCostUsd) {
    }

    public record UsageByModel(String provider, String model, long calls, int totalTokens, double estimatedCostUsd) {
    }

    public record UsageSummary(LocalDate from, LocalDate to, long runs, UsageTotals totals, List<UsageByDay> byDay,
                               List<UsageByModel> byModel) {
    }
}
