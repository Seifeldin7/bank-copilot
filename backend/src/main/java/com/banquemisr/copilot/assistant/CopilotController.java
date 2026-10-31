package com.banquemisr.copilot.assistant;

import com.banquemisr.copilot.assistant.CopilotDtos.AskRequest;
import com.banquemisr.copilot.assistant.CopilotDtos.AskResponse;
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

    private final CopilotService copilotService;
    private final CurrentUser currentUser;

    public CopilotController(CopilotService copilotService, CurrentUser currentUser) {
        this.copilotService = copilotService;
        this.currentUser = currentUser;
    }

    @PostMapping("/ask")
    public AskResponse ask(@Valid @RequestBody AskRequest request, @AuthenticationPrincipal Jwt jwt) {
        return copilotService.ask(currentUser.from(jwt), request.question());
    }
}
