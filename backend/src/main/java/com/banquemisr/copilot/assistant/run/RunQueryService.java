package com.banquemisr.copilot.assistant.run;

import com.banquemisr.copilot.assistant.CopilotDtos.UsageTotals;
import com.banquemisr.copilot.assistant.run.RunDtos.ModelCall;
import com.banquemisr.copilot.assistant.run.RunDtos.RunSummary;
import com.banquemisr.copilot.assistant.run.RunDtos.RunTrace;
import com.banquemisr.copilot.assistant.run.RunDtos.UsageByDay;
import com.banquemisr.copilot.assistant.run.RunDtos.UsageByModel;
import com.banquemisr.copilot.assistant.run.RunDtos.UsageSummary;
import com.banquemisr.copilot.audit.AuditEvent;
import com.banquemisr.copilot.audit.AuditEventRepository;
import com.banquemisr.copilot.audit.AuditEventView;
import com.banquemisr.copilot.common.ApiException;
import com.banquemisr.copilot.servicerequest.ServiceRequestRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class RunQueryService {

    private static final int MAX_RUNS = 200;

    private final CopilotRunRepository runs;
    private final RunStepRepository steps;
    private final AiUsageRepository usage;
    private final AuditEventRepository events;
    private final ServiceRequestRepository requests;

    public RunQueryService(CopilotRunRepository runs, RunStepRepository steps, AiUsageRepository usage,
                           AuditEventRepository events, ServiceRequestRepository requests) {
        this.runs = runs;
        this.steps = steps;
        this.usage = usage;
        this.events = events;
        this.requests = requests;
    }

    public List<RunSummary> latest() {
        return runs.findLatest(Limit.of(MAX_RUNS)).stream().map(RunSummary::of).toList();
    }

    public RunTrace trace(String runId) {
        CopilotRun run = runs.findWithUser(runId).orElseThrow(() -> ApiException.notFound("Run not found."));
        List<AuditEvent> related = new ArrayList<>(events.findByRunIdOrderByAt(runId));
        requests.findBySourceRunId(runId).ifPresent(request ->
                related.addAll(events.findByResourceTypeAndResourceIdOrderByAt("ServiceRequest", request.getReference())));

        return new RunTrace(
                RunSummary.of(run),
                steps.findByRunIdOrderByStepNo(runId).stream().map(RunStep::toView).toList(),
                usage.findByRunIdOrderById(runId).stream().map(ModelCall::of).toList(),
                related.stream().distinct().sorted(Comparator.comparing(AuditEvent::getAt)).map(AuditEventView::of).toList());
    }

    public UsageSummary usage(LocalDate from, LocalDate to) {
        Instant start = from.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant end = to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        List<AiUsage> calls = usage.findBetween(start, end);
        Map<LocalDate, Long> runsPerDay = runs.findCreatedAtBetween(start, end).stream()
                .collect(Collectors.groupingBy(RunQueryService::day, TreeMap::new, Collectors.counting()));
        Map<LocalDate, List<AiUsage>> callsPerDay = calls.stream().collect(Collectors.groupingBy(call -> day(call.getAt())));

        TreeSet<LocalDate> days = new TreeSet<>(runsPerDay.keySet());
        days.addAll(callsPerDay.keySet());
        List<UsageByDay> byDay = new ArrayList<>();
        for (LocalDate date : days.descendingSet()) {
            List<AiUsage> dayCalls = callsPerDay.getOrDefault(date, List.of());
            byDay.add(new UsageByDay(date, runsPerDay.getOrDefault(date, 0L),
                    dayCalls.stream().mapToInt(AiUsage::getPromptTokens).sum(),
                    dayCalls.stream().mapToInt(AiUsage::getCompletionTokens).sum(),
                    dayCalls.stream().mapToDouble(AiUsage::getCostUsd).sum()));
        }

        List<UsageByModel> byModel = calls.stream()
                .collect(Collectors.groupingBy(call -> call.getProvider() + "/" + call.getModel(), TreeMap::new, Collectors.toList()))
                .values().stream()
                .map(group -> new UsageByModel(group.getFirst().getProvider(), group.getFirst().getModel(), group.size(),
                        group.stream().mapToInt(call -> call.getPromptTokens() + call.getCompletionTokens()).sum(),
                        group.stream().mapToDouble(AiUsage::getCostUsd).sum()))
                .toList();

        int prompt = calls.stream().mapToInt(AiUsage::getPromptTokens).sum();
        int completion = calls.stream().mapToInt(AiUsage::getCompletionTokens).sum();
        double cost = calls.stream().mapToDouble(AiUsage::getCostUsd).sum();
        long runCount = runsPerDay.values().stream().mapToLong(Long::longValue).sum();
        return new UsageSummary(from, to, runCount, new UsageTotals(prompt, completion, prompt + completion, cost), byDay, byModel);
    }

    private static LocalDate day(Instant instant) {
        return instant.atZone(ZoneOffset.UTC).toLocalDate();
    }
}
