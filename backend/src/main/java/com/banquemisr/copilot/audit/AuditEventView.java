package com.banquemisr.copilot.audit;

import com.banquemisr.copilot.identity.UserDtos.UserRef;
import java.time.Instant;
import java.util.UUID;

public record AuditEventView(UUID id, String action, UserRef actor, String resourceType, String resourceId, String detail,
                             String runId, Instant at) {

    private static final UserRef SYSTEM = new UserRef(null, "system", "System");

    public static AuditEventView of(AuditEvent event) {
        UserRef actor = event.getActor() == null ? SYSTEM : UserRef.of(event.getActor());
        return new AuditEventView(event.getId(), event.getAction(), actor, event.getResourceType(), event.getResourceId(),
                event.getDetail(), event.getRunId(), event.getAt());
    }
}
