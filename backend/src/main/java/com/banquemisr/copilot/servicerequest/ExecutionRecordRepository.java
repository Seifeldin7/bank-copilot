package com.banquemisr.copilot.servicerequest;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ExecutionRecordRepository extends JpaRepository<ExecutionRecord, UUID> {

    @Query("select e from ExecutionRecord e join fetch e.executedBy where e.request.id = :requestId")
    Optional<ExecutionRecord> findByRequestId(UUID requestId);

    @Query("select e from ExecutionRecord e join fetch e.executedBy where e.request.id in :requestIds")
    List<ExecutionRecord> findByRequestIds(Collection<UUID> requestIds);
}
