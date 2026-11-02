package com.banquemisr.copilot.assistant.agent;

import com.banquemisr.copilot.assistant.ModelGateway.ModelReply;
import java.util.List;

public record AgentResult<T>(T output, String summary, int iterations, List<ModelReply> modelCalls) {
}
