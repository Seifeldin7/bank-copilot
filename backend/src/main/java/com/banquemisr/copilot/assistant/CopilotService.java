package com.banquemisr.copilot.assistant;

import com.banquemisr.copilot.assistant.CopilotDtos.Agent;
import com.banquemisr.copilot.assistant.CopilotDtos.AgentStep;
import com.banquemisr.copilot.assistant.CopilotDtos.AskResponse;
import com.banquemisr.copilot.assistant.CopilotDtos.Citation;
import com.banquemisr.copilot.assistant.CopilotDtos.Mode;
import com.banquemisr.copilot.assistant.CopilotDtos.StepStatus;
import com.banquemisr.copilot.assistant.CopilotDtos.UsageTotals;
import com.banquemisr.copilot.assistant.GroundedAnswerService.AnswerResult;
import com.banquemisr.copilot.audit.AuditService;
import com.banquemisr.copilot.audit.RunContext;
import com.banquemisr.copilot.identity.AppUser;
import com.banquemisr.copilot.retrieval.Evidence;
import com.banquemisr.copilot.retrieval.HybridSearch;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class CopilotService {

    static final String MODEL_UNAVAILABLE = "The copilot could not reach the language model. Please try again shortly.";

    private static final Logger log = LoggerFactory.getLogger(CopilotService.class);

    private final PreCheck preCheck;
    private final HybridSearch hybridSearch;
    private final GroundedAnswerService groundedAnswers;
    private final AuditService audit;

    public CopilotService(PreCheck preCheck, HybridSearch hybridSearch, GroundedAnswerService groundedAnswers,
                          AuditService audit) {
        this.preCheck = preCheck;
        this.hybridSearch = hybridSearch;
        this.groundedAnswers = groundedAnswers;
        this.audit = audit;
    }

    public AskResponse ask(AppUser user, String question) {
        Instant createdAt = Instant.now();
        String text = question.trim();
        List<AgentStep> steps = new ArrayList<>();

        Optional<String> refusal = preCheck.beforeSearch(text);
        List<Evidence> evidence = List.of();
        if (refusal.isPresent()) {
            steps.add(skipped(steps, Agent.KNOWLEDGE_RETRIEVAL, "Skipped: the question did not pass the pre-check"));
        } else {
            Instant started = Instant.now();
            evidence = hybridSearch.search(text);
            steps.add(step(steps, Agent.KNOWLEDGE_RETRIEVAL, StepStatus.SUCCEEDED, retrievalSummary(evidence), started, null));
            refusal = preCheck.afterSearch(evidence);
        }

        String answer = null;
        List<Citation> citations = List.of();
        if (refusal.isPresent()) {
            steps.add(skipped(steps, Agent.ANSWER_GENERATION, "Skipped: not enough relevant evidence, refusal returned"));
        } else {
            Instant started = Instant.now();
            try {
                AnswerResult result = groundedAnswers.answer(text, evidence);
                answer = result.answer();
                citations = result.citations();
                refusal = Optional.ofNullable(result.refusalReason());
                String summary = result.refused()
                        ? "Model refused or its citations failed the guard: " + result.refusalReason()
                        : "Grounded answer with " + citations.size() + " verified citation" + (citations.size() == 1 ? "" : "s");
                steps.add(step(steps, Agent.ANSWER_GENERATION, StepStatus.SUCCEEDED, summary, started, result.reply()));
            } catch (RuntimeException ex) {
                log.warn("Answer generation failed", ex);
                refusal = Optional.of(MODEL_UNAVAILABLE);
                steps.add(step(steps, Agent.ANSWER_GENERATION, StepStatus.FAILED, "Model call failed: " + ex.getClass().getSimpleName(), started, null));
            }
        }

        boolean refused = refusal.isPresent();
        String runId = RunContext.currentRunId() != null ? RunContext.currentRunId() : UUID.randomUUID().toString();
        long latencyMs = Instant.now().toEpochMilli() - createdAt.toEpochMilli();
        audit.record(refused ? "COPILOT_REFUSED" : "COPILOT_ANSWERED", user, "CopilotRun", runId, text);
        log.atInfo()
                .addKeyValue("refused", refused)
                .addKeyValue("citations", citations.size())
                .addKeyValue("latencyMs", latencyMs)
                .log("Copilot run finished");

        return new AskResponse(runId, text, refused ? null : answer, refused, refusal.orElse(null), Mode.ORCHESTRATED,
                refused ? List.of() : citations, null, null, steps, UsageTotals.of(steps), latencyMs, createdAt);
    }

    private static String retrievalSummary(List<Evidence> evidence) {
        if (evidence.isEmpty()) {
            return "Hybrid search found no passages";
        }
        double best = evidence.stream().mapToDouble(Evidence::similarity).max().orElse(0);
        long both = evidence.stream().filter(e -> e.semanticRank() != null && e.keywordRank() != null).count();
        return "Hybrid search (semantic + keyword, fused by RRF) returned %d passages, %d found by both; best similarity %.2f"
                .formatted(evidence.size(), both, best);
    }

    private static AgentStep step(List<AgentStep> steps, Agent agent, StepStatus status, String summary, Instant started,
                                  ModelGateway.ModelReply reply) {
        long durationMs = Instant.now().toEpochMilli() - started.toEpochMilli();
        return new AgentStep(steps.size() + 1, agent, status, summary, 1, durationMs,
                reply == null ? 0 : reply.promptTokens(),
                reply == null ? 0 : reply.completionTokens(),
                reply == null ? 0 : reply.costUsd(),
                started);
    }

    private static AgentStep skipped(List<AgentStep> steps, Agent agent, String summary) {
        return new AgentStep(steps.size() + 1, agent, StepStatus.SKIPPED, summary, 0, 0, 0, 0, 0, Instant.now());
    }
}
