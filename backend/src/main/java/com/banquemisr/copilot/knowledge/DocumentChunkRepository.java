package com.banquemisr.copilot.knowledge;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, UUID> {

    @Query("""
            select c.id from DocumentChunk c
            where c.document.status = com.banquemisr.copilot.knowledge.Document.Status.ACTIVE
              and c.suspicious = false
            order by cosine_distance(c.embedding, :query)
            """)
    List<UUID> findNearest(float[] query, Limit limit);

    @Query(nativeQuery = true, value = """
            with q as (
                select replace(plainto_tsquery('english', :text)::text, '&', '|')::tsquery as query
            )
            select c.id
            from document_chunk c
            join document d on d.id = c.document_id
            cross join q
            where d.status = 'ACTIVE'
              and not c.suspicious
              and c.content_tsv @@ q.query
            order by ts_rank_cd(c.content_tsv, q.query) desc, c.id
            limit :limit
            """)
    List<UUID> findByKeywords(String text, int limit);

    @Query("select c from DocumentChunk c join fetch c.document where c.id in :ids")
    List<DocumentChunk> findWithDocument(Collection<UUID> ids);
}
