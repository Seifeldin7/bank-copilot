package com.banquemisr.copilot.assistant;

import com.banquemisr.copilot.assistant.CopilotDtos.ValidationVerdict;
import com.banquemisr.copilot.assistant.agent.PolicyValidationAgent;
import com.banquemisr.copilot.assistant.agent.RequestPreparationAgent;
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
    private static final double MIN_COVERAGE = 0.5;
    private static final int MAX_PASSAGES = 2;
    private static final int MAX_SENTENCES = 2;

    private record Passage(String id, String text) {
    }

    private record Pick(String id, double coverage, String text) {
    }

    private final JsonMapper jsonMapper;
    private final StubRequestDrafter drafter = new StubRequestDrafter();

    public StubChatModel(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        String system = prompt.getSystemMessage().getText();
        String user = prompt.getUserMessage().getText();
        Object reply;
        if (system.startsWith(PolicyValidationAgent.TASK)) {
            reply = validate(user);
        } else if (system.startsWith(RequestPreparationAgent.TASK)) {
            reply = drafter.draft(user);
        } else {
            reply = answer(user);
        }

        String json = jsonMapper.writeValueAsString(reply);
        ChatResponseMetadata metadata = ChatResponseMetadata.builder()
                .model(MODEL_NAME)
                .usage(new DefaultUsage(ModelGateway.estimateTokens(prompt.getContents()), ModelGateway.estimateTokens(json)))
                .build();
        return new ChatResponse(List.of(new Generation(new AssistantMessage(json))), metadata);
    }

    AnswerDraft answer(String userPrompt) {
        List<String> terms = questionTerms(userPrompt);
        if (terms.isEmpty()) {
            return AnswerDraft.refusal(NO_MATCH);
        }
        List<Pick> chosen = passages(userPrompt).stream()
                .map(passage -> pick(passage, terms))
                .flatMap(Optional::stream)
                .sorted(Comparator.comparingDouble(Pick::coverage).reversed())
                .limit(MAX_PASSAGES)
                .toList();
        if (chosen.isEmpty()) {
            return AnswerDraft.refusal(NO_MATCH);
        }
        String answer = String.join(" ", chosen.stream().map(p -> p.text() + " [" + p.id() + "]").toList());
        return new AnswerDraft(answer, chosen.stream().map(Pick::id).toList(), false, null);
    }

    ValidationVerdict validate(String userPrompt) {
        List<String> terms = questionTerms(userPrompt);
        List<Passage> passages = passages(userPrompt);
        double best = passages.stream().mapToDouble(passage -> coverage(passage.text(), terms)).max().orElse(0);
        if (best >= MIN_COVERAGE) {
            return new ValidationVerdict(true, List.of(), List.of(), Math.round((0.35 + 0.6 * best) * 100) / 100.0);
        }
        List<String> unmentioned = terms.stream()
                .filter(term -> passages.stream().noneMatch(passage -> mentions(passage.text(), term)))
                .toList();
        String missing = unmentioned.isEmpty()
                ? "No single passage covers the whole question"
                : "The documents do not mention: " + String.join(", ", unmentioned);
        return new ValidationVerdict(false, List.of(missing), List.of(), Math.round(best * 50) / 100.0);
    }

    private Optional<Pick> pick(Passage passage, List<String> terms) {
        List<String> sentences = Arrays.stream(passage.text().trim().split("(?<=[.!?])\\s+"))
                .map(sentence -> sentence.replaceAll("\\s+", " ").trim())
                .filter(sentence -> !sentence.isEmpty())
                .toList();
        List<String> best = sentences.stream()
                .filter(sentence -> matchCount(sentence, terms) > 0)
                .sorted(Comparator.comparingLong((String sentence) -> matchCount(sentence, terms)).reversed())
                .limit(MAX_SENTENCES)
                .sorted(Comparator.comparingInt(sentences::indexOf))
                .toList();
        long bestScore = best.stream().mapToLong(sentence -> matchCount(sentence, terms)).max().orElse(0);
        double coverage = coverage(passage.text(), terms);
        if (coverage < MIN_COVERAGE || bestScore < Math.min(2, terms.size())) {
            return Optional.empty();
        }
        return Optional.of(new Pick(passage.id(), coverage, String.join(" ", best)));
    }

    private static List<String> questionTerms(String userPrompt) {
        Matcher question = QUESTION.matcher(userPrompt);
        return question.find() ? QueryTerms.of(question.group(1)) : List.of();
    }

    private static List<Passage> passages(String userPrompt) {
        List<Passage> passages = new ArrayList<>();
        Matcher evidence = EVIDENCE.matcher(userPrompt);
        while (evidence.find()) {
            passages.add(new Passage(evidence.group(1), evidence.group(2)));
        }
        return passages;
    }

    private static double coverage(String text, List<String> terms) {
        return terms.isEmpty() ? 0 : (double) matchCount(text, terms) / terms.size();
    }

    private static long matchCount(String text, List<String> terms) {
        return terms.stream().filter(term -> mentions(text, term)).count();
    }

    static boolean mentions(String text, String term) {
        return Arrays.stream(text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+"))
                .anyMatch(word -> QueryTerms.matches(term, word));
    }
}
