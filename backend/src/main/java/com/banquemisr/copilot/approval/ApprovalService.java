package com.banquemisr.copilot.approval;

import com.banquemisr.copilot.approval.ApprovalDtos.ApproveRequest;
import com.banquemisr.copilot.approval.ApprovalDtos.EditApproveRequest;
import com.banquemisr.copilot.approval.ApprovalDtos.ExecuteResponse;
import com.banquemisr.copilot.approval.ApprovalDtos.RejectRequest;
import com.banquemisr.copilot.common.ApiException;
import com.banquemisr.copilot.common.FieldError;
import com.banquemisr.copilot.identity.AppUser;
import com.banquemisr.copilot.servicerequest.CategoryValidator;
import com.banquemisr.copilot.servicerequest.FieldChange;
import com.banquemisr.copilot.servicerequest.RequestAccessPolicy;
import com.banquemisr.copilot.servicerequest.RequestDiff;
import com.banquemisr.copilot.servicerequest.RequestDtos.ExecutionView;
import com.banquemisr.copilot.servicerequest.RequestDtos.RequestView;
import com.banquemisr.copilot.servicerequest.RequestHistory.Action;
import com.banquemisr.copilot.servicerequest.RequestHistoryRecorder;
import com.banquemisr.copilot.servicerequest.RequestStatus;
import com.banquemisr.copilot.servicerequest.ServiceRequest;
import com.banquemisr.copilot.servicerequest.ServiceRequest.Decision;
import com.banquemisr.copilot.servicerequest.ServiceRequestRepository;
import com.banquemisr.copilot.servicerequest.ValidationResult;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ApprovalService {

    private static final int MIN_REASON_LENGTH = 10;

    private final ServiceRequestRepository requests;
    private final CategoryValidator validator;
    private final RequestHistoryRecorder recorder;
    private final SimulatedExecutionService execution;

    public ApprovalService(ServiceRequestRepository requests, CategoryValidator validator, RequestHistoryRecorder recorder,
                           SimulatedExecutionService execution) {
        this.requests = requests;
        this.validator = validator;
        this.recorder = recorder;
        this.execution = execution;
    }

    @Transactional(readOnly = true)
    public List<RequestView> queue() {
        return requests.findQueue().stream().map(request -> RequestView.of(request, null)).toList();
    }

    public RequestView approve(AppUser supervisor, UUID id, ApproveRequest body) {
        ServiceRequest request = loadForDecision(supervisor, id, body.version());
        ValidationResult validation = validator.validate(request.getTitle(), request.getDescription(), request.getPayload());
        if (!validation.valid()) {
            throw ApiException.unprocessable("The request no longer passes validation. Use Edit & approve or reject it.",
                    validation.errors());
        }
        String comment = body.comment() == null || body.comment().isBlank() ? null : body.comment().trim();

        request.updateValidation(validation);
        request.moveTo(RequestStatus.APPROVED);
        request.recordDecision(Decision.APPROVED, supervisor, comment, List.of());
        recorder.record(request, Action.APPROVED, supervisor, RequestStatus.PENDING_APPROVAL, RequestStatus.APPROVED, List.of(), comment);
        return executeAndView(request, supervisor);
    }

    public RequestView reject(AppUser supervisor, UUID id, RejectRequest body) {
        String reason = requireReason(body.reason(), "Give a reason of at least 10 characters.");
        ServiceRequest request = loadForDecision(supervisor, id, body.version());

        request.moveTo(RequestStatus.REJECTED);
        request.recordDecision(Decision.REJECTED, supervisor, reason, List.of());
        recorder.record(request, Action.REJECTED, supervisor, RequestStatus.PENDING_APPROVAL, RequestStatus.REJECTED, List.of(), reason);
        return RequestView.of(requests.saveAndFlush(request), null);
    }

    public RequestView editAndApprove(AppUser supervisor, UUID id, EditApproveRequest body) {
        String reason = requireReason(body.reason(), "Explain the change in at least 10 characters.");
        ServiceRequest request = loadForDecision(supervisor, id, body.version());
        if (body.payload().category() != request.getCategory()) {
            throw ApiException.badRequest("Payload does not match the category.",
                    new FieldError("payload", "Payload category must match the request category."));
        }
        List<FieldChange> changes = RequestDiff.between(
                new RequestDiff.Content(request.getTitle(), request.getDescription(), request.getPayload()),
                new RequestDiff.Content(body.title(), body.description(), body.payload()));
        if (changes.isEmpty()) {
            throw ApiException.badRequest("No changes were made. Use Approve instead.");
        }
        ValidationResult validation = validator.validate(body.title(), body.description(), body.payload());
        if (!validation.valid()) {
            throw ApiException.unprocessable("The edited request has validation errors.", validation.errors());
        }

        request.edit(body.title(), body.description(), body.payload(), validation);
        request.moveTo(RequestStatus.APPROVED);
        request.recordDecision(Decision.EDITED_APPROVED, supervisor, reason, changes);
        recorder.record(request, Action.EDITED_AND_APPROVED, supervisor, RequestStatus.PENDING_APPROVAL, RequestStatus.APPROVED, changes, reason);
        return executeAndView(request, supervisor);
    }

    public ExecuteResponse execute(AppUser supervisor, UUID id) {
        if (!supervisor.isSupervisor()) {
            throw ApiException.forbidden("This action requires the SUPERVISOR role.");
        }
        ServiceRequest request = requests.findForUpdate(id).orElseThrow(RequestAccessPolicy::notFoundError);
        SimulatedExecutionService.Outcome outcome = execution.execute(request, supervisor);
        return new ExecuteResponse(ExecutionView.of(outcome.record()), outcome.alreadyExecuted());
    }

    private ServiceRequest loadForDecision(AppUser supervisor, UUID id, int version) {
        ServiceRequest request = requests.findForUpdate(id).orElseThrow(RequestAccessPolicy::notFoundError);
        RequestAccessPolicy.requireCanDecide(supervisor, request);
        if (request.getStatus() != RequestStatus.PENDING_APPROVAL) {
            throw ApiException.conflict("Only requests waiting for approval can be decided. This one is " + request.getStatus() + ".");
        }
        request.requireVersion(version);
        return request;
    }

    private RequestView executeAndView(ServiceRequest request, AppUser supervisor) {
        SimulatedExecutionService.Outcome outcome = execution.execute(request, supervisor);
        return RequestView.of(requests.saveAndFlush(request), outcome.record());
    }

    private static String requireReason(String reason, String message) {
        if (reason == null || reason.trim().length() < MIN_REASON_LENGTH) {
            throw ApiException.badRequest("A reason is required.", new FieldError("reason", message));
        }
        return reason.trim();
    }
}
