package com.banquemisr.copilot.knowledge.ingestion;

import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class InjectionDetector {

    private static final List<Pattern> PATTERNS = List.of(
            Pattern.compile("ignore\\s+(all\\s+)?(the\\s+)?(previous|prior|above)\\s+(instructions|rules)"),
            Pattern.compile("disregard\\s+(your|the|all)\\s+(rules|instructions)"),
            Pattern.compile("system\\s+(override|prompt)"),
            Pattern.compile("assistant\\s+instruction"),
            Pattern.compile("you\\s+are\\s+now\\s+"),
            Pattern.compile("pretend\\s+to\\s+be"),
            Pattern.compile("(reveal|print|show)\\s+(your|the)\\s+(system\\s+)?(prompt|instructions)"));

    public boolean isSuspicious(String text) {
        String normalized = text.toLowerCase();
        return PATTERNS.stream().anyMatch(pattern -> pattern.matcher(normalized).find());
    }
}
