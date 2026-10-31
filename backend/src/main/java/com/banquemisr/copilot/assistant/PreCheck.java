package com.banquemisr.copilot.assistant;

import com.banquemisr.copilot.common.CopilotProperties;
import com.banquemisr.copilot.knowledge.ingestion.InjectionDetector;
import com.banquemisr.copilot.retrieval.Evidence;
import com.banquemisr.copilot.retrieval.QueryTerms;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class PreCheck {

    public static final String INJECTION = "The question contains instructions that try to change how the copilot behaves. I can only answer questions about approved banking procedures.";
    public static final String TOO_VAGUE = "The question is too broad to answer reliably. Please say which product or procedure you mean, for example \"debit card replacement fee\".";
    public static final String NO_EVIDENCE = "None of the approved documents contain enough information to answer this question. Please consult your supervisor or the relevant department.";

    private static final Pattern ACTION_REQUEST = Pattern.compile("\\b(approve|execute)\\s+(this|my)\\s+request");

    private final InjectionDetector injectionDetector;
    private final double minSimilarity;

    public PreCheck(InjectionDetector injectionDetector, CopilotProperties properties) {
        this.injectionDetector = injectionDetector;
        this.minSimilarity = properties.retrieval().minSimilarity();
    }

    public Optional<String> beforeSearch(String question) {
        if (injectionDetector.isSuspicious(question) || ACTION_REQUEST.matcher(question.toLowerCase(Locale.ROOT)).find()) {
            return Optional.of(INJECTION);
        }
        if (QueryTerms.of(question).size() < 2) {
            return Optional.of(TOO_VAGUE);
        }
        return Optional.empty();
    }

    public Optional<String> afterSearch(List<Evidence> evidence) {
        boolean relevant = evidence.stream().anyMatch(item -> item.similarity() >= minSimilarity);
        return relevant ? Optional.empty() : Optional.of(NO_EVIDENCE);
    }
}
