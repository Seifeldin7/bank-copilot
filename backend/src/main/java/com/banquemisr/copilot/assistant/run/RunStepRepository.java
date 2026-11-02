package com.banquemisr.copilot.assistant.run;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RunStepRepository extends JpaRepository<RunStep, UUID> {

    List<RunStep> findByRunIdOrderByStepNo(String runId);

    long countByRunId(String runId);
}
