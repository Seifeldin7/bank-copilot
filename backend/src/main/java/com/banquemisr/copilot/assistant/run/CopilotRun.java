package com.banquemisr.copilot.assistant.run;

import com.banquemisr.copilot.assistant.CopilotDtos.AskResponse;
import com.banquemisr.copilot.assistant.CopilotDtos.Citation;
import com.banquemisr.copilot.assistant.CopilotDtos.Mode;
import com.banquemisr.copilot.assistant.CopilotDtos.ValidationVerdict;
import com.banquemisr.copilot.identity.AppUser;
import com.banquemisr.copilot.servicerequest.RequestCategory;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "copilot_run")
public class CopilotRun {

    @Id
    private String runId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private AppUser user;

    private String question;

    @Enumerated(EnumType.STRING)
    private Mode mode;

    private boolean refused;
    private String refusalReason;
    private String answer;

    @JdbcTypeCode(SqlTypes.JSON)
    private List<Citation> citations;

    @JdbcTypeCode(SqlTypes.JSON)
    private ValidationVerdict verdict;

    @Enumerated(EnumType.STRING)
    private RequestCategory suggestedCategory;

    private int promptTokens;
    private int completionTokens;
    private double costUsd;
    private long latencyMs;
    private Instant createdAt;

    protected CopilotRun() {
    }

    public CopilotRun(AppUser user, AskResponse response) {
        this.runId = response.runId();
        this.user = user;
        this.question = response.question();
        this.mode = response.mode();
        this.refused = response.refused();
        this.refusalReason = response.refusalReason();
        this.answer = response.answer();
        this.citations = response.citations();
        this.verdict = response.verdict();
        this.suggestedCategory = response.suggestedCategory();
        this.promptTokens = response.usage().promptTokens();
        this.completionTokens = response.usage().completionTokens();
        this.costUsd = response.usage().estimatedCostUsd();
        this.latencyMs = response.latencyMs();
        this.createdAt = response.createdAt();
    }

    public void addUsage(int promptTokens, int completionTokens, double costUsd) {
        this.promptTokens += promptTokens;
        this.completionTokens += completionTokens;
        this.costUsd += costUsd;
    }

    public boolean isOwnedBy(AppUser other) {
        return user.getId().equals(other.getId());
    }

    public String getRunId() {
        return runId;
    }

    public AppUser getUser() {
        return user;
    }

    public String getQuestion() {
        return question;
    }

    public Mode getMode() {
        return mode;
    }

    public boolean isRefused() {
        return refused;
    }

    public String getAnswer() {
        return answer;
    }

    public RequestCategory getSuggestedCategory() {
        return suggestedCategory;
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

    public Instant getCreatedAt() {
        return createdAt;
    }
}
