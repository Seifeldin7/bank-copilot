package com.banquemisr.copilot.knowledge;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "ingestion_stage_log")
public class IngestionStageLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id")
    private Document document;

    private String stage;
    private String status;
    private long durationMs;
    private String detail;
    private String runId;
    private Instant at;

    protected IngestionStageLog() {
    }

    public IngestionStageLog(Document document, StageResult result, String runId) {
        this.document = document;
        this.stage = result.stage().name();
        this.status = result.status().name();
        this.durationMs = result.durationMs();
        this.detail = result.detail();
        this.runId = runId;
        this.at = Instant.now();
    }

    public StageResult toResult() {
        return new StageResult(StageResult.Stage.valueOf(stage), StageResult.Status.valueOf(status), durationMs, detail);
    }
}
