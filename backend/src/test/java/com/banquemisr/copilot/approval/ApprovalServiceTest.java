package com.banquemisr.copilot.approval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.banquemisr.copilot.approval.ApprovalDtos.ApproveRequest;
import com.banquemisr.copilot.approval.ApprovalDtos.EditApproveRequest;
import com.banquemisr.copilot.approval.ApprovalDtos.RejectRequest;
import com.banquemisr.copilot.common.ApiException;
import com.banquemisr.copilot.identity.AppUser;
import com.banquemisr.copilot.servicerequest.ExecutionRecord;
import com.banquemisr.copilot.servicerequest.FieldChange;
import com.banquemisr.copilot.servicerequest.RequestDtos.RequestView;
import com.banquemisr.copilot.servicerequest.RequestFixtures;
import com.banquemisr.copilot.servicerequest.RequestHistory.Action;
import com.banquemisr.copilot.servicerequest.RequestHistoryRecorder;
import com.banquemisr.copilot.servicerequest.RequestPayload.CardService;
import com.banquemisr.copilot.servicerequest.RequestStatus;
import com.banquemisr.copilot.servicerequest.ServiceRequest;
import com.banquemisr.copilot.servicerequest.ServiceRequestRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ApprovalServiceTest {

    private final ServiceRequestRepository requests = mock(ServiceRequestRepository.class);
    private final RequestHistoryRecorder recorder = mock(RequestHistoryRecorder.class);
    private final SimulatedExecutionService execution = mock(SimulatedExecutionService.class);
    private final ApprovalService service = new ApprovalService(requests, RequestFixtures.validator(), recorder, execution);
    private final AppUser employee = RequestFixtures.employee("employee1");
    private final AppUser supervisor = RequestFixtures.supervisor("supervisor1");

    @Test
    void approvingRecordsTheDecisionAndExecutes() {
        ServiceRequest request = stored(RequestFixtures.pending(employee));
        executionSucceeds(request);

        RequestView view = service.approve(supervisor, request.getId(), new ApproveRequest(" Verified ID ", request.getVersion()));

        assertThat(request.getStatus()).isEqualTo(RequestStatus.APPROVED);
        assertThat(view.decision().decision()).isEqualTo(ServiceRequest.Decision.APPROVED);
        assertThat(view.decision().reason()).isEqualTo("Verified ID");
        assertThat(view.execution()).isNotNull();
        verify(execution).execute(request, supervisor);
    }

    @Test
    void aSupervisorCannotApproveTheirOwnRequest() {
        ServiceRequest own = stored(RequestFixtures.pending(supervisor));

        assertStatus(() -> service.approve(supervisor, own.getId(), new ApproveRequest(null, own.getVersion())), HttpStatus.FORBIDDEN);
        verifyNoInteractions(execution);
    }

    @Test
    void approvalReValidatesTheRequest() {
        ServiceRequest request = stored(RequestFixtures.draft(employee, new CardService("CUS-1", "4821", null, "CAI-012", "")));
        request.moveTo(RequestStatus.PENDING_APPROVAL);

        assertStatus(() -> service.approve(supervisor, request.getId(), new ApproveRequest(null, request.getVersion())),
                HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(request.getStatus()).isEqualTo(RequestStatus.PENDING_APPROVAL);
    }

    @Test
    void onlyPendingRequestsCanBeDecided() {
        ServiceRequest draft = stored(RequestFixtures.draft(employee));

        assertStatus(() -> service.reject(supervisor, draft.getId(), new RejectRequest("Customer withdrew the request", draft.getVersion())),
                HttpStatus.CONFLICT);
    }

    @Test
    void rejectingNeedsAReasonOfTenCharacters() {
        ServiceRequest request = stored(RequestFixtures.pending(employee));

        assertThatThrownBy(() -> service.reject(supervisor, request.getId(), new RejectRequest("  too short ", request.getVersion())))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.status()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.errors()).extracting(error -> error.field()).containsExactly("reason");
                });

        service.reject(supervisor, request.getId(), new RejectRequest("Customer withdrew the request", request.getVersion()));
        assertThat(request.getStatus()).isEqualTo(RequestStatus.REJECTED);
        assertThat(request.getDecisionReason()).isEqualTo("Customer withdrew the request");
        verifyNoInteractions(execution);
    }

    @Test
    void editAndApproveStoresTheDiffOnTheDecisionAndHistory() {
        ServiceRequest request = stored(RequestFixtures.pending(employee));
        executionSucceeds(request);
        CardService corrected = new CardService("CUS-104233", "4822", RequestFixtures.card().issueType(), "CAI-012", "");

        service.editAndApprove(supervisor, request.getId(), new EditApproveRequest(request.getTitle(), request.getDescription(),
                corrected, "Corrected the card digits", request.getVersion()));

        List<FieldChange> expected = List.of(new FieldChange("payload.cardLast4", "4821", "4822"));
        assertThat(request.getDecision()).isEqualTo(ServiceRequest.Decision.EDITED_APPROVED);
        assertThat(request.getDecisionChanges()).isEqualTo(expected);
        assertThat(request.getPayload()).isEqualTo(corrected);
        verify(recorder).record(request, Action.EDITED_AND_APPROVED, supervisor, RequestStatus.PENDING_APPROVAL,
                RequestStatus.APPROVED, expected, "Corrected the card digits");
    }

    @Test
    void editAndApproveWithoutChangesIsRejected() {
        ServiceRequest request = stored(RequestFixtures.pending(employee));

        assertStatus(() -> service.editAndApprove(supervisor, request.getId(), new EditApproveRequest(request.getTitle(),
                request.getDescription(), request.getPayload(), "Looks fine to me", request.getVersion())), HttpStatus.BAD_REQUEST);
    }

    private ServiceRequest stored(ServiceRequest request) {
        when(requests.findForUpdate(request.getId())).thenReturn(Optional.of(request));
        when(requests.saveAndFlush(request)).thenReturn(request);
        return request;
    }

    private void executionSucceeds(ServiceRequest request) {
        ExecutionRecord record = new ExecutionRecord(request, "Simulated replacement card order placed", Map.of(), supervisor);
        when(execution.execute(eq(request), any())).thenReturn(new SimulatedExecutionService.Outcome(record, false));
    }

    private static void assertStatus(Runnable action, HttpStatus status) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.status()).isEqualTo(status));
    }
}
