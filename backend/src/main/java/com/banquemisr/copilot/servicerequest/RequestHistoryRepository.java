package com.banquemisr.copilot.servicerequest;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface RequestHistoryRepository extends JpaRepository<RequestHistory, UUID> {

    @Query("select h from RequestHistory h join fetch h.actor where h.request.id = :requestId order by h.at")
    List<RequestHistory> findTimeline(UUID requestId);
}
