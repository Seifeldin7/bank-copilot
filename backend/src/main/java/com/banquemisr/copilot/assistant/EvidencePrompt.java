package com.banquemisr.copilot.assistant;

import com.banquemisr.copilot.retrieval.Evidence;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class EvidencePrompt {

    private EvidencePrompt() {
    }

    public static Map<String, Evidence> label(List<Evidence> evidence) {
        Map<String, Evidence> byId = new LinkedHashMap<>();
        for (int i = 0; i < evidence.size(); i++) {
            byId.put("E" + (i + 1), evidence.get(i));
        }
        return byId;
    }

    public static String format(String question, Map<String, Evidence> byId) {
        StringBuilder prompt = new StringBuilder()
                .append("<question>").append(stripTags(question)).append("</question>\n\n");
        byId.forEach((id, item) -> prompt
                .append("<evidence id=\"").append(id).append("\" source=\"").append(source(item)).append("\">\n")
                .append(stripTags(item.content()))
                .append("\n</evidence>\n\n"));
        return prompt.toString();
    }

    public static String stripTags(String text) {
        return text.replaceAll("(?i)</?(evidence|question|answer|category|schema)[^>]*>", "");
    }

    private static String source(Evidence item) {
        String location = item.page() != null ? ", page " + item.page() : item.section() != null ? ", " + item.section() : "";
        return (item.documentTitle() + " v" + item.version() + location).replaceAll("[\"<>]", "");
    }
}
