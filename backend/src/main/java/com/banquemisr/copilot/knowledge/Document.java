package com.banquemisr.copilot.knowledge;

import com.banquemisr.copilot.identity.AppUser;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "document")
public class Document {

    public enum Format { PDF, MARKDOWN }

    public enum Status { ACTIVE, SUPERSEDED, RETIRED }

    public enum Source { CORPUS, UPLOAD }

    @Id
    private UUID id;

    private String title;
    private String fileName;

    @Enumerated(EnumType.STRING)
    private Format format;

    private String category;
    private int version;
    private String contentSha256;

    @Enumerated(EnumType.STRING)
    private Status status;

    @Enumerated(EnumType.STRING)
    private Source source;

    private String storagePath;
    private int pageCount;
    private int chunkCount;
    private int suspiciousChunks;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ingested_by")
    private AppUser ingestedBy;

    private Instant ingestedAt;
    private Instant supersededAt;

    protected Document() {
    }

    public Document(String title, String fileName, Format format, String category, int version, String contentSha256,
                    Source source, String storagePath, int pageCount, int chunkCount, int suspiciousChunks,
                    AppUser ingestedBy) {
        this.id = UUID.randomUUID();
        this.title = title;
        this.fileName = fileName;
        this.format = format;
        this.category = category;
        this.version = version;
        this.contentSha256 = contentSha256;
        this.status = Status.ACTIVE;
        this.source = source;
        this.storagePath = storagePath;
        this.pageCount = pageCount;
        this.chunkCount = chunkCount;
        this.suspiciousChunks = suspiciousChunks;
        this.ingestedBy = ingestedBy;
        this.ingestedAt = Instant.now();
    }

    public void supersede() {
        this.status = Status.SUPERSEDED;
        this.supersededAt = Instant.now();
    }

    public void retire() {
        this.status = Status.RETIRED;
    }

    public UUID getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getFileName() {
        return fileName;
    }

    public Format getFormat() {
        return format;
    }

    public String getCategory() {
        return category;
    }

    public int getVersion() {
        return version;
    }

    public String getContentSha256() {
        return contentSha256;
    }

    public Status getStatus() {
        return status;
    }

    public Source getSource() {
        return source;
    }

    public String getStoragePath() {
        return storagePath;
    }

    public int getPageCount() {
        return pageCount;
    }

    public int getChunkCount() {
        return chunkCount;
    }

    public int getSuspiciousChunks() {
        return suspiciousChunks;
    }

    public AppUser getIngestedBy() {
        return ingestedBy;
    }

    public Instant getIngestedAt() {
        return ingestedAt;
    }
}
