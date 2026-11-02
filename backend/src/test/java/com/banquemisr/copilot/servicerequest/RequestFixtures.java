package com.banquemisr.copilot.servicerequest;

import com.banquemisr.copilot.common.CopilotProperties;
import com.banquemisr.copilot.identity.AppUser;
import com.banquemisr.copilot.identity.Role;
import com.banquemisr.copilot.servicerequest.RequestPayload.CardIssue;
import com.banquemisr.copilot.servicerequest.RequestPayload.CardService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

public final class RequestFixtures {

    public static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-15T09:00:00Z"), ZoneOffset.UTC);

    private RequestFixtures() {
    }

    public static AppUser employee(String username) {
        return new AppUser(username, "x", "Employee " + username, Role.EMPLOYEE, "CAI-012");
    }

    public static AppUser supervisor(String username) {
        return new AppUser(username, "x", "Supervisor " + username, Role.SUPERVISOR, "CAI-012");
    }

    public static CategoryValidator validator() {
        CopilotProperties properties = new CopilotProperties(null, null, null, null, null, null, null,
                new CopilotProperties.Requests(12, 14), null);
        return new CategoryValidator(CLOCK, properties);
    }

    public static CardService card() {
        return new CardService("CUS-104233", "4821", CardIssue.REPLACEMENT, "CAI-012", "");
    }

    public static ServiceRequest draft(AppUser owner) {
        return draft(owner, card());
    }

    public static ServiceRequest draft(AppUser owner, RequestPayload payload) {
        String title = "Replace damaged debit card";
        String description = "The chip on the customer's card is damaged.";
        return new ServiceRequest("SR-2026-000101", owner, title, description, payload,
                validator().validate(title, description, payload), null);
    }

    public static ServiceRequest pending(AppUser owner) {
        ServiceRequest request = draft(owner);
        request.moveTo(RequestStatus.PENDING_APPROVAL);
        return request;
    }

    public static ServiceRequest approved(AppUser owner) {
        ServiceRequest request = pending(owner);
        request.moveTo(RequestStatus.APPROVED);
        request.recordDecision(ServiceRequest.Decision.APPROVED, supervisor("supervisor9"), null, List.of());
        return request;
    }
}
