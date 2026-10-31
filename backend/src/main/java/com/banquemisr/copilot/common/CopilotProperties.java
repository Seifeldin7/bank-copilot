package com.banquemisr.copilot.common;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "copilot")
public record CopilotProperties(
        Security security,
        Demo demo,
        Corpus corpus,
        Uploads uploads,
        Ingestion ingestion,
        Retrieval retrieval,
        Llm llm) {

    public record Security(String jwtSecret, Duration tokenTtl, List<String> allowedOrigins) {
    }

    public record Demo(boolean seedUsers) {
    }

    public record Corpus(String dir, boolean bootstrapEnabled) {
    }

    public record Uploads(String dir) {
    }

    public record Ingestion(int chunkSize, int chunkOverlap) {
    }

    public record Retrieval(int candidates, int evidence, int rrfK, double minSimilarity) {
    }

    public record Llm(double inputUsdPerMillionTokens, double outputUsdPerMillionTokens) {
    }
}
