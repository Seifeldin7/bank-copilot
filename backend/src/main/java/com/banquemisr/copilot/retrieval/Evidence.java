package com.banquemisr.copilot.retrieval;

import java.util.UUID;

public record Evidence(
        UUID chunkId,
        UUID documentId,
        String documentTitle,
        int version,
        Integer page,
        String section,
        String content,
        double similarity,
        Integer semanticRank,
        Integer keywordRank,
        double fusedScore) {
}
