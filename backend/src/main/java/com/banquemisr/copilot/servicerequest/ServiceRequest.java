package com.banquemisr.copilot.servicerequest;

import com.banquemisr.copilot.common.ApiException;
import com.banquemisr.copilot.identity.AppUser;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.NaturalId;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "service_request")
public class ServiceRequest {

    public enum Decision { APPROVED, REJECTED, EDITED_APPROVED }

    @Id
    private UUID id;

    @NaturalId
    private String reference;

    @Enumerated(EnumType.STRING)
    private RequestCategory category;

    @Enumerated(EnumType.STRING)
    private RequestStatus status;

    private String title;
    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    private RequestPayload payload;

    @JdbcTypeCode(SqlTypes.JSON)
    private ValidationResult validation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requester_id")
    private AppUser requester;

    private String sourceRunId;

    @Enumerated(EnumType.STRING)
    private Decision decision;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decided_by")
    private AppUser decidedBy;

    private String decisionReason;

    @JdbcTypeCode(SqlTypes.JSON)
    private List<FieldChange> decisionChanges;

    private Instant decidedAt;

    @Version
    private int version;

    private Instant createdAt;
    private Instant updatedAt;
    private Instant submittedAt;
    private Instant executedAt;

    protected ServiceRequest() {
    }

    public ServiceRequest(String reference, AppUser requester, String title, String description, RequestPayload payload,
                          ValidationResult validation, String sourceRunId) {
        this.id = UUID.randomUUID();
        this.reference = reference;
        this.category = payload.category();
        this.status = RequestStatus.DRAFT;
        this.title = title.trim();
        this.description = description.trim();
        this.payload = payload;
        this.validation = validation;
        this.requester = requester;
        this.sourceRunId = sourceRunId;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public boolean isOwnedBy(AppUser user) {
        return requester.getId().equals(user.getId());
    }

    public void requireVersion(int expected) {
        if (expected != version) {
            throw ApiException.conflict("This request was changed by someone else. Reload it and try again.");
        }
    }

    public void edit(String title, String description, RequestPayload payload, ValidationResult validation) {
        this.title = title.trim();
        this.description = description.trim();
        this.payload = payload;
        this.validation = validation;
        this.updatedAt = Instant.now();
    }

    public void moveTo(RequestStatus next) {
        if (!status.canMoveTo(next)) {
            throw ApiException.conflict("A request in status " + status + " cannot move to " + next + ".");
        }
        status = next;
        updatedAt = Instant.now();
        if (next == RequestStatus.PENDING_APPROVAL) {
            submittedAt = updatedAt;
        } else if (next == RequestStatus.EXECUTED) {
            executedAt = updatedAt;
        }
    }

    public void recordDecision(Decision decision, AppUser supervisor, String reason, List<FieldChange> changes) {
        this.decision = decision;
        this.decidedBy = supervisor;
        this.decisionReason = reason;
        this.decisionChanges = List.copyOf(changes);
        this.decidedAt = Instant.now();
    }

    public void updateValidation(ValidationResult validation) {
        this.validation = validation;
    }

    public UUID getId() {
        return id;
    }

    public String getReference() {
        return reference;
    }

    public RequestCategory getCategory() {
        return category;
    }

    public RequestStatus getStatus() {
        return status;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public RequestPayload getPayload() {
        return payload;
    }

    public ValidationResult getValidation() {
        return validation;
    }

    public AppUser getRequester() {
        return requester;
    }

    public String getSourceRunId() {
        return sourceRunId;
    }

    public Decision getDecision() {
        return decision;
    }

    public AppUser getDecidedBy() {
        return decidedBy;
    }

    public String getDecisionReason() {
        return decisionReason;
    }

    public List<FieldChange> getDecisionChanges() {
        return decisionChanges;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public int getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public Instant getExecutedAt() {
        return executedAt;
    }
}
