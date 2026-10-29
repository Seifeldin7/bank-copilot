package com.banquemisr.copilot.knowledge;

import com.banquemisr.copilot.audit.AuditService;
import com.banquemisr.copilot.common.ApiException;
import com.banquemisr.copilot.identity.AppUser;
import com.banquemisr.copilot.knowledge.ingestion.IngestionPipeline;
import com.banquemisr.copilot.knowledge.ingestion.IngestionReport;
import com.banquemisr.copilot.knowledge.ingestion.IngestionRequest;
import com.banquemisr.copilot.knowledge.ingestion.UploadValidator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentService {

    private final DocumentRepository documents;
    private final IngestionStageLogRepository stageLogs;
    private final IngestionPipeline pipeline;
    private final UploadValidator uploadValidator;
    private final UploadStorage storage;
    private final AuditService audit;

    public DocumentService(DocumentRepository documents, IngestionStageLogRepository stageLogs, IngestionPipeline pipeline,
                           UploadValidator uploadValidator, UploadStorage storage, AuditService audit) {
        this.documents = documents;
        this.stageLogs = stageLogs;
        this.pipeline = pipeline;
        this.uploadValidator = uploadValidator;
        this.storage = storage;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<DocumentSummary> list() {
        return documents.findAllByOrderByTitleAscVersionDesc().stream().map(DocumentSummary::of).toList();
    }

    @Transactional(readOnly = true)
    public List<StageResult> ingestionLog(UUID documentId) {
        if (!documents.existsById(documentId)) {
            throw ApiException.notFound("Document not found.");
        }
        return stageLogs.findByDocumentIdOrderByIdAsc(documentId).stream().map(IngestionStageLog::toResult).toList();
    }

    public IngestionReport upload(String fileName, byte[] bytes, String title, String category, AppUser uploadedBy) {
        uploadValidator.validate(fileName, bytes);
        String storagePath = storage.store(fileName, bytes);
        return pipeline.ingest(new IngestionRequest(fileName, bytes, title, category, Document.Source.UPLOAD, storagePath, uploadedBy));
    }

    @Transactional
    public DocumentSummary retire(UUID documentId, AppUser actor) {
        Document document = documents.findById(documentId).orElseThrow(() -> ApiException.notFound("Document not found."));
        if (document.getStatus() == Document.Status.RETIRED) {
            throw ApiException.conflict("This document is already retired.");
        }
        document.retire();
        audit.record("DOCUMENT_RETIRED", actor, "Document", document.getFileName(),
                document.getTitle() + " v" + document.getVersion() + " removed from retrieval");
        return DocumentSummary.of(document);
    }
}
