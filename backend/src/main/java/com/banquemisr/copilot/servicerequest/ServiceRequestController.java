package com.banquemisr.copilot.servicerequest;

import com.banquemisr.copilot.common.ApiException;
import com.banquemisr.copilot.common.FieldError;
import com.banquemisr.copilot.identity.CurrentUser;
import com.banquemisr.copilot.servicerequest.RequestDtos.CreateRequest;
import com.banquemisr.copilot.servicerequest.RequestDtos.HistoryView;
import com.banquemisr.copilot.servicerequest.RequestDtos.RequestView;
import com.banquemisr.copilot.servicerequest.RequestDtos.SubmitRequest;
import com.banquemisr.copilot.servicerequest.RequestDtos.UpdateRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/requests")
public class ServiceRequestController {

    private final ServiceRequestService service;
    private final CurrentUser currentUser;

    public ServiceRequestController(ServiceRequestService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @GetMapping
    public List<RequestView> list(@RequestParam(defaultValue = "mine") String scope,
                                  @RequestParam(required = false) RequestStatus status,
                                  @RequestParam(required = false) RequestCategory category,
                                  @RequestParam(required = false) UUID requesterId,
                                  @AuthenticationPrincipal Jwt jwt) {
        if (!scope.equals("mine") && !scope.equals("all")) {
            throw ApiException.badRequest("Unknown scope.", new FieldError("scope", "Use mine or all."));
        }
        return service.list(currentUser.from(jwt), scope.equals("all"), status, category, requesterId);
    }

    @GetMapping("/{id}")
    public RequestView get(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return service.get(currentUser.from(jwt), id);
    }

    @GetMapping("/{id}/history")
    public List<HistoryView> history(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return service.history(currentUser.from(jwt), id);
    }

    @PostMapping
    public RequestView create(@Valid @RequestBody CreateRequest body, @AuthenticationPrincipal Jwt jwt) {
        return service.create(currentUser.from(jwt), body, null, null);
    }

    @PutMapping("/{id}")
    public RequestView update(@PathVariable UUID id, @Valid @RequestBody UpdateRequest body, @AuthenticationPrincipal Jwt jwt) {
        return service.update(currentUser.from(jwt), id, body);
    }

    @PostMapping("/{id}/submit")
    public RequestView submit(@PathVariable UUID id, @Valid @RequestBody SubmitRequest body, @AuthenticationPrincipal Jwt jwt) {
        return service.submit(currentUser.from(jwt), id, body.version());
    }
}
