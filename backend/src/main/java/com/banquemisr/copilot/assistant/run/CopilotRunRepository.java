package com.banquemisr.copilot.assistant.run;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CopilotRunRepository extends JpaRepository<CopilotRun, String> {

    @Query("select r from CopilotRun r join fetch r.user order by r.createdAt desc")
    List<CopilotRun> findLatest(Limit limit);

    @Query("select r from CopilotRun r join fetch r.user where r.runId = :runId")
    Optional<CopilotRun> findWithUser(String runId);

    @Query("select r.createdAt from CopilotRun r where r.createdAt >= :from and r.createdAt < :to")
    List<Instant> findCreatedAtBetween(Instant from, Instant to);
}
