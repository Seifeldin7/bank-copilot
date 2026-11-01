package com.banquemisr.copilot.servicerequest;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface ServiceRequestRepository extends JpaRepository<ServiceRequest, UUID> {

    @Query(nativeQuery = true, value = "select nextval('service_request_number')")
    long nextNumber();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ServiceRequest r where r.id = :id")
    Optional<ServiceRequest> findForUpdate(UUID id);

    @Query("""
            select r from ServiceRequest r
            join fetch r.requester
            left join fetch r.decidedBy
            where r.id = :id
            """)
    Optional<ServiceRequest> findDetailed(UUID id);

    @Query("""
            select r from ServiceRequest r
            join fetch r.requester
            left join fetch r.decidedBy
            where (:requesterId is null or r.requester.id = :requesterId)
              and (:status is null or r.status = :status)
              and (:category is null or r.category = :category)
            order by r.updatedAt desc
            """)
    List<ServiceRequest> search(UUID requesterId, RequestStatus status, RequestCategory category);

    @Query("""
            select r from ServiceRequest r
            join fetch r.requester
            where r.status = com.banquemisr.copilot.servicerequest.RequestStatus.PENDING_APPROVAL
            order by r.submittedAt
            """)
    List<ServiceRequest> findQueue();
}
