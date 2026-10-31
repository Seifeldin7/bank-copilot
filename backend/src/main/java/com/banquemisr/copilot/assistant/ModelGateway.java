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
import org.springframework.stereotype.Service;

@Service
public class ModelGateway {

    public record ModelReply(String text, String model, int promptTokens, int completionTokens, double costUsd,
                             long latencyMs) {
    }

    private final ChatModel chatModel;
    private final CopilotProperties.Llm pricing;

    public ModelGateway(ChatModel chatModel, CopilotProperties properties) {
        this.chatModel = chatModel;
        this.pricing = properties.llm();
    }

    public ModelReply call(String systemPrompt, String userPrompt) {
        long start = System.nanoTime();
        ChatResponse response = chatModel.call(new Prompt(List.of(new SystemMessage(systemPrompt), new UserMessage(userPrompt))));
        long latencyMs = (System.nanoTime() - start) / 1_000_000;

        String text = response.getResult() == null ? "" : response.getResult().getOutput().getText();
        ChatResponseMetadata metadata = response.getMetadata();
        Usage usage = metadata.getUsage();
        int promptTokens = usage != null && usage.getPromptTokens() != null
                ? usage.getPromptTokens() : estimateTokens(systemPrompt + userPrompt);
        int completionTokens = usage != null && usage.getCompletionTokens() != null
                ? usage.getCompletionTokens() : estimateTokens(text);
        String model = metadata.getModel() == null || metadata.getModel().isBlank() ? "unknown" : metadata.getModel();
        return new ModelReply(text == null ? "" : text, model, promptTokens, completionTokens,
                cost(promptTokens, completionTokens), latencyMs);
    }

    double cost(int promptTokens, int completionTokens) {
        return (promptTokens * pricing.inputUsdPerMillionTokens() + completionTokens * pricing.outputUsdPerMillionTokens())
                / 1_000_000;
    }

    static int estimateTokens(String text) {
        return Math.max(1, text.length() / 4);
    }
}
