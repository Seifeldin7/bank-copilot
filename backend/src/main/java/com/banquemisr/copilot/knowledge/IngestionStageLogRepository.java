package com.banquemisr.copilot.knowledge;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IngestionStageLogRepository extends JpaRepository<IngestionStageLog, Long> {

    List<IngestionStageLog> findByDocumentIdOrderByIdAsc(UUID documentId);
}
