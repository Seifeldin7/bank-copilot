package com.banquemisr.copilot.assistant.run;

import com.banquemisr.copilot.assistant.CopilotDtos.AgentName;
import com.banquemisr.copilot.assistant.RunLog;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "ai_usage")
public class AiUsage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String runId;
    private int stepNo;

    @Enumerated(EnumType.STRING)
    private AgentName agent;

    private String provider;
    private String model;
    private int promptTokens;
    private int completionTokens;
    private double costUsd;
    private long latencyMs;
    private String prompt;
    private String response;
    private Instant at;

    protected AiUsage() {
    }

    public AiUsage(String runId, RunLog.Call call, boolean keepText) {
        this.runId = runId;
        this.stepNo = call.step();
        this.agent = call.agent();
        this.provider = call.reply().provider();
        this.model = call.reply().model();
        this.promptTokens = call.reply().promptTokens();
        this.completionTokens = call.reply().completionTokens();
        this.costUsd = call.reply().costUsd();
        this.latencyMs = call.reply().latencyMs();
        this.prompt = keepText ? call.reply().prompt() : null;
        this.response = keepText ? call.reply().text() : null;
        this.at = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getRunId() {
        return runId;
    }

    public int getStepNo() {
        return stepNo;
    }

    public AgentName getAgent() {
        return agent;
    }

    public String getProvider() {
        return provider;
    }

    public String getModel() {
        return model;
    }

    public int getPromptTokens() {
        return promptTokens;
    }

    public int getCompletionTokens() {
        return completionTokens;
    }

    public double getCostUsd() {
        return costUsd;
    }

    public long getLatencyMs() {
        return latencyMs;
    }

    public Instant getAt() {
        return at;
    }
}
