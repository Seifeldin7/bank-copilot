package com.banquemisr.copilot.assistant;

import com.banquemisr.copilot.assistant.CopilotDtos.AgentName;
import com.banquemisr.copilot.assistant.CopilotDtos.AskResponse;
import com.banquemisr.copilot.assistant.CopilotDtos.Citation;
import com.banquemisr.copilot.assistant.CopilotDtos.Mode;
import com.banquemisr.copilot.assistant.CopilotDtos.StepStatus;
import com.banquemisr.copilot.assistant.CopilotDtos.UsageTotals;
import com.banquemisr.copilot.assistant.CopilotDtos.ValidationVerdict;
import com.banquemisr.copilot.assistant.GroundedAnswerService.AnswerResult;
import com.banquemisr.copilot.assistant.StepRunner.StepFailure;
import com.banquemisr.copilot.assistant.agent.AgentMessages.RetrievalRequest;
import com.banquemisr.copilot.assistant.agent.AgentMessages.RetrievalResult;
import com.banquemisr.copilot.assistant.agent.AgentMessages.ValidationRequest;
import com.banquemisr.copilot.assistant.agent.AgentResult;
import com.banquemisr.copilot.assistant.agent.CategorySchemaCatalog;
import com.banquemisr.copilot.assistant.agent.KnowledgeRetrievalAgent;
import com.banquemisr.copilot.assistant.agent.PolicyValidationAgent;
import com.banquemisr.copilot.assistant.run.RunRecorder;
import com.banquemisr.copilot.audit.AuditService;
import com.banquemisr.copilot.audit.RunContext;
import com.banquemisr.copilot.common.CopilotProperties;
import com.banquemisr.copilot.identity.AppUser;
import com.banquemisr.copilot.retrieval.Evidence;
import com.banquemisr.copilot.servicerequest.RequestCategory;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class CopilotOrchestrator {

    static final String MODEL_UNAVAILABLE = "The copilot could not reach the language model. Please try again shortly.";

    private static final Logger log = LoggerFactory.getLogger(CopilotOrchestrator.class);

    record Outcome(String answer, List<Citation> citations, String refusalReason, ValidationVerdict verdict,
                   RequestCategory suggestedCategory, Mode mode) {

        static Outcome refusal(String reason, ValidationVerdict verdict, Mode mode) {
            return new Outcome(null, List.of(), reason, verdict, null, mode);
        }
    }

    private final PreCheck preCheck;
    private final KnowledgeRetrievalAgent retrievalAgent;
    private final PolicyValidationAgent validationAgent;
    private final GroundedAnswerService groundedAnswers;
    private final FallbackAnswerService fallback;
    private final CategorySchemaCatalog catalog;
    private final StepRunner stepRunner;
    private final RunRecorder recorder;
    private final AuditService audit;
    private final CopilotProperties.Agents settings;

    public CopilotOrchestrator(PreCheck preCheck, KnowledgeRetrievalAgent retrievalAgent, PolicyValidationAgent validationAgent,
                               GroundedAnswerService groundedAnswers, FallbackAnswerService fallback,
                               CategorySchemaCatalog catalog, StepRunner stepRunner, RunRecorder recorder,
                               AuditService audit, CopilotProperties properties) {
        this.preCheck = preCheck;
        this.retrievalAgent = retrievalAgent;
        this.validationAgent = validationAgent;
        this.groundedAnswers = groundedAnswers;
        this.fallback = fallback;
        this.catalog = catalog;
        this.stepRunner = stepRunner;
        this.recorder = recorder;
        this.audit = audit;
        this.settings = properties.agents();
    }

    public AskResponse ask(AppUser user, String question) {
        Instant createdAt = Instant.now();
        String text = question.trim();
        RunLog runLog = new RunLog();

        Outcome outcome;
        Optional<String> screened = preCheck.beforeSearch(text);
        if (screened.isPresent()) {
            runLog.succeeded(AgentName.ORCHESTRATOR, "Question screening refused the question before retrieval", 1, createdAt, List.of());
            skipRemaining(runLog, "Skipped: the question did not pass screening");
            outcome = Outcome.refusal(screened.get(), screeningVerdict(screened.get()), Mode.ORCHESTRATED);
        } else {
            runLog.succeeded(AgentName.ORCHESTRATOR, "Planned retrieve → validate → answer", 1, createdAt, List.of());
            try {
                outcome = orchestrate(text, runLog);
            } catch (StepFailure failure) {
                skipRemaining(runLog, "Skipped after orchestration failure");
                outcome = fallBack(withoutDrillPhrase(text), runLog);
            }
        }

        AskResponse response = new AskResponse(runId(), text, outcome.answer(), outcome.refusalReason() != null,
                outcome.refusalReason(), outcome.mode(), outcome.citations(), outcome.verdict(), outcome.suggestedCategory(),
                runLog.steps(), UsageTotals.of(runLog.steps()), Instant.now().toEpochMilli() - createdAt.toEpochMilli(), createdAt);
        recorder.saveRun(user, response, runLog);
        audit.record(response.refused() ? "COPILOT_REFUSED" : "COPILOT_ANSWERED", user, "CopilotRun", response.runId(), text);
        log.atInfo()
                .addKeyValue("mode", response.mode())
                .addKeyValue("refused", response.refused())
                .addKeyValue("citations", response.citations().size())
                .addKeyValue("totalTokens", response.usage().totalTokens())
                .addKeyValue("latencyMs", response.latencyMs())
                .log("Copilot run finished");
        return response;
    }

    private Outcome orchestrate(String question, RunLog runLog) {
        RetrievalResult retrieved = step(runLog, AgentName.KNOWLEDGE_RETRIEVAL, settings.retrievalTimeout(), () -> {
            if (isDrill(question)) {
                throw new StepFailure(StepStatus.TIMED_OUT, "Failure drill: simulated retrieval timeout", null);
            }
            return retrievalAgent.run(new RetrievalRequest(question));
        });

        ValidationVerdict verdict = step(runLog, AgentName.POLICY_VALIDATION, settings.validationTimeout(),
                () -> validationAgent.run(new ValidationRequest(question, retrieved.evidence())));
        if (!verdict.sufficient()) {
            runLog.skipped(AgentName.ANSWER_GENERATION, "Skipped: validation found the evidence insufficient, refusal returned");
            return Outcome.refusal(PreCheck.NO_EVIDENCE, verdict, Mode.ORCHESTRATED);
        }

        AnswerResult answer = step(runLog, AgentName.ANSWER_GENERATION, settings.answerTimeout(),
                () -> answerStep(groundedAnswers.answer(question, retrieved.evidence())));
        return outcome(answer, retrieved.evidence(), verdict, Mode.ORCHESTRATED);
    }

    private Outcome fallBack(String question, RunLog runLog) {
        try {
            FallbackAnswerService.Result result = step(runLog, AgentName.FALLBACK_ANSWER, settings.answerTimeout(),
                    () -> fallback.answer(question));
            if (result.answer() == null) {
                return Outcome.refusal(result.refusalReason(), null, Mode.FALLBACK);
            }
            return outcome(result.answer(), result.evidence(), null, Mode.FALLBACK);
        } catch (StepFailure failure) {
            return Outcome.refusal(MODEL_UNAVAILABLE, null, Mode.FALLBACK);
        }
    }

    private <T> T step(RunLog runLog, AgentName agent, Duration timeout, Supplier<AgentResult<T>> work) {
        Instant started = Instant.now();
        try {
            AgentResult<T> result = stepRunner.run(work, timeout);
            runLog.succeeded(agent, result.summary(), result.iterations(), started, result.modelCalls());
            logStep(agent, StepStatus.SUCCEEDED, started);
            return result.output();
        } catch (StepFailure failure) {
            log.warn("Agent step {} ended with {}", agent, failure.status(), failure.getCause());
            runLog.failed(agent, failure.status(), failure.getMessage(), started);
            logStep(agent, failure.status(), started);
            throw failure;
        }
    }

    private Outcome outcome(AnswerResult answer, List<Evidence> evidence, ValidationVerdict verdict, Mode mode) {
        if (answer.refused()) {
            return Outcome.refusal(answer.refusalReason(), verdict, mode);
        }
        return new Outcome(answer.answer(), answer.citations(), null, verdict, suggestedCategory(answer, evidence), mode);
    }

    private RequestCategory suggestedCategory(AnswerResult answer, List<Evidence> evidence) {
        UUID firstCited = answer.citations().getFirst().chunkId();
        return evidence.stream()
                .filter(item -> item.chunkId().equals(firstCited))
                .findFirst()
                .flatMap(item -> catalog.forDocumentCategory(item.documentCategory()))
                .orElse(null);
    }

    private static AgentResult<AnswerResult> answerStep(AnswerResult answer) {
        String summary = answer.refused()
                ? "Model refused or its citations failed the guard: " + answer.refusalReason()
                : "Grounded answer with " + answer.citations().size() + " verified citation(s)";
        return new AgentResult<>(answer, summary, 1, List.of(answer.reply()));
    }

    private static void skipRemaining(RunLog runLog, String summary) {
        for (AgentName agent : List.of(AgentName.KNOWLEDGE_RETRIEVAL, AgentName.POLICY_VALIDATION, AgentName.ANSWER_GENERATION)) {
            if (!runLog.ran(agent)) {
                runLog.skipped(agent, summary);
            }
        }
    }

    private static ValidationVerdict screeningVerdict(String reason) {
        if (reason.equals(PreCheck.TOO_VAGUE)) {
            return new ValidationVerdict(false, List.of("Which product, service or procedure the question is about"), List.of(), 0.4);
        }
        return new ValidationVerdict(false, List.of(), List.of("The question tries to change how the copilot behaves"), 0.98);
    }

    private boolean isDrill(String question) {
        String phrase = settings.failureDrillPhrase();
        return phrase != null && !phrase.isBlank() && question.toLowerCase(Locale.ROOT).contains(phrase.toLowerCase(Locale.ROOT));
    }

    private String withoutDrillPhrase(String question) {
        if (!isDrill(question)) {
            return question;
        }
        return question.replaceAll("(?i)" + Pattern.quote(settings.failureDrillPhrase()), " ").trim();
    }

    private String runId() {
        String current = RunContext.currentRunId();
        return current == null || recorder.exists(current) ? UUID.randomUUID().toString() : current;
    }

    private static void logStep(AgentName agent, StepStatus status, Instant started) {
        log.atInfo()
                .addKeyValue("agent", agent)
                .addKeyValue("status", status)
                .addKeyValue("durationMs", Instant.now().toEpochMilli() - started.toEpochMilli())
                .log("Agent step finished");
    }
}
