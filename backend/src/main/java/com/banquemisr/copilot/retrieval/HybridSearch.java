package com.banquemisr.copilot.retrieval;

import com.banquemisr.copilot.common.CopilotProperties;
import com.banquemisr.copilot.knowledge.DocumentChunk;
import com.banquemisr.copilot.knowledge.DocumentChunkRepository;
import com.banquemisr.copilot.knowledge.ingestion.EmbeddingService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HybridSearch {

    private final DocumentChunkRepository chunks;
    private final EmbeddingService embeddings;
    private final CopilotProperties.Retrieval settings;

    public HybridSearch(DocumentChunkRepository chunks, EmbeddingService embeddings, CopilotProperties properties) {
        this.chunks = chunks;
        this.embeddings = embeddings;
        this.settings = properties.retrieval();
    }

    @Transactional(readOnly = true)
    public List<Evidence> search(String question) {
        return search(question, settings.evidence());
    }

    @Transactional(readOnly = true)
    public List<Evidence> search(String question, int limit) {
        float[] query = embeddings.embed(question);
        List<UUID> semantic = chunks.findNearest(query, Limit.of(settings.candidates()));
        List<UUID> keyword = chunks.findByKeywords(question, settings.candidates());
        List<RankFusion.FusedRank> fused = RankFusion.fuse(semantic, keyword, settings.rrfK()).stream()
                .limit(limit)
                .toList();

        Map<UUID, DocumentChunk> byId = chunks.findWithDocument(fused.stream().map(RankFusion.FusedRank::chunkId).toList())
                .stream()
                .collect(Collectors.toMap(DocumentChunk::getId, Function.identity()));

        return fused.stream()
                .map(rank -> toEvidence(byId.get(rank.chunkId()), rank, query))
                .toList();
    }

    private Evidence toEvidence(DocumentChunk chunk, RankFusion.FusedRank rank, float[] query) {
        return new Evidence(
                chunk.getId(),
                chunk.getDocument().getId(),
                chunk.getDocument().getTitle(),
                chunk.getDocument().getCategory(),
                chunk.getDocument().getVersion(),
                chunk.getPageNumber(),
                chunk.getSection(),
                chunk.getContent(),
                cosineSimilarity(query, chunk.getEmbedding()),
                rank.semanticRank(),
                rank.keywordRank(),
                rank.score());
    }

    static double cosineSimilarity(float[] a, float[] b) {
        double dot = 0;
        double normA = 0;
        double normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        return normA == 0 || normB == 0 ? 0 : dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
