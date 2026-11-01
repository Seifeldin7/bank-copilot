package com.banquemisr.copilot.servicerequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.banquemisr.copilot.common.ApiException;
import com.banquemisr.copilot.identity.AppUser;
import com.banquemisr.copilot.servicerequest.RequestDtos.CreateRequest;
import com.banquemisr.copilot.servicerequest.RequestDtos.RequestView;
import com.banquemisr.copilot.servicerequest.RequestDtos.UpdateRequest;
import com.banquemisr.copilot.servicerequest.RequestHistory.Action;
import com.banquemisr.copilot.servicerequest.RequestPayload.CardService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ServiceRequestServiceTest {

    private final ServiceRequestRepository requests = mock(ServiceRequestRepository.class);
    private final RequestHistoryRecorder recorder = mock(RequestHistoryRecorder.class);
    private final ServiceRequestService service = new ServiceRequestService(requests, mock(RequestHistoryRepository.class),
            mock(ExecutionRecordRepository.class), RequestFixtures.validator(), recorder, RequestFixtures.CLOCK);
    private final AppUser owner = RequestFixtures.employee("employee1");

    @Test
    void createsADraftWithAReferenceAndItsValidation() {
        when(requests.nextNumber()).thenReturn(105L);

        RequestView created = service.create(owner, new CreateRequest(RequestCategory.CARD_SERVICE, "Replace damaged card",
                "The chip on the card is damaged.", RequestFixtures.card()), "run-1", null);

        assertThat(created.reference()).isEqualTo("SR-2026-000105");
        assertThat(created.status()).isEqualTo(RequestStatus.DRAFT);
        assertThat(created.validation().valid()).isTrue();
        assertThat(created.sourceRunId()).isEqualTo("run-1");
        verify(recorder).record(any(), eq(Action.CREATED), eq(owner), isNull(), eq(RequestStatus.DRAFT), anyList(), isNull());
    }

    @Test
    void rejectsAPayloadFromAnotherCategory() {
        CreateRequest body = new CreateRequest(RequestCategory.DIGITAL_BANKING, "Replace damaged card",
                "The chip on the card is damaged.", RequestFixtures.card());

        assertStatus(() -> service.create(owner, body, null, null), HttpStatus.BAD_REQUEST);
    }

    @Test
    void editingRecordsTheChangedFields() {
        ServiceRequest draft = stored(RequestFixtures.draft(owner));
        CardService corrected = new CardService("CUS-104233", "4822", RequestFixtures.card().issueType(), "CAI-012", "");

        service.update(owner, draft.getId(), new UpdateRequest(draft.getTitle(), draft.getDescription(), corrected, draft.getVersion()));

        assertThat(draft.getPayload()).isEqualTo(corrected);
        verify(recorder).record(eq(draft), eq(Action.UPDATED), eq(owner), eq(RequestStatus.DRAFT), eq(RequestStatus.DRAFT),
                eq(List.of(new FieldChange("payload.cardLast4", "4821", "4822"))), isNull());
    }

    @Test
    void onlyDraftsCanBeEdited() {
        ServiceRequest pending = stored(RequestFixtures.pending(owner));
        UpdateRequest body = new UpdateRequest("New title here", pending.getDescription(), pending.getPayload(), pending.getVersion());

        assertStatus(() -> service.update(owner, pending.getId(), body), HttpStatus.CONFLICT);
    }

    @Test
    void submittingAnInvalidDraftFailsWithFieldErrorsAndStaysADraft() {
        ServiceRequest draft = stored(RequestFixtures.draft(owner, new CardService("CUS-1", "4821", null, "CAI-012", "")));

        assertThatThrownBy(() -> service.submit(owner, draft.getId(), draft.getVersion()))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.status()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
                    assertThat(ex.errors()).extracting(error -> error.field())
                            .containsExactly("payload.customerReference", "payload.issueType");
                });
        assertThat(draft.getStatus()).isEqualTo(RequestStatus.DRAFT);
        verify(recorder, never()).record(any(), eq(Action.SUBMITTED), any(), any(), any(), anyList(), any());
    }

    @Test
    void submittingAValidDraftSendsItForApproval() {
        ServiceRequest draft = stored(RequestFixtures.draft(owner));

        RequestView submitted = service.submit(owner, draft.getId(), draft.getVersion());

        assertThat(submitted.status()).isEqualTo(RequestStatus.PENDING_APPROVAL);
        verify(recorder).record(draft, Action.SUBMITTED, owner, RequestStatus.DRAFT, RequestStatus.PENDING_APPROVAL, List.of(), null);
    }

    @Test
    void onlyTheOwnerCanSubmit() {
        ServiceRequest draft = stored(RequestFixtures.draft(owner));

        assertStatus(() -> service.submit(RequestFixtures.employee("employee2"), draft.getId(), draft.getVersion()), HttpStatus.NOT_FOUND);
        assertStatus(() -> service.submit(RequestFixtures.supervisor("supervisor1"), draft.getId(), draft.getVersion()), HttpStatus.FORBIDDEN);
    }

    @Test
    void aStaleVersionIsAConflict() {
        ServiceRequest draft = stored(RequestFixtures.draft(owner));

        assertStatus(() -> service.submit(owner, draft.getId(), draft.getVersion() + 1), HttpStatus.CONFLICT);
    }

    @Test
    void employeesCannotListEveryonesRequests() {
        assertStatus(() -> service.list(owner, true, null, null, null), HttpStatus.FORBIDDEN);
    }

    private ServiceRequest stored(ServiceRequest request) {
        when(requests.findForUpdate(request.getId())).thenReturn(Optional.of(request));
        when(requests.saveAndFlush(request)).thenReturn(request);
        return request;
    }

    private static void assertStatus(Runnable action, HttpStatus status) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.status()).isEqualTo(status));
    }
}
