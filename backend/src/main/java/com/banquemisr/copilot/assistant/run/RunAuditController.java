package com.banquemisr.copilot.assistant.run;

import com.banquemisr.copilot.assistant.run.RunDtos.RunSummary;
import com.banquemisr.copilot.assistant.run.RunDtos.RunTrace;
import com.banquemisr.copilot.assistant.run.RunDtos.UsageSummary;
import com.banquemisr.copilot.common.ApiException;
import com.banquemisr.copilot.common.FieldError;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/audit")
@PreAuthorize("hasRole('SUPERVISOR')")
public class RunAuditController {

    private static final int MAX_RANGE_DAYS = 366;

    private final RunQueryService runs;

    public RunAuditController(RunQueryService runs) {
        this.runs = runs;
    }

    @GetMapping("/runs")
    public List<RunSummary> runs() {
        return runs.latest();
    }

    @GetMapping("/runs/{runId}")
    public RunTrace run(@PathVariable String runId) {
        return runs.trace(runId);
    }

    @GetMapping("/usage")
    public UsageSummary usage(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate end = to != null ? to : LocalDate.now(ZoneOffset.UTC);
        LocalDate start = from != null ? from : end.minusDays(29);
        if (start.isAfter(end) || start.plusDays(MAX_RANGE_DAYS).isBefore(end)) {
            throw ApiException.badRequest("Invalid date range.", new FieldError("from", "Use a range of at most one year, with from before to."));
        }
        return runs.usage(start, end);
    }
}
