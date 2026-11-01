package com.banquemisr.copilot.servicerequest;

import com.banquemisr.copilot.common.ApiException;
import com.banquemisr.copilot.common.FieldError;
import com.banquemisr.copilot.identity.AppUser;
import com.banquemisr.copilot.servicerequest.RequestDtos.CreateRequest;
import com.banquemisr.copilot.servicerequest.RequestDtos.HistoryView;
import com.banquemisr.copilot.servicerequest.RequestDtos.RequestView;
import com.banquemisr.copilot.servicerequest.RequestDtos.UpdateRequest;
import com.banquemisr.copilot.servicerequest.RequestHistory.Action;
import java.time.Clock;
import java.time.Year;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ServiceRequestService {

    private final ServiceRequestRepository requests;
    private final RequestHistoryRepository history;
    private final ExecutionRecordRepository executions;
    private final CategoryValidator validator;
    private final RequestHistoryRecorder recorder;
    private final Clock clock;

    public ServiceRequestService(ServiceRequestRepository requests, RequestHistoryRepository history,
                                 ExecutionRecordRepository executions, CategoryValidator validator,
                                 RequestHistoryRecorder recorder, Clock clock) {
        this.requests = requests;
        this.history = history;
        this.executions = executions;
        this.validator = validator;
        this.recorder = recorder;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<RequestView> list(AppUser user, boolean all, RequestStatus status, RequestCategory category, UUID requesterId) {
        if (all && !user.isSupervisor()) {
            throw ApiException.forbidden("Only supervisors can list all requests.");
        }
        List<ServiceRequest> found = requests.search(all ? requesterId : user.getId(), status, category);
        return views(found);
    }

    @Transactional(readOnly = true)
    public RequestView get(AppUser user, UUID id) {
        ServiceRequest request = requests.findDetailed(id).orElseThrow(RequestAccessPolicy::notFoundError);
        RequestAccessPolicy.requireVisible(user, request);
        return RequestView.of(request, executions.findByRequestId(id).orElse(null));
    }

    @Transactional(readOnly = true)
    public List<HistoryView> history(AppUser user, UUID id) {
        ServiceRequest request = requests.findById(id).orElseThrow(RequestAccessPolicy::notFoundError);
        RequestAccessPolicy.requireVisible(user, request);
        return history.findTimeline(id).stream().map(HistoryView::of).toList();
    }

    public RequestView create(AppUser user, CreateRequest body, String sourceRunId, String reason) {
        requireMatchingCategory(body.category(), body.payload());
        String reference = "SR-%d-%06d".formatted(Year.now(clock).getValue(), requests.nextNumber());
        ValidationResult validation = validator.validate(body.title(), body.description(), body.payload());
        ServiceRequest request = new ServiceRequest(reference, user, body.title(), body.description(), body.payload(),
                validation, sourceRunId);
        requests.save(request);
        recorder.record(request, Action.CREATED, user, null, RequestStatus.DRAFT, List.of(), reason);
        return RequestView.of(request, null);
    }

    public RequestView update(AppUser user, UUID id, UpdateRequest body) {
        ServiceRequest request = load(id);
        RequestAccessPolicy.requireOwner(user, request);
        if (request.getStatus() != RequestStatus.DRAFT) {
            throw ApiException.conflict("Only draft requests can be edited.");
        }
        requireMatchingCategory(request.getCategory(), body.payload());
        request.requireVersion(body.version());

        List<FieldChange> changes = RequestDiff.between(
                new RequestDiff.Content(request.getTitle(), request.getDescription(), request.getPayload()),
                new RequestDiff.Content(body.title(), body.description(), body.payload()));
        request.edit(body.title(), body.description(), body.payload(),
                validator.validate(body.title(), body.description(), body.payload()));
        if (!changes.isEmpty()) {
            recorder.record(request, Action.UPDATED, user, RequestStatus.DRAFT, RequestStatus.DRAFT, changes, null);
        }
        return RequestView.of(requests.saveAndFlush(request), null);
    }

    public RequestView submit(AppUser user, UUID id, int version) {
        ServiceRequest request = load(id);
        RequestAccessPolicy.requireOwner(user, request);
        request.requireVersion(version);
        if (request.getStatus() != RequestStatus.DRAFT) {
            throw ApiException.conflict("A request in status " + request.getStatus() + " cannot be submitted.");
        }
        ValidationResult validation = validator.validate(request.getTitle(), request.getDescription(), request.getPayload());
        if (!validation.valid()) {
            throw ApiException.unprocessable("The request has validation errors. Fix them before submitting.", validation.errors());
        }
        request.updateValidation(validation);
        request.moveTo(RequestStatus.PENDING_APPROVAL);
        recorder.record(request, Action.SUBMITTED, user, RequestStatus.DRAFT, RequestStatus.PENDING_APPROVAL, List.of(), null);
        return RequestView.of(requests.saveAndFlush(request), null);
    }

    private ServiceRequest load(UUID id) {
        return requests.findForUpdate(id).orElseThrow(RequestAccessPolicy::notFoundError);
    }

    static void requireMatchingCategory(RequestCategory category, RequestPayload payload) {
        if (payload.category() != category) {
            throw ApiException.badRequest("Payload does not match the category.",
                    new FieldError("payload", "Payload category must match the request category."));
        }
    }

    private List<RequestView> views(List<ServiceRequest> found) {
        if (found.isEmpty()) {
            return List.of();
        }
        Map<UUID, ExecutionRecord> byRequest = executions.findByRequestIds(found.stream().map(ServiceRequest::getId).toList())
                .stream()
                .collect(Collectors.toMap(record -> record.getRequest().getId(), Function.identity()));
        return found.stream().map(request -> RequestView.of(request, byRequest.get(request.getId()))).toList();
    }
}
