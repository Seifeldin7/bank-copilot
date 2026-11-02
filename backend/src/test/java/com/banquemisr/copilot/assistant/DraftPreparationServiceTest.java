package com.banquemisr.copilot.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.banquemisr.copilot.assistant.CopilotDtos.AskResponse;
import com.banquemisr.copilot.assistant.CopilotDtos.Mode;
import com.banquemisr.copilot.assistant.CopilotDtos.PreparedDraftResponse;
import com.banquemisr.copilot.assistant.CopilotDtos.UsageTotals;
import com.banquemisr.copilot.assistant.agent.AgentMessages.RequestDraft;
import com.banquemisr.copilot.assistant.agent.AgentResult;
import com.banquemisr.copilot.assistant.agent.RequestPreparationAgent;
import com.banquemisr.copilot.assistant.run.CopilotRun;
import com.banquemisr.copilot.assistant.run.RunRecorder;
import com.banquemisr.copilot.common.ApiException;
import com.banquemisr.copilot.identity.AppUser;
import com.banquemisr.copilot.servicerequest.RequestCategory;
import com.banquemisr.copilot.servicerequest.RequestDtos.CreateRequest;
import com.banquemisr.copilot.servicerequest.RequestDtos.RequestView;
import com.banquemisr.copilot.servicerequest.RequestFixtures;
import com.banquemisr.copilot.servicerequest.ServiceRequestRepository;
import com.banquemisr.copilot.servicerequest.ServiceRequestService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class DraftPreparationServiceTest {

    private final RunRecorder recorder = mock(RunRecorder.class);
    private final RequestPreparationAgent agent = mock(RequestPreparationAgent.class);
    private final ServiceRequestService serviceRequests = mock(ServiceRequestService.class);
    private final ServiceRequestRepository requests = mock(ServiceRequestRepository.class);
    private final DraftPreparationService service = new DraftPreparationService(recorder, agent, new StepRunner(),
            serviceRequests, requests, AssistantFixtures.properties());
    private final AppUser employee = RequestFixtures.employee("employee1");

    @Test
    void createsADraftLinkedToTheRunAndRecordsTheStep() {
        stored(run(employee, false, RequestCategory.CARD_SERVICE));
        RequestDraft draft = new RequestDraft("Replace lost card", "Customer lost their card.", RequestFixtures.card(), List.of("payload.cardLast4"));
        when(agent.run(any())).thenReturn(new AgentResult<>(draft, "drafted", 1, List.of(AssistantFixtures.reply("{}"))));
        RequestView created = RequestView.of(RequestFixtures.draft(employee), null);
        when(serviceRequests.create(eq(employee), any(CreateRequest.class), eq("run-1"), any())).thenReturn(created);

        PreparedDraftResponse response = service.prepare(employee, "run-1");

        assertThat(response.request()).isSameAs(created);
        assertThat(response.missingFields()).containsExactly("payload.cardLast4");
        verify(recorder).appendSteps(eq("run-1"), any());
    }

    @Test
    void anotherUsersRunIsNotFound() {
        stored(run(RequestFixtures.employee("employee2"), false, RequestCategory.CARD_SERVICE));

        assertStatus(() -> service.prepare(employee, "run-1"), HttpStatus.NOT_FOUND);
    }

    @Test
    void aRefusedAnswerCannotBecomeARequest() {
        stored(run(employee, true, null));

        assertStatus(() -> service.prepare(employee, "run-1"), HttpStatus.CONFLICT);
        verifyNoInteractions(agent, serviceRequests);
    }

    @Test
    void anAnswerWithoutACategoryIsUnprocessable() {
        stored(run(employee, false, null));

        assertStatus(() -> service.prepare(employee, "run-1"), HttpStatus.UNPROCESSABLE_CONTENT);
    }

    @Test
    void onlyOneDraftCanBePreparedPerRun() {
        stored(run(employee, false, RequestCategory.CARD_SERVICE));
        when(requests.existsBySourceRunId("run-1")).thenReturn(true);

        assertStatus(() -> service.prepare(employee, "run-1"), HttpStatus.CONFLICT);
        verifyNoInteractions(agent);
    }

    private void stored(CopilotRun run) {
        when(recorder.find("run-1")).thenReturn(Optional.of(run));
    }

    private static CopilotRun run(AppUser owner, boolean refused, RequestCategory category) {
        AskResponse response = new AskResponse("run-1", "Customer lost their debit card, what now?", refused ? null : "Block it first [1].",
                refused, refused ? "Not covered." : null, Mode.ORCHESTRATED, List.of(), null, category, List.of(),
                new UsageTotals(0, 0, 0, 0), 10, Instant.now());
        return new CopilotRun(owner, response);
    }

    private static void assertStatus(Runnable action, HttpStatus status) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.status()).isEqualTo(status));
    }
}
