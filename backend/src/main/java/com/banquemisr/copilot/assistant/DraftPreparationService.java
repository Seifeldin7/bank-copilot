package com.banquemisr.copilot.assistant;

import com.banquemisr.copilot.assistant.CopilotDtos.AgentName;
import com.banquemisr.copilot.assistant.CopilotDtos.PreparedDraftResponse;
import com.banquemisr.copilot.assistant.StepRunner.StepFailure;
import com.banquemisr.copilot.assistant.agent.AgentMessages.PreparationRequest;
import com.banquemisr.copilot.assistant.agent.AgentMessages.RequestDraft;
import com.banquemisr.copilot.assistant.agent.AgentResult;
import com.banquemisr.copilot.assistant.agent.RequestPreparationAgent;
import com.banquemisr.copilot.assistant.run.CopilotRun;
import com.banquemisr.copilot.assistant.run.RunRecorder;
import com.banquemisr.copilot.common.ApiException;
import com.banquemisr.copilot.common.CopilotProperties;
import com.banquemisr.copilot.identity.AppUser;
import com.banquemisr.copilot.servicerequest.RequestCategory;
import com.banquemisr.copilot.servicerequest.RequestDtos.CreateRequest;
import com.banquemisr.copilot.servicerequest.RequestDtos.RequestView;
import com.banquemisr.copilot.servicerequest.ServiceRequestRepository;
import com.banquemisr.copilot.servicerequest.ServiceRequestService;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class DraftPreparationService {

    private final RunRecorder recorder;
    private final RequestPreparationAgent preparationAgent;
    private final StepRunner stepRunner;
    private final ServiceRequestService serviceRequests;
    private final ServiceRequestRepository requests;
    private final CopilotProperties.Agents settings;

    public DraftPreparationService(RunRecorder recorder, RequestPreparationAgent preparationAgent, StepRunner stepRunner,
                                   ServiceRequestService serviceRequests, ServiceRequestRepository requests,
                                   CopilotProperties properties) {
        this.recorder = recorder;
        this.preparationAgent = preparationAgent;
        this.stepRunner = stepRunner;
        this.serviceRequests = serviceRequests;
        this.requests = requests;
        this.settings = properties.agents();
    }

    public PreparedDraftResponse prepare(AppUser user, String runId) {
        CopilotRun run = recorder.find(runId)
                .filter(found -> found.isOwnedBy(user))
                .orElseThrow(() -> ApiException.notFound("Copilot run not found."));
        if (run.isRefused()) {
            throw ApiException.conflict("A service request cannot be prepared from a refused answer.");
        }
        RequestCategory category = run.getSuggestedCategory();
        if (category == null) {
            throw ApiException.unprocessable("No service request category applies to this answer.", List.of());
        }
        if (requests.existsBySourceRunId(runId)) {
            throw ApiException.conflict("A draft was already prepared from this answer.");
        }

        RunLog runLog = new RunLog(recorder.stepCount(runId) + 1);
        Instant started = Instant.now();
        AgentResult<RequestDraft> result;
        try {
            result = stepRunner.run(() -> preparationAgent.run(
                    new PreparationRequest(run.getQuestion(), run.getAnswer(), category, user.getBranchCode())),
                    settings.preparationTimeout());
        } catch (StepFailure failure) {
            runLog.failed(AgentName.REQUEST_PREPARATION, failure.status(), failure.getMessage(), started);
            recorder.appendSteps(runId, runLog);
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "The copilot could not prepare a draft right now. Please try again.", List.of());
        }
        runLog.succeeded(AgentName.REQUEST_PREPARATION, result.summary(), result.iterations(), started, result.modelCalls());

        RequestDraft draft = result.output();
        RequestView created = serviceRequests.create(user,
                new CreateRequest(category, draft.title(), draft.description(), draft.payload()),
                runId, "Draft prepared by the Request Preparation Agent");
        recorder.appendSteps(runId, runLog);
        return new PreparedDraftResponse(created, draft.missingFields());
    }
}
