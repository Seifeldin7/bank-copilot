package com.banquemisr.copilot.knowledge.ingestion;

import com.banquemisr.copilot.common.CopilotProperties;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class Chunker {

    private final int chunkSize;
    private final int overlap;

    @Autowired
    public Chunker(CopilotProperties properties) {
        this(properties.ingestion().chunkSize(), properties.ingestion().chunkOverlap());
    }

    Chunker(int chunkSize, int overlap) {
        if (overlap >= chunkSize) {
            throw new IllegalArgumentException("Chunk overlap must be smaller than the chunk size");
        }
        this.chunkSize = chunkSize;
        this.overlap = overlap;
    }

    public List<Chunk> chunk(List<ExtractedDocument.Page> pages) {
        List<Chunk> chunks = new ArrayList<>();
        for (ExtractedDocument.Page page : pages) {
            for (String text : chunkText(page.text())) {
                chunks.add(new Chunk(chunks.size(), page.pageNumber(), page.section(), text));
            }
        }
        return chunks;
    }

    List<String> chunkText(String text) {
        List<String> chunks = new ArrayList<>();
        List<String> current = new ArrayList<>();
        for (String sentence : splitIntoSentences(text)) {
            if (!current.isEmpty() && lengthWith(current, sentence) > chunkSize) {
                chunks.add(String.join(" ", current));
                current = overlapTail(current);
                if (lengthWith(current, sentence) > chunkSize) {
                    current = new ArrayList<>();
                }
            }
            current.add(sentence);
        }
        if (!current.isEmpty()) {
            chunks.add(String.join(" ", current));
        }
        return chunks;
    }

    private int lengthWith(List<String> sentences, String next) {
        int length = next.length();
        for (String sentence : sentences) {
            length += sentence.length() + 1;
        }
        return length;
    }

    private List<String> overlapTail(List<String> sentences) {
        List<String> tail = new ArrayList<>();
        int length = 0;
        for (int i = sentences.size() - 1; i >= 0; i--) {
            String sentence = sentences.get(i);
            if (length + sentence.length() > overlap) {
                break;
            }
            tail.addFirst(sentence);
            length += sentence.length() + 1;
        }
        return tail;
    }

    private List<String> splitIntoSentences(String text) {
        List<String> sentences = new ArrayList<>();
        for (String paragraph : text.split("\\n")) {
            for (String sentence : paragraph.split("(?<=[.!?])\\s+")) {
                String trimmed = sentence.strip();
                if (!trimmed.isEmpty()) {
                    sentences.addAll(splitLongSentence(trimmed));
                }
            }
        }
        return sentences;
    }

    private List<String> splitLongSentence(String sentence) {
        if (sentence.length() <= chunkSize) {
            return List.of(sentence);
        }
        List<String> parts = new ArrayList<>();
        StringBuilder part = new StringBuilder();
        for (String word : sentence.split("\\s+")) {
            if (!part.isEmpty() && part.length() + 1 + word.length() > chunkSize) {
                parts.add(part.toString());
                part.setLength(0);
            }
            if (!part.isEmpty()) {
                part.append(' ');
            }
            part.append(word);
        }
        if (!part.isEmpty()) {
            parts.add(part.toString());
        }
        return parts;
    }

    public record Chunk(int index, Integer pageNumber, String section, String text) {
    }
}
