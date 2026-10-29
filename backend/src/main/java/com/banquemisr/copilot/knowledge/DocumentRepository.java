package com.banquemisr.copilot.knowledge;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentRepository extends JpaRepository<Document, UUID> {

    Optional<Document> findByContentSha256(String contentSha256);

    Optional<Document> findByFileNameAndStatus(String fileName, Document.Status status);

    @EntityGraph(attributePaths = "ingestedBy")
    List<Document> findAllByOrderByTitleAscVersionDesc();

    long countByStatus(Document.Status status);
}
