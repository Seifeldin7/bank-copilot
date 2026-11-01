package com.banquemisr.copilot.servicerequest;

import com.banquemisr.copilot.audit.AuditService;
import com.banquemisr.copilot.audit.RunContext;
import com.banquemisr.copilot.identity.AppUser;
import com.banquemisr.copilot.servicerequest.RequestHistory.Action;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class RequestHistoryRecorder {

    private final RequestHistoryRepository history;
    private final AuditService audit;

    public RequestHistoryRecorder(RequestHistoryRepository history, AuditService audit) {
        this.history = history;
        this.audit = audit;
    }

    public void record(ServiceRequest request, Action action, AppUser actor, RequestStatus from, RequestStatus to,
                       List<FieldChange> changes, String reason) {
        history.save(new RequestHistory(request, action, actor, from, to, changes, reason, RunContext.currentRunId()));
        String detail = reason != null ? reason : (from == null ? "new" : from) + " -> " + to;
        audit.record("REQUEST_" + action, actor, "ServiceRequest", request.getReference(), detail);
    }
}
