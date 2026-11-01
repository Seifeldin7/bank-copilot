package com.banquemisr.copilot.servicerequest;

import com.banquemisr.copilot.identity.AppUser;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "execution_record")
public class ExecutionRecord {

    public enum Outcome { SUCCEEDED, FAILED }

    @Id
    private UUID id;

    private String reference;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "request_id")
    private ServiceRequest request;

    @Enumerated(EnumType.STRING)
    private Outcome outcome;

    private String simulatedAction;

    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, String> result;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "executed_by")
    private AppUser executedBy;

    private Instant executedAt;

    protected ExecutionRecord() {
    }

    public ExecutionRecord(ServiceRequest request, String simulatedAction, Map<String, String> result, AppUser executedBy) {
        this.id = UUID.randomUUID();
        this.reference = "EX-" + id.toString().substring(0, 8).toUpperCase();
        this.request = request;
        this.outcome = Outcome.SUCCEEDED;
        this.simulatedAction = simulatedAction;
        this.result = Map.copyOf(result);
        this.executedBy = executedBy;
        this.executedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getReference() {
        return reference;
    }

    public ServiceRequest getRequest() {
        return request;
    }

    public Outcome getOutcome() {
        return outcome;
    }

    public String getSimulatedAction() {
        return simulatedAction;
    }

    public Map<String, String> getResult() {
        return result;
    }

    public AppUser getExecutedBy() {
        return executedBy;
    }

    public Instant getExecutedAt() {
        return executedAt;
    }
}
