package com.banquemisr.copilot.assistant;

import com.banquemisr.copilot.assistant.CopilotDtos.AskRequest;
import com.banquemisr.copilot.assistant.CopilotDtos.AskResponse;
import com.banquemisr.copilot.assistant.CopilotDtos.PrepareRequest;
import com.banquemisr.copilot.assistant.CopilotDtos.PreparedDraftResponse;
import com.banquemisr.copilot.identity.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/copilot")
public class CopilotController {

    private final CopilotOrchestrator orchestrator;
    private final DraftPreparationService drafts;
    private final CurrentUser currentUser;

    public CopilotController(CopilotOrchestrator orchestrator, DraftPreparationService drafts, CurrentUser currentUser) {
        this.orchestrator = orchestrator;
        this.drafts = drafts;
        this.currentUser = currentUser;
    }

    @PostMapping("/ask")
    public AskResponse ask(@Valid @RequestBody AskRequest request, @AuthenticationPrincipal Jwt jwt) {
        return orchestrator.ask(currentUser.from(jwt), request.question());
    }

    @PostMapping("/prepare-request")
    public PreparedDraftResponse prepare(@Valid @RequestBody PrepareRequest request, @AuthenticationPrincipal Jwt jwt) {
        return drafts.prepare(currentUser.from(jwt), request.runId());
    }
}
