package com.banquemisr.copilot.knowledge;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.Array;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "document_chunk")
public class DocumentChunk {

    public static final int EMBEDDING_DIMENSIONS = 384;

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id")
    private Document document;

    private int chunkIndex;
    private Integer pageNumber;
    private String section;
    private String content;

    @JdbcTypeCode(SqlTypes.VECTOR)
    @Array(length = EMBEDDING_DIMENSIONS)
    private float[] embedding;

    private boolean suspicious;

    protected DocumentChunk() {
    }

    public DocumentChunk(Document document, int chunkIndex, Integer pageNumber, String section, String content,
                         float[] embedding, boolean suspicious) {
        this.id = UUID.randomUUID();
        this.document = document;
        this.chunkIndex = chunkIndex;
        this.pageNumber = pageNumber;
        this.section = section;
        this.content = content;
        this.embedding = embedding;
        this.suspicious = suspicious;
    }

    public UUID getId() {
        return id;
    }

    public Document getDocument() {
        return document;
    }

    public int getChunkIndex() {
        return chunkIndex;
    }

    public Integer getPageNumber() {
        return pageNumber;
    }

    public String getSection() {
        return section;
    }

    public String getContent() {
        return content;
    }

    public float[] getEmbedding() {
        return embedding;
    }

    public boolean isSuspicious() {
        return suspicious;
    }
}
