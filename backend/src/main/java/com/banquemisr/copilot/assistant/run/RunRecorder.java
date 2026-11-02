package com.banquemisr.copilot.assistant.run;

import com.banquemisr.copilot.assistant.CopilotDtos.AgentStep;
import com.banquemisr.copilot.assistant.CopilotDtos.AskResponse;
import com.banquemisr.copilot.assistant.RunLog;
import com.banquemisr.copilot.common.CopilotProperties;
import com.banquemisr.copilot.identity.AppUser;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RunRecorder {

    private final CopilotRunRepository runs;
    private final RunStepRepository steps;
    private final AiUsageRepository usage;
    private final boolean keepPromptText;

    public RunRecorder(CopilotRunRepository runs, RunStepRepository steps, AiUsageRepository usage, CopilotProperties properties) {
        this.runs = runs;
        this.steps = steps;
        this.usage = usage;
        this.keepPromptText = properties.llm().logPrompts();
    }

    @Transactional(readOnly = true)
    public boolean exists(String runId) {
        return runs.existsById(runId);
    }

    @Transactional(readOnly = true)
    public Optional<CopilotRun> find(String runId) {
        return runs.findWithUser(runId);
    }

    @Transactional(readOnly = true)
    public int stepCount(String runId) {
        return (int) steps.countByRunId(runId);
    }

    @Transactional
    public void saveRun(AppUser user, AskResponse response, RunLog log) {
        runs.save(new CopilotRun(user, response));
        saveSteps(response.runId(), log);
    }

    @Transactional
    public void appendSteps(String runId, RunLog log) {
        CopilotRun run = runs.findById(runId).orElseThrow();
        for (AgentStep step : log.steps()) {
            run.addUsage(step.promptTokens(), step.completionTokens(), step.estimatedCostUsd());
        }
        saveSteps(runId, log);
    }

    private void saveSteps(String runId, RunLog log) {
        log.steps().forEach(step -> steps.save(new RunStep(runId, step)));
        log.calls().forEach(call -> usage.save(new AiUsage(runId, call, keepPromptText)));
    }
}
