package com.banquemisr.copilot.audit;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {

    @EntityGraph(attributePaths = "actor")
    List<AuditEvent> findAllByOrderByAtDesc(Limit limit);

    @EntityGraph(attributePaths = "actor")
    List<AuditEvent> findByRunIdOrderByAt(String runId);

    @EntityGraph(attributePaths = "actor")
    List<AuditEvent> findByResourceTypeAndResourceIdOrderByAt(String resourceType, String resourceId);
}
