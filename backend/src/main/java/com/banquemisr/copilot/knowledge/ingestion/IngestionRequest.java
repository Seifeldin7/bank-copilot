package com.banquemisr.copilot.knowledge.ingestion;

import com.banquemisr.copilot.identity.AppUser;
import com.banquemisr.copilot.knowledge.Document;

public record IngestionRequest(
        String fileName,
        byte[] bytes,
        String title,
        String category,
        Document.Source source,
        String storagePath,
        AppUser ingestedBy) {
}
