package com.banquemisr.copilot.knowledge.ingestion;

import java.util.List;

public record ExtractedDocument(String title, List<Page> pages) {

    public record Page(Integer pageNumber, String section, String text) {
    }
}
