package com.banquemisr.copilot.knowledge.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class TextCleanerTest {

    private final TextCleaner cleaner = new TextCleaner();

    @Test
    void removesHeadersFootersAndPageNumbersRepeatedAcrossPages() {
        List<ExtractedDocument.Page> pages = List.of(
                page(1, "Debit Card Replacement Procedure\nFirst page body.\nPage 1 of 3"),
                page(2, "Debit Card Replacement Procedure\nSecond page body.\nPage 2 of 3"),
                page(3, "Debit Card Replacement Procedure\nThird page body.\nPage 3 of 3"));

        List<ExtractedDocument.Page> cleaned = cleaner.clean(pages);

        assertThat(cleaned).extracting(ExtractedDocument.Page::text)
                .containsExactly("First page body.", "Second page body.", "Third page body.");
    }

    @Test
    void joinsHyphenatedWordsAndLinesOfTheSameParagraph() {
        List<ExtractedDocument.Page> cleaned = cleaner.clean(List.of(page(1, "The replace-\nment card is ready\nwithin 7 working days.")));

        assertThat(cleaned.getFirst().text()).isEqualTo("The replacement card is ready within 7 working days.");
    }

    @Test
    void dropsSyntheticBannerAndListMarkersButKeepsListItemsAsSeparateLines() {
        String raw = "Synthetic training material. Not an official policy of any bank.\n- Valid national ID\n- Proof of address";

        List<ExtractedDocument.Page> cleaned = cleaner.clean(List.of(page(1, raw)));

        assertThat(cleaned.getFirst().text()).isEqualTo("Valid national ID\nProof of address");
    }

    @Test
    void dropsPagesThatBecomeEmpty() {
        assertThat(cleaner.clean(List.of(page(1, "  \n12\n")))).isEmpty();
    }

    private ExtractedDocument.Page page(int number, String text) {
        return new ExtractedDocument.Page(number, null, text);
    }
}
