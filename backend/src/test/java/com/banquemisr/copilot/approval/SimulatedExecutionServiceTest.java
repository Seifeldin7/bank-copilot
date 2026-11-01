package com.banquemisr.copilot.approval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.banquemisr.copilot.common.ApiException;
import com.banquemisr.copilot.identity.AppUser;
import com.banquemisr.copilot.servicerequest.ExecutionRecord;
import com.banquemisr.copilot.servicerequest.ExecutionRecordRepository;
import com.banquemisr.copilot.servicerequest.RequestFixtures;
import com.banquemisr.copilot.servicerequest.RequestHistory.Action;
import com.banquemisr.copilot.servicerequest.RequestHistoryRecorder;
import com.banquemisr.copilot.servicerequest.RequestStatus;
import com.banquemisr.copilot.servicerequest.ServiceRequest;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class SimulatedExecutionServiceTest {

    private final ExecutionRecordRepository executions = mock(ExecutionRecordRepository.class);
    private final RequestHistoryRecorder recorder = mock(RequestHistoryRecorder.class);
    private final SimulatedExecutionService service = new SimulatedExecutionService(executions, recorder, RequestFixtures.CLOCK);
    private final AppUser supervisor = RequestFixtures.supervisor("supervisor1");

    @Test
    void executesAnApprovedRequestOnce() {
        ServiceRequest request = RequestFixtures.approved(RequestFixtures.employee("employee1"));
        when(executions.findByRequestId(request.getId())).thenReturn(Optional.empty());
        when(executions.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));

        SimulatedExecutionService.Outcome outcome = service.execute(request, supervisor);

        assertThat(outcome.alreadyExecuted()).isFalse();
        assertThat(outcome.record().getSimulatedAction()).isEqualTo("Simulated replacement card order placed");
        assertThat(outcome.record().getResult()).containsEntry("cardOrderRef", "CO-000101").containsEntry("collectionBranch", "CAI-012");
        assertThat(request.getStatus()).isEqualTo(RequestStatus.EXECUTED);
        verify(recorder).record(eq(request), eq(Action.EXECUTED), eq(supervisor), eq(RequestStatus.APPROVED),
                eq(RequestStatus.EXECUTED), anyList(), any());
    }

    @Test
    void aSecondExecutionReturnsTheExistingRecordAndLogsTheRetry() {
        ServiceRequest request = RequestFixtures.approved(RequestFixtures.employee("employee1"));
        request.moveTo(RequestStatus.EXECUTED);
        ExecutionRecord existing = new ExecutionRecord(request, "Simulated replacement card order placed", Map.of(), supervisor);
        when(executions.findByRequestId(request.getId())).thenReturn(Optional.of(existing));

        SimulatedExecutionService.Outcome outcome = service.execute(request, supervisor);

        assertThat(outcome.alreadyExecuted()).isTrue();
        assertThat(outcome.record()).isSameAs(existing);
        verify(executions, never()).saveAndFlush(any());
        verify(recorder).record(eq(request), eq(Action.EXECUTION_RETRY), eq(supervisor), eq(RequestStatus.EXECUTED),
                eq(RequestStatus.EXECUTED), anyList(), anyString());
    }

    @Test
    void aRequestThatIsNotApprovedIsNeverExecuted() {
        ServiceRequest pending = RequestFixtures.pending(RequestFixtures.employee("employee1"));
        when(executions.findByRequestId(pending.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.execute(pending, supervisor))
                .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.status()).isEqualTo(HttpStatus.CONFLICT));
        verify(executions, never()).saveAndFlush(any());
    }
}
