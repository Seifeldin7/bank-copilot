package com.banquemisr.copilot.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.banquemisr.copilot.assistant.ModelGateway.ModelReply;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

class ModelGatewayTest {

    private final ChatModel chatModel = mock(ChatModel.class);
    private final ModelGateway gateway = new ModelGateway(chatModel, AssistantFixtures.properties(), "openai");

    @Test
    void reportsTheProvidersTokenCountsAndEstimatesTheCost() {
        ChatResponseMetadata metadata = ChatResponseMetadata.builder()
                .model("gpt-test")
                .usage(new DefaultUsage(1_000_000, 500_000))
                .build();
        when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("{}"))), metadata));

        ModelReply reply = gateway.call("system", "user");

        assertThat(reply.provider()).isEqualTo("openai");
        assertThat(reply.model()).isEqualTo("gpt-test");
        assertThat(reply.promptTokens()).isEqualTo(1_000_000);
        assertThat(reply.costUsd()).isCloseTo(0.45, within(1e-9));
    }

    @Test
    void estimatesTokensWhenTheProviderReportsNone() {
        when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("12345678")))));

        ModelReply reply = gateway.call("x".repeat(40), "y".repeat(40));

        assertThat(reply.model()).isEqualTo("unknown");
        assertThat(reply.promptTokens()).isEqualTo(20);
        assertThat(reply.completionTokens()).isEqualTo(2);
    }
}
