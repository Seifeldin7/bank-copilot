package com.banquemisr.copilot.assistant;

import com.banquemisr.copilot.retrieval.QueryTerms;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
@ConditionalOnProperty(name = "spring.ai.model.chat", havingValue = "stub")
public class StubChatModel implements ChatModel {

    public static final String MODEL_NAME = "stub-extractive";
    static final String NO_MATCH = "The evidence passages do not answer this question.";

    private static final Pattern QUESTION = Pattern.compile("<question>(.*?)</question>", Pattern.DOTALL);
    private static final Pattern EVIDENCE = Pattern.compile("<evidence id=\"(E\\d+)\"[^>]*>(.*?)</evidence>", Pattern.DOTALL);
    private static final int MAX_PASSAGES = 2;
    private static final int MAX_SENTENCES = 2;

    private record Pick(String id, double coverage, String text) {
    }

    private final JsonMapper jsonMapper;

    public StubChatModel(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        String json = jsonMapper.writeValueAsString(answer(prompt.getUserMessage().getText()));
        int promptTokens = ModelGateway.estimateTokens(prompt.getContents());
        int completionTokens = ModelGateway.estimateTokens(json);
        ChatResponseMetadata metadata = ChatResponseMetadata.builder()
                .model(MODEL_NAME)
                .usage(new DefaultUsage(promptTokens, completionTokens))
                .build();
        return new ChatResponse(List.of(new Generation(new AssistantMessage(json))), metadata);
    }

    AnswerDraft answer(String userPrompt) {
        Matcher question = QUESTION.matcher(userPrompt);
        List<String> terms = question.find() ? QueryTerms.of(question.group(1)) : List.of();
        if (terms.isEmpty()) {
            return AnswerDraft.refusal(NO_MATCH);
        }

        List<Pick> picks = new ArrayList<>();
        Matcher evidence = EVIDENCE.matcher(userPrompt);
        while (evidence.find()) {
            pick(evidence.group(1), evidence.group(2), terms).ifPresent(picks::add);
        }
        if (picks.isEmpty()) {
            return AnswerDraft.refusal(NO_MATCH);
        }

        List<Pick> chosen = picks.stream()
                .sorted(Comparator.comparingDouble(Pick::coverage).reversed())
                .limit(MAX_PASSAGES)
                .toList();
        String answer = String.join(" ", chosen.stream().map(p -> p.text() + " [" + p.id() + "]").toList());
        return new AnswerDraft(answer, chosen.stream().map(Pick::id).toList(), false, null);
    }

    private Optional<Pick> pick(String id, String passage, List<String> terms) {
        List<String> sentences = Arrays.stream(passage.trim().split("(?<=[.!?])\\s+"))
                .map(sentence -> sentence.replaceAll("\\s+", " ").trim())
                .filter(sentence -> !sentence.isEmpty())
                .toList();
        long covered = terms.stream().filter(term -> mentions(passage, term)).count();
        double coverage = (double) covered / terms.size();

        List<String> best = sentences.stream()
                .filter(sentence -> matchCount(sentence, terms) > 0)
                .sorted(Comparator.comparingLong((String sentence) -> matchCount(sentence, terms)).reversed())
                .limit(MAX_SENTENCES)
                .sorted(Comparator.comparingInt(sentences::indexOf))
                .toList();
        long bestScore = best.stream().mapToLong(sentence -> matchCount(sentence, terms)).max().orElse(0);

        if (coverage < 0.5 || bestScore < Math.min(2, terms.size())) {
            return Optional.empty();
        }
        return Optional.of(new Pick(id, coverage, String.join(" ", best)));
    }

    private static long matchCount(String sentence, List<String> terms) {
        return terms.stream().filter(term -> mentions(sentence, term)).count();
    }

    private static boolean mentions(String text, String term) {
        return Arrays.stream(text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+"))
                .anyMatch(word -> QueryTerms.matches(term, word));
    }
}
