package com.banquemisr.copilot.assistant.agent;

import com.banquemisr.copilot.assistant.CopilotDtos.AgentName;

public interface Agent<I, O> {

    AgentName name();

    AgentResult<O> run(I input);
}
