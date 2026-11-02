package com.banquemisr.copilot.assistant.run;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AiUsageRepository extends JpaRepository<AiUsage, Long> {

    List<AiUsage> findByRunIdOrderById(String runId);

    @Query("select u from AiUsage u where u.at >= :from and u.at < :to")
    List<AiUsage> findBetween(Instant from, Instant to);
}
