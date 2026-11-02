package com.banquemisr.copilot.assistant;

import com.banquemisr.copilot.assistant.ModelGateway.ModelReply;
import com.banquemisr.copilot.common.CopilotProperties;
import com.banquemisr.copilot.retrieval.Evidence;
import java.time.Duration;
import java.util.UUID;

public final class AssistantFixtures {

    public static final String DRILL_PHRASE = "simulate an agent failure";

    private AssistantFixtures() {
    }

    public static CopilotProperties properties() {
        Duration timeout = Duration.ofMillis(300);
        return new CopilotProperties(null, null, null, null, null,
                new CopilotProperties.Retrieval(20, 5, 60, 0.35),
                new CopilotProperties.Llm(0.15, 0.6, false),
                null,
                new CopilotProperties.Agents(2, 2, timeout, timeout, timeout, timeout, DRILL_PHRASE));
    }

    public static Evidence evidence(double similarity) {
        return evidence("Debit Card Replacement Procedure", "Cards",
                "A replacement card costs EGP 50 and is ready in 5 working days.", similarity);
    }

    public static Evidence evidence(String title, String category, String content, double similarity) {
        return new Evidence(UUID.randomUUID(), UUID.randomUUID(), title, category, 1, 2, null, content, similarity, 1, 2, 0.032);
    }

    public static ModelReply reply(String text) {
        return new ModelReply(text, "stub", "test-model", 400, 40, 0.0001, 5, "prompt");
    }
}
