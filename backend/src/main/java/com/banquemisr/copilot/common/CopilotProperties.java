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
        Ingestion ingestion) {

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
}
