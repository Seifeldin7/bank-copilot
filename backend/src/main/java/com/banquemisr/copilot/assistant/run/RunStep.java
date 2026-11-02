package com.banquemisr.copilot.assistant.run;

import com.banquemisr.copilot.assistant.CopilotDtos.AgentName;
import com.banquemisr.copilot.assistant.CopilotDtos.AgentStep;
import com.banquemisr.copilot.assistant.CopilotDtos.StepStatus;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "agent_step")
public class RunStep {

    @Id
    private UUID id;

    private String runId;
    private int stepNo;

    @Enumerated(EnumType.STRING)
    private AgentName agent;

    @Enumerated(EnumType.STRING)
    private StepStatus status;

    private String summary;
    private int iterations;
    private long durationMs;
    private int promptTokens;
    private int completionTokens;
    private double costUsd;
    private Instant startedAt;

    protected RunStep() {
    }

    public RunStep(String runId, AgentStep step) {
        this.id = UUID.randomUUID();
        this.runId = runId;
        this.stepNo = step.step();
        this.agent = step.agent();
        this.status = step.status();
        this.summary = step.summary().length() > 500 ? step.summary().substring(0, 500) : step.summary();
        this.iterations = step.iterations();
        this.durationMs = step.durationMs();
        this.promptTokens = step.promptTokens();
        this.completionTokens = step.completionTokens();
        this.costUsd = step.estimatedCostUsd();
        this.startedAt = step.startedAt();
    }

    public AgentStep toView() {
        return new AgentStep(stepNo, agent, status, summary, iterations, durationMs, promptTokens, completionTokens,
                costUsd, startedAt);
    }
}
