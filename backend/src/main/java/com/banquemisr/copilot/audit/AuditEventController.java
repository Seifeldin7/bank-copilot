package com.banquemisr.copilot.audit;

import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/audit/events")
@PreAuthorize("hasRole('SUPERVISOR')")
public class AuditEventController {

    private static final int MAX_EVENTS = 200;

    private final AuditEventRepository events;

    public AuditEventController(AuditEventRepository events) {
        this.events = events;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<AuditEventView> latest() {
        return events.findAllByOrderByAtDesc(Limit.of(MAX_EVENTS)).stream().map(AuditEventView::of).toList();
    }
}
