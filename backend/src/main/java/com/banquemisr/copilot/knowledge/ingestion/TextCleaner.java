package com.banquemisr.copilot.knowledge.ingestion;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class TextCleaner {

    private static final Pattern PAGE_NUMBER_LINE = Pattern.compile("(?i)^(page\\s+)?\\d+(\\s+of\\s+\\d+)?$");
    private static final Pattern BANNER_LINE = Pattern.compile("(?i)^synthetic training material.*$");
    private static final Pattern LIST_MARKER = Pattern.compile("^(?:[-*]|\\d+\\.)\\s+");
    private static final Pattern HYPHENATED_BREAK = Pattern.compile("(\\p{L})-\\n(\\p{Ll})");

    public List<ExtractedDocument.Page> clean(List<ExtractedDocument.Page> pages) {
        Set<String> repeatedLines = findRepeatedLines(pages);
        List<ExtractedDocument.Page> cleaned = new ArrayList<>();
        for (ExtractedDocument.Page page : pages) {
            String text = cleanText(page.text(), repeatedLines);
            if (!text.isBlank()) {
                cleaned.add(new ExtractedDocument.Page(page.pageNumber(), page.section(), text));
            }
        }
        return cleaned;
    }

    String cleanText(String raw, Set<String> repeatedLines) {
        String text = Normalizer.normalize(raw, Normalizer.Form.NFKC).replace(' ', ' ');
        text = HYPHENATED_BREAK.matcher(text).replaceAll("$1$2");

        List<String> paragraphs = new ArrayList<>();
        StringBuilder paragraph = new StringBuilder();
        for (String rawLine : text.split("\\R")) {
            String line = rawLine.strip();
            if (line.isEmpty()) {
                flush(paragraphs, paragraph);
                continue;
            }
            if (repeatedLines.contains(line) || PAGE_NUMBER_LINE.matcher(line).matches() || BANNER_LINE.matcher(line).matches()) {
                continue;
            }
            if (LIST_MARKER.matcher(line).find()) {
                flush(paragraphs, paragraph);
                line = LIST_MARKER.matcher(line).replaceFirst("");
            }
            if (!paragraph.isEmpty()) {
                paragraph.append(' ');
            }
            paragraph.append(line);
        }
        flush(paragraphs, paragraph);
        return String.join("\n", paragraphs).replaceAll("[ \\t]{2,}", " ").strip();
    }

    private void flush(List<String> paragraphs, StringBuilder paragraph) {
        if (!paragraph.isEmpty()) {
            paragraphs.add(paragraph.toString());
            paragraph.setLength(0);
        }
    }

    private Set<String> findRepeatedLines(List<ExtractedDocument.Page> pages) {
        if (pages.size() < 3) {
            return Set.of();
        }
        Map<String, Integer> counts = new HashMap<>();
        for (ExtractedDocument.Page page : pages) {
            Set<String> seenOnPage = new HashSet<>();
            for (String line : page.text().split("\\R")) {
                String trimmed = line.strip();
                if (!trimmed.isEmpty() && seenOnPage.add(trimmed)) {
                    counts.merge(trimmed, 1, Integer::sum);
                }
            }
        }
        int threshold = (int) Math.ceil(pages.size() * 0.6);
        Set<String> repeated = new HashSet<>();
        counts.forEach((line, count) -> {
            if (count >= threshold) {
                repeated.add(line);
            }
        });
        return repeated;
    }
}
