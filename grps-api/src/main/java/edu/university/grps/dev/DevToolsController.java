package edu.university.grps.dev;

import edu.university.grps.common.AdjustableClock;
import edu.university.grps.common.ApiException;
import edu.university.grps.common.GrpsProperties;
import edu.university.grps.common.Times;
import edu.university.grps.deadline.service.ScheduledJobs;
import edu.university.grps.thesis.screening.ScreeningProvider;
import edu.university.grps.thesis.screening.ScreeningProviderConfig.StubScreeningProvider;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Local-only tools for demos and end-to-end tests: move the injected clock, run a job now and queue stub
 * screening scores. Disabled unless {@code grps.dev-tools=true} (the local profile).
 */
@RestController
@RequestMapping("/api/v1/dev")
@PreAuthorize("hasRole('ADMIN')")
public class DevToolsController {

    private final GrpsProperties props;
    private final AdjustableClock clock;
    private final ScheduledJobs jobs;
    private final ScreeningProvider screening;

    public DevToolsController(GrpsProperties props, AdjustableClock clock, ScheduledJobs jobs, ScreeningProvider screening) {
        this.props = props;
        this.clock = clock;
        this.jobs = jobs;
        this.screening = screening;
    }

    public record ClockRequest(LocalDate date, Integer offsetDays) {}

    public record ScoresRequest(BigDecimal similarityPct, BigDecimal aiPct) {}

    @GetMapping("/clock")
    public Map<String, Object> clock() {
        enabled();
        return Map.of("now", clock.instant(), "today", Times.today(clock), "offsetDays", clock.getOffset().toDays());
    }

    @PostMapping("/clock")
    public Map<String, Object> setClock(@RequestBody ClockRequest request) {
        enabled();
        if (request.date() != null) {
            LocalDate realToday = Times.today(java.time.Clock.systemUTC());
            clock.setOffset(Duration.ofDays(ChronoUnit.DAYS.between(realToday, request.date())));
        } else {
            clock.setOffset(Duration.ofDays(request.offsetDays() == null ? 0 : request.offsetDays()));
        }
        return clock();
    }

    @PostMapping("/jobs/{name}")
    public Map<String, Object> runJob(@PathVariable String name) {
        enabled();
        Supplier<Object> job = jobs.registry().get(name);
        if (job == null) throw ApiException.notFound("Job " + name + " (known: " + String.join(", ", jobs.registry().keySet()) + ")");
        return Map.of("job", name, "result", String.valueOf(job.get()));
    }

    @PostMapping("/screening/next")
    public Map<String, Object> queueScores(@RequestBody ScoresRequest request) {
        enabled();
        if (!(screening instanceof StubScreeningProvider stub)) throw ApiException.invalidState("Only the stub provider accepts queued scores.");
        stub.queueNext(request.similarityPct(), request.aiPct());
        return Map.of("queued", true);
    }

    private void enabled() {
        if (!props.devTools()) throw ApiException.notFound("Dev tools");
    }
}
