package com.banquemisr.copilot.knowledge.ingestion;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class MarkdownTextExtractor {

    private static final Pattern HEADING = Pattern.compile("^(#{1,6})\\s+(.*)$");

    public ExtractedDocument extract(byte[] bytes) {
        String text = new String(bytes, StandardCharsets.UTF_8);
        String title = null;
        String currentSection = "Introduction";
        StringBuilder body = new StringBuilder();
        List<ExtractedDocument.Page> sections = new ArrayList<>();

        for (String line : text.split("\\R")) {
            Matcher heading = HEADING.matcher(line.trim());
            if (heading.matches()) {
                String headingText = heading.group(2).trim();
                if (title == null && heading.group(1).length() == 1) {
                    title = headingText;
                    currentSection = headingText;
                    continue;
                }
                addSection(sections, currentSection, body);
                currentSection = headingText;
                body.setLength(0);
            } else {
                body.append(line).append('\n');
            }
        }
        addSection(sections, currentSection, body);
        return new ExtractedDocument(title, sections);
    }

    private void addSection(List<ExtractedDocument.Page> sections, String section, StringBuilder body) {
        if (!body.toString().isBlank()) {
            sections.add(new ExtractedDocument.Page(null, section, body.toString()));
        }
    }
}
