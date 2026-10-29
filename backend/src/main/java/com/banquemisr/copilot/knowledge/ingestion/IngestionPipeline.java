package com.banquemisr.copilot.knowledge.ingestion;

import com.banquemisr.copilot.audit.AuditService;
import com.banquemisr.copilot.audit.RunContext;
import com.banquemisr.copilot.common.ApiException;
import com.banquemisr.copilot.common.FieldError;
import com.banquemisr.copilot.knowledge.Document;
import com.banquemisr.copilot.knowledge.DocumentChunk;
import com.banquemisr.copilot.knowledge.DocumentChunkRepository;
import com.banquemisr.copilot.knowledge.DocumentRepository;
import com.banquemisr.copilot.knowledge.IngestionStageLog;
import com.banquemisr.copilot.knowledge.IngestionStageLogRepository;
import com.banquemisr.copilot.knowledge.StageResult;
import com.banquemisr.copilot.knowledge.StageResult.Stage;
import com.banquemisr.copilot.knowledge.ingestion.Chunker.Chunk;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IngestionPipeline {

    private final DocumentRepository documents;
    private final DocumentChunkRepository chunks;
    private final IngestionStageLogRepository stageLogs;
    private final PdfTextExtractor pdfExtractor;
    private final MarkdownTextExtractor markdownExtractor;
    private final TextCleaner cleaner;
    private final Chunker chunker;
    private final InjectionDetector injectionDetector;
    private final EmbeddingService embeddings;
    private final AuditService audit;

    public IngestionPipeline(DocumentRepository documents, DocumentChunkRepository chunks,
                             IngestionStageLogRepository stageLogs, PdfTextExtractor pdfExtractor,
                             MarkdownTextExtractor markdownExtractor, TextCleaner cleaner, Chunker chunker,
                             InjectionDetector injectionDetector, EmbeddingService embeddings, AuditService audit) {
        this.documents = documents;
        this.chunks = chunks;
        this.stageLogs = stageLogs;
        this.pdfExtractor = pdfExtractor;
        this.markdownExtractor = markdownExtractor;
        this.cleaner = cleaner;
        this.chunker = chunker;
        this.injectionDetector = injectionDetector;
        this.embeddings = embeddings;
        this.audit = audit;
    }

    @Transactional
    public IngestionReport ingest(IngestionRequest request) {
        List<StageResult> stages = new ArrayList<>();
        Document.Format format = UploadValidator.formatOf(request.fileName());

        long started = System.nanoTime();
        String hash = sha256(request.bytes());
        Optional<Document> duplicate = documents.findByContentSha256(hash);
        if (duplicate.isPresent()) {
            return duplicateReport(duplicate.get(), started, request);
        }
        Optional<Document> previous = documents.findByFileNameAndStatus(request.fileName(), Document.Status.ACTIVE);
        stages.add(StageResult.succeeded(Stage.FINGERPRINT, started, previous
                .map(p -> "New content for " + request.fileName() + "; previous v" + p.getVersion() + " will be superseded")
                .orElse("SHA-256 computed; no existing document with this hash")));

        started = System.nanoTime();
        ExtractedDocument extracted = format == Document.Format.PDF
                ? pdfExtractor.extract(request.bytes())
                : markdownExtractor.extract(request.bytes());
        int pageCount = extracted.pages().size();
        stages.add(StageResult.succeeded(Stage.EXTRACT, started, format == Document.Format.PDF
                ? pageCount + " pages extracted with PDFBox"
                : pageCount + " sections split by heading"));

        started = System.nanoTime();
        List<ExtractedDocument.Page> cleanPages = cleaner.clean(extracted.pages());
        if (cleanPages.isEmpty()) {
            throw ApiException.badRequest("No text could be extracted from this file.",
                    new FieldError("file", "The file contains no readable text."));
        }
        stages.add(StageResult.succeeded(Stage.CLEAN, started, "Whitespace normalised; repeated headers, footers and page numbers removed"));

        started = System.nanoTime();
        List<Chunk> chunkList = chunker.chunk(cleanPages);
        stages.add(StageResult.succeeded(Stage.CHUNK, started, chunkList.size() + " chunks, none crossing a page or section boundary"));

        started = System.nanoTime();
        List<Boolean> suspicious = chunkList.stream().map(chunk -> injectionDetector.isSuspicious(chunk.text())).toList();
        int suspiciousCount = (int) suspicious.stream().filter(flag -> flag).count();
        stages.add(StageResult.succeeded(Stage.SCREEN, started, suspiciousCount == 0
                ? "No instruction-like content detected"
                : suspiciousCount + " chunk(s) flagged as possible prompt injection"));

        started = System.nanoTime();
        List<float[]> vectors = embeddings.embedAll(chunkList.stream().map(Chunk::text).toList());
        stages.add(StageResult.succeeded(Stage.EMBED, started, vectors.size() + " embeddings (all-MiniLM-L6-v2, 384 dimensions)"));

        started = System.nanoTime();
        previous.ifPresent(Document::supersede);
        documents.flush();
        Document document = documents.save(new Document(
                chooseTitle(request, previous, extracted),
                request.fileName(),
                format,
                chooseCategory(request, previous),
                previous.map(p -> p.getVersion() + 1).orElse(1),
                hash,
                request.source(),
                request.storagePath(),
                pageCount,
                chunkList.size(),
                suspiciousCount,
                request.ingestedBy()));
        List<DocumentChunk> entities = new ArrayList<>();
        for (int i = 0; i < chunkList.size(); i++) {
            Chunk chunk = chunkList.get(i);
            entities.add(new DocumentChunk(document, chunk.index(), chunk.pageNumber(), chunk.section(), chunk.text(),
                    vectors.get(i), suspicious.get(i)));
        }
        chunks.saveAll(entities);
        stages.add(StageResult.succeeded(Stage.INDEX, started, "Document and chunks saved; vector and keyword indexes updated"));

        String runId = RunContext.currentRunId();
        stageLogs.saveAll(stages.stream().map(stage -> new IngestionStageLog(document, stage, runId)).toList());

        IngestionReport.Outcome outcome = previous.isPresent() ? IngestionReport.Outcome.NEW_VERSION : IngestionReport.Outcome.INGESTED;
        audit.record(outcome == IngestionReport.Outcome.NEW_VERSION ? "DOCUMENT_NEW_VERSION" : "DOCUMENT_INGESTED",
                request.ingestedBy(), "Document", request.fileName(),
                document.getTitle() + " v" + document.getVersion() + ": " + pageCount + " pages, " + chunkList.size() + " chunks, " + suspiciousCount + " suspicious");
        return new IngestionReport(document.getId(), document.getTitle(), outcome, document.getVersion(), pageCount,
                chunkList.size(), suspiciousCount, stages);
    }

    private IngestionReport duplicateReport(Document existing, long started, IngestionRequest request) {
        List<StageResult> stages = new ArrayList<>();
        stages.add(StageResult.succeeded(Stage.FINGERPRINT, started,
                "Identical content already stored as \"" + existing.getTitle() + "\" v" + existing.getVersion()));
        for (Stage stage : List.of(Stage.EXTRACT, Stage.CLEAN, Stage.CHUNK, Stage.SCREEN, Stage.EMBED, Stage.INDEX)) {
            stages.add(StageResult.skipped(stage, "Skipped: duplicate document"));
        }
        if (request.source() == Document.Source.UPLOAD) {
            audit.record("DOCUMENT_DUPLICATE_SKIPPED", request.ingestedBy(), "Document", request.fileName(),
                    "Duplicate of " + existing.getTitle() + " v" + existing.getVersion());
        }
        return new IngestionReport(existing.getId(), existing.getTitle(), IngestionReport.Outcome.DUPLICATE,
                existing.getVersion(), existing.getPageCount(), existing.getChunkCount(), existing.getSuspiciousChunks(), stages);
    }

    private String chooseTitle(IngestionRequest request, Optional<Document> previous, ExtractedDocument extracted) {
        if (request.title() != null && !request.title().isBlank()) {
            return request.title().trim();
        }
        if (previous.isPresent()) {
            return previous.get().getTitle();
        }
        if (extracted.title() != null && !extracted.title().isBlank()) {
            return extracted.title().trim();
        }
        return titleFromFileName(request.fileName());
    }

    private String chooseCategory(IngestionRequest request, Optional<Document> previous) {
        if (request.category() != null && !request.category().isBlank()) {
            return request.category().trim();
        }
        return previous.map(Document::getCategory).orElse("General");
    }

    static String titleFromFileName(String fileName) {
        String base = fileName.replaceAll("(?i)\\.(pdf|md|markdown)$", "");
        StringBuilder title = new StringBuilder();
        for (String word : base.split("[-_\\s]+")) {
            if (!word.isEmpty()) {
                title.append(title.isEmpty() ? "" : " ").append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
            }
        }
        return title.toString();
    }

    static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
