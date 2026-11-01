package com.banquemisr.copilot.servicerequest;

import com.banquemisr.copilot.identity.UserDtos.UserRef;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class RequestDtos {

    private RequestDtos() {
    }

    public record CreateRequest(
            @NotNull(message = "Select a valid category.") RequestCategory category,
            @NotNull(message = "Title is required.") @Size(max = 120, message = "Title must be at most 120 characters.") String title,
            @NotNull(message = "Description is required.") @Size(max = 1000, message = "Description must be at most 1000 characters.") String description,
            @NotNull(message = "Payload is required.") @Valid RequestPayload payload) {
    }

    public record UpdateRequest(
            @NotNull(message = "Title is required.") @Size(max = 120, message = "Title must be at most 120 characters.") String title,
            @NotNull(message = "Description is required.") @Size(max = 1000, message = "Description must be at most 1000 characters.") String description,
            @NotNull(message = "Payload is required.") @Valid RequestPayload payload,
            @NotNull(message = "Version is required.") Integer version) {
    }

    public record SubmitRequest(@NotNull(message = "Version is required.") Integer version) {
    }

    public record DecisionView(ServiceRequest.Decision decision, UserRef supervisor, String reason,
                               List<FieldChange> changes, Instant decidedAt) {
    }

    public record ExecutionView(String id, ExecutionRecord.Outcome outcome, String simulatedAction,
                                Map<String, String> result, UserRef executedBy, Instant executedAt) {

        public static ExecutionView of(ExecutionRecord record) {
            return record == null ? null : new ExecutionView(record.getReference(), record.getOutcome(),
                    record.getSimulatedAction(), record.getResult(), UserRef.of(record.getExecutedBy()),
                    record.getExecutedAt());
        }
    }

    public record RequestView(UUID id, String reference, RequestCategory category, RequestStatus status, String title,
                              String description, RequestPayload payload, UserRef requester, ValidationResult validation,
                              DecisionView decision, ExecutionView execution, String sourceRunId, int version,
                              Instant createdAt, Instant updatedAt, Instant submittedAt, Instant decidedAt,
                              Instant executedAt) {

        public static RequestView of(ServiceRequest request, ExecutionRecord execution) {
            DecisionView decision = request.getDecision() == null ? null : new DecisionView(request.getDecision(),
                    UserRef.of(request.getDecidedBy()), request.getDecisionReason(), request.getDecisionChanges(),
                    request.getDecidedAt());
            return new RequestView(request.getId(), request.getReference(), request.getCategory(), request.getStatus(),
                    request.getTitle(), request.getDescription(), request.getPayload(), UserRef.of(request.getRequester()),
                    request.getValidation(), decision, ExecutionView.of(execution), request.getSourceRunId(),
                    request.getVersion(), request.getCreatedAt(), request.getUpdatedAt(), request.getSubmittedAt(),
                    request.getDecidedAt(), request.getExecutedAt());
        }
    }

    public record HistoryView(UUID id, RequestHistory.Action action, UserRef actor, RequestStatus fromStatus,
                              RequestStatus toStatus, List<FieldChange> changes, String reason, String runId,
                              Instant at) {

        public static HistoryView of(RequestHistory entry) {
            return new HistoryView(entry.getId(), entry.getAction(), UserRef.of(entry.getActor()), entry.getFromStatus(),
                    entry.getToStatus(), entry.getChanges(), entry.getReason(), entry.getRunId(), entry.getAt());
        }
    }
}
