package com.banquemisr.copilot.knowledge.ingestion;

import com.banquemisr.copilot.knowledge.StageResult;
import java.util.List;
import java.util.UUID;

public record IngestionReport(
        UUID documentId,
        String title,
        Outcome outcome,
        int version,
        int pages,
        int chunks,
        int suspiciousChunks,
        List<StageResult> stages) {

    public enum Outcome { INGESTED, NEW_VERSION, DUPLICATE }
}
