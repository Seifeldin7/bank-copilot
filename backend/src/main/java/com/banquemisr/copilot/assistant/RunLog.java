package com.banquemisr.copilot.assistant;

import com.banquemisr.copilot.assistant.CopilotDtos.AgentName;
import com.banquemisr.copilot.assistant.CopilotDtos.AgentStep;
import com.banquemisr.copilot.assistant.CopilotDtos.StepStatus;
import com.banquemisr.copilot.assistant.ModelGateway.ModelReply;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class RunLog {

    public record Call(int step, AgentName agent, ModelReply reply) {
    }

    private final int firstStep;
    private final List<AgentStep> steps = new ArrayList<>();
    private final List<Call> calls = new ArrayList<>();

    public RunLog() {
        this(1);
    }

    public RunLog(int firstStep) {
        this.firstStep = firstStep;
    }

    public void succeeded(AgentName agent, String summary, int iterations, Instant started, List<ModelReply> replies) {
        int step = nextStep();
        replies.forEach(reply -> calls.add(new Call(step, agent, reply)));
        steps.add(new AgentStep(step, agent, StepStatus.SUCCEEDED, summary, iterations, millisSince(started),
                replies.stream().mapToInt(ModelReply::promptTokens).sum(),
                replies.stream().mapToInt(ModelReply::completionTokens).sum(),
                replies.stream().mapToDouble(ModelReply::costUsd).sum(),
                started));
    }

    public void failed(AgentName agent, StepStatus status, String summary, Instant started) {
        steps.add(new AgentStep(nextStep(), agent, status, summary, 1, millisSince(started), 0, 0, 0, started));
    }

    public void skipped(AgentName agent, String summary) {
        steps.add(new AgentStep(nextStep(), agent, StepStatus.SKIPPED, summary, 0, 0, 0, 0, 0, Instant.now()));
    }

    public boolean ran(AgentName agent) {
        return steps.stream().anyMatch(step -> step.agent() == agent);
    }

    public List<AgentStep> steps() {
        return List.copyOf(steps);
    }

    public List<Call> calls() {
        return List.copyOf(calls);
    }

    private int nextStep() {
        return firstStep + steps.size();
    }

    private static long millisSince(Instant started) {
        return Instant.now().toEpochMilli() - started.toEpochMilli();
    }
}
