package com.banquemisr.copilot.audit;

import com.banquemisr.copilot.identity.AppUser;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_event")
public class AuditEvent {

    @Id
    private UUID id;

    private String action;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id")
    private AppUser actor;

    private String resourceType;
    private String resourceId;
    private String detail;
    private String runId;
    private Instant at;

    protected AuditEvent() {
    }

    public AuditEvent(String action, AppUser actor, String resourceType, String resourceId, String detail, String runId) {
        this.id = UUID.randomUUID();
        this.action = action;
        this.actor = actor;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.detail = detail;
        this.runId = runId;
        this.at = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getAction() {
        return action;
    }

    public AppUser getActor() {
        return actor;
    }

    public String getResourceType() {
        return resourceType;
    }

    public String getResourceId() {
        return resourceId;
    }

    public String getDetail() {
        return detail;
    }

    public String getRunId() {
        return runId;
    }

    public Instant getAt() {
        return at;
    }
}
