package com.banquemisr.copilot.servicerequest;

import com.banquemisr.copilot.identity.AppUser;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "request_history")
public class RequestHistory {

    public enum Action { CREATED, UPDATED, SUBMITTED, APPROVED, REJECTED, EDITED_AND_APPROVED, EXECUTED, EXECUTION_RETRY }

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "request_id")
    private ServiceRequest request;

    @Enumerated(EnumType.STRING)
    private Action action;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id")
    private AppUser actor;

    @Enumerated(EnumType.STRING)
    private RequestStatus fromStatus;

    @Enumerated(EnumType.STRING)
    private RequestStatus toStatus;

    @JdbcTypeCode(SqlTypes.JSON)
    private List<FieldChange> changes;

    private String reason;
    private String runId;
    private Instant at;

    protected RequestHistory() {
    }

    public RequestHistory(ServiceRequest request, Action action, AppUser actor, RequestStatus fromStatus,
                          RequestStatus toStatus, List<FieldChange> changes, String reason, String runId) {
        this.id = UUID.randomUUID();
        this.request = request;
        this.action = action;
        this.actor = actor;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.changes = List.copyOf(changes);
        this.reason = reason;
        this.runId = runId;
        this.at = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public Action getAction() {
        return action;
    }

    public AppUser getActor() {
        return actor;
    }

    public RequestStatus getFromStatus() {
        return fromStatus;
    }

    public RequestStatus getToStatus() {
        return toStatus;
    }

    public List<FieldChange> getChanges() {
        return changes;
    }

    public String getReason() {
        return reason;
    }

    public String getRunId() {
        return runId;
    }

    public Instant getAt() {
        return at;
    }
}
