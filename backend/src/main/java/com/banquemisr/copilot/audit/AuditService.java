package com.banquemisr.copilot.audit;

import com.banquemisr.copilot.identity.AppUser;
import org.springframework.stereotype.Service;

@Service
public class AuditService {

    private final AuditEventRepository events;

    public AuditService(AuditEventRepository events) {
        this.events = events;
    }

    public void record(String action, AppUser actor, String resourceType, String resourceId, String detail) {
        String trimmed = detail != null && detail.length() > 1000 ? detail.substring(0, 1000) : detail;
        events.save(new AuditEvent(action, actor, resourceType, resourceId, trimmed, RunContext.currentRunId()));
    }
}
