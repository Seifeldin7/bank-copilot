package com.banquemisr.copilot.assistant;

import com.banquemisr.copilot.servicerequest.RequestCategory;
import com.banquemisr.copilot.servicerequest.RequestDtos.RequestView;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class CopilotDtos {

    private CopilotDtos() {
    }

    public enum Mode { ORCHESTRATED, FALLBACK }

    public enum AgentName { ORCHESTRATOR, KNOWLEDGE_RETRIEVAL, POLICY_VALIDATION, ANSWER_GENERATION, REQUEST_PREPARATION, FALLBACK_ANSWER }

    public enum StepStatus { SUCCEEDED, FAILED, TIMED_OUT, SKIPPED }

    public record AskRequest(
            @NotBlank(message = "Ask a question between 3 and 1000 characters.")
            @Size(min = 3, max = 1000, message = "Ask a question between 3 and 1000 characters.")
            String question) {
    }

    public record PrepareRequest(@NotBlank(message = "The run ID is required.") @Size(max = 64) String runId) {
    }

    public record PreparedDraftResponse(RequestView request, List<String> missingFields) {
    }

    public record Citation(int index, UUID chunkId, UUID documentId, String documentTitle, int version,
                           Integer page, String section, String snippet, double score) {
    }

    public record ValidationVerdict(boolean sufficient, List<String> missingInformation,
                                    List<String> unsupportedAssumptions, double confidence) {
    }

    public record AgentStep(int step, AgentName agent, StepStatus status, String summary, int iterations, long durationMs,
                            int promptTokens, int completionTokens, double estimatedCostUsd, Instant startedAt) {
    }

    public record UsageTotals(int promptTokens, int completionTokens, int totalTokens, double estimatedCostUsd) {

        public static UsageTotals of(List<AgentStep> steps) {
            int prompt = steps.stream().mapToInt(AgentStep::promptTokens).sum();
            int completion = steps.stream().mapToInt(AgentStep::completionTokens).sum();
            double cost = steps.stream().mapToDouble(AgentStep::estimatedCostUsd).sum();
            return new UsageTotals(prompt, completion, prompt + completion, cost);
        }
    }

    public record AskResponse(String runId, String question, String answer, boolean refused, String refusalReason,
                              Mode mode, List<Citation> citations, ValidationVerdict verdict, RequestCategory suggestedCategory,
                              List<AgentStep> steps, UsageTotals usage, long latencyMs, Instant createdAt) {
    }
}
