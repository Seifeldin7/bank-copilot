package com.banquemisr.copilot.knowledge.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class ChunkerTest {

    private final Chunker chunker = new Chunker(100, 30);

    @Test
    void keepsChunksWithinTheSizeLimit() {
        String text = "Sentence number one is here. ".repeat(20);

        List<String> chunks = chunker.chunkText(text);

        assertThat(chunks).hasSizeGreaterThan(1).allSatisfy(chunk -> assertThat(chunk.length()).isLessThanOrEqualTo(100));
    }

    @Test
    void carriesShortTrailingSentencesIntoTheNextChunkAsOverlap() {
        String text = "Alpha sentence that is fairly long for this test case. Short end. Beta sentence that pushes past the limit for sure.";

        List<String> chunks = chunker.chunkText(text);

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0)).endsWith("Short end.");
        assertThat(chunks.get(1)).startsWith("Short end.");
    }

    @Test
    void neverMixesTextFromDifferentPages() {
        List<ExtractedDocument.Page> pages = List.of(
                new ExtractedDocument.Page(1, null, "Page one text."),
                new ExtractedDocument.Page(2, null, "Page two text."));

        List<Chunker.Chunk> chunks = chunker.chunk(pages);

        assertThat(chunks).extracting(Chunker.Chunk::pageNumber).containsExactly(1, 2);
        assertThat(chunks).extracting(Chunker.Chunk::index).containsExactly(0, 1);
        assertThat(chunks.get(0).text()).isEqualTo("Page one text.");
    }

    @Test
    void splitsASingleSentenceLongerThanTheLimitByWords() {
        String longSentence = "word ".repeat(60).trim();

        List<String> chunks = chunker.chunkText(longSentence);

        assertThat(chunks).hasSizeGreaterThan(1).allSatisfy(chunk -> assertThat(chunk.length()).isLessThanOrEqualTo(100));
    }

    @Test
    void rejectsOverlapThatIsNotSmallerThanTheChunkSize() {
        assertThatThrownBy(() -> new Chunker(100, 100)).isInstanceOf(IllegalArgumentException.class);
    }
}
