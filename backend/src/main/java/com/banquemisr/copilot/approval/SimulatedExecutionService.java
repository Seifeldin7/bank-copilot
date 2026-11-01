package com.banquemisr.copilot.approval;

import com.banquemisr.copilot.common.ApiException;
import com.banquemisr.copilot.identity.AppUser;
import com.banquemisr.copilot.servicerequest.ExecutionRecord;
import com.banquemisr.copilot.servicerequest.ExecutionRecordRepository;
import com.banquemisr.copilot.servicerequest.RequestHistory.Action;
import com.banquemisr.copilot.servicerequest.RequestHistoryRecorder;
import com.banquemisr.copilot.servicerequest.RequestStatus;
import com.banquemisr.copilot.servicerequest.ServiceRequest;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SimulatedExecutionService {

    public record Outcome(ExecutionRecord record, boolean alreadyExecuted) {
    }

    private final ExecutionRecordRepository executions;
    private final RequestHistoryRecorder recorder;
    private final Clock clock;

    public SimulatedExecutionService(ExecutionRecordRepository executions, RequestHistoryRecorder recorder, Clock clock) {
        this.executions = executions;
        this.recorder = recorder;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Outcome execute(ServiceRequest lockedRequest, AppUser actor) {
        Optional<ExecutionRecord> existing = executions.findByRequestId(lockedRequest.getId());
        if (existing.isPresent()) {
            recorder.record(lockedRequest, Action.EXECUTION_RETRY, actor, RequestStatus.EXECUTED, RequestStatus.EXECUTED,
                    List.of(), "Repeated execution blocked; returned existing record " + existing.get().getReference());
            return new Outcome(existing.get(), true);
        }
        if (lockedRequest.getStatus() != RequestStatus.APPROVED) {
            throw ApiException.conflict("Only approved requests can be executed.");
        }

        SimulatedActions.Action action = SimulatedActions.run(lockedRequest, LocalDate.now(clock));
        ExecutionRecord record = executions.saveAndFlush(
                new ExecutionRecord(lockedRequest, action.description(), action.result(), actor));
        lockedRequest.moveTo(RequestStatus.EXECUTED);
        recorder.record(lockedRequest, Action.EXECUTED, actor, RequestStatus.APPROVED, RequestStatus.EXECUTED, List.of(), null);
        return new Outcome(record, false);
    }
}
