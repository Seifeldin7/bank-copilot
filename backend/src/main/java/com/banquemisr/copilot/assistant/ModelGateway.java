package com.banquemisr.copilot.assistant;

import com.banquemisr.copilot.common.CopilotProperties;
import java.util.List;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ModelGateway {

    public record ModelReply(String text, String provider, String model, int promptTokens, int completionTokens,
                             double costUsd, long latencyMs, String prompt) {
    }

    private final ChatModel chatModel;
    private final CopilotProperties.Llm pricing;
    private final String provider;

    public ModelGateway(ChatModel chatModel, CopilotProperties properties, @Value("${spring.ai.model.chat}") String provider) {
        this.chatModel = chatModel;
        this.pricing = properties.llm();
        this.provider = provider;
    }

    public ModelReply call(String systemPrompt, String userPrompt) {
        long start = System.nanoTime();
        ChatResponse response = chatModel.call(new Prompt(List.of(new SystemMessage(systemPrompt), new UserMessage(userPrompt))));
        long latencyMs = (System.nanoTime() - start) / 1_000_000;

        String text = response.getResult() == null ? null : response.getResult().getOutput().getText();
        String reply = text == null ? "" : text;
        ChatResponseMetadata metadata = response.getMetadata();
        Usage usage = metadata.getUsage();
        int promptTokens = reportedOrEstimated(usage == null ? null : usage.getPromptTokens(), systemPrompt + userPrompt);
        int completionTokens = reportedOrEstimated(usage == null ? null : usage.getCompletionTokens(), reply);
        String model = metadata.getModel() == null || metadata.getModel().isBlank() ? "unknown" : metadata.getModel();
        return new ModelReply(reply, provider, model, promptTokens, completionTokens,
                cost(pricing, promptTokens, completionTokens), latencyMs, systemPrompt + "\n\n" + userPrompt);
    }

    static double cost(CopilotProperties.Llm pricing, int promptTokens, int completionTokens) {
        return (promptTokens * pricing.inputUsdPerMillionTokens() + completionTokens * pricing.outputUsdPerMillionTokens())
                / 1_000_000;
    }

    private static int reportedOrEstimated(Integer reported, String text) {
        return reported != null && reported > 0 ? reported : estimateTokens(text);
    }

    static int estimateTokens(String text) {
        return Math.max(1, text.length() / 4);
    }
}
