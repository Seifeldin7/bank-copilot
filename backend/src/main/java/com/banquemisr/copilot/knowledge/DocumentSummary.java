package com.banquemisr.copilot.knowledge;

import com.banquemisr.copilot.identity.UserDtos.UserRef;
import java.time.Instant;
import java.util.UUID;

public record DocumentSummary(
        UUID id,
        String title,
        String fileName,
        Document.Format format,
        String category,
        int version,
        int pageCount,
        int chunkCount,
        int suspiciousChunks,
        Document.Status status,
        Document.Source source,
        String contentSha256,
        UserRef ingestedBy,
        Instant ingestedAt) {

    public static DocumentSummary of(Document document) {
        return new DocumentSummary(document.getId(), document.getTitle(), document.getFileName(), document.getFormat(),
                document.getCategory(), document.getVersion(), document.getPageCount(), document.getChunkCount(),
                document.getSuspiciousChunks(), document.getStatus(), document.getSource(), document.getContentSha256(),
                UserRef.of(document.getIngestedBy()), document.getIngestedAt());
    }
}
