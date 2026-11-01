package com.banquemisr.copilot.approval;

import com.banquemisr.copilot.servicerequest.RequestDtos.ExecutionView;
import com.banquemisr.copilot.servicerequest.RequestPayload;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class ApprovalDtos {

    private ApprovalDtos() {
    }

    public record ApproveRequest(
            @Size(max = 500, message = "Comments must be at most 500 characters.") String comment,
            @NotNull(message = "Version is required.") Integer version) {
    }

    public record RejectRequest(
            @Size(max = 1000, message = "Reasons must be at most 1000 characters.") String reason,
            @NotNull(message = "Version is required.") Integer version) {
    }

    public record EditApproveRequest(
            @NotNull(message = "Title is required.") @Size(max = 120, message = "Title must be at most 120 characters.") String title,
            @NotNull(message = "Description is required.") @Size(max = 1000, message = "Description must be at most 1000 characters.") String description,
            @NotNull(message = "Payload is required.") @Valid RequestPayload payload,
            @Size(max = 1000, message = "Reasons must be at most 1000 characters.") String reason,
            @NotNull(message = "Version is required.") Integer version) {
    }

    public record ExecuteResponse(ExecutionView execution, boolean alreadyExecuted) {
    }
}
