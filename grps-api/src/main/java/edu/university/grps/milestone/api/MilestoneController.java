package edu.university.grps.milestone.api;

import edu.university.grps.common.Times;
import edu.university.grps.milestone.api.MilestoneDtos.AttemptDto;
import edu.university.grps.milestone.api.MilestoneDtos.MilestoneDto;
import edu.university.grps.milestone.api.MilestoneDtos.PresentationRowDto;
import edu.university.grps.milestone.api.MilestoneDtos.ScheduleResult;
import edu.university.grps.milestone.api.MilestoneDtos.SchedulableDto;
import edu.university.grps.milestone.api.MilestoneDtos.VerificationItemDto;
import edu.university.grps.milestone.service.MilestoneService;
import edu.university.grps.milestone.service.MilestoneService.BulkInput;
import edu.university.grps.milestone.service.MilestoneService.OutcomeInput;
import edu.university.grps.milestone.service.MilestoneService.RescheduleInput;
import edu.university.grps.milestone.service.MilestoneService.ScheduleInput;
import edu.university.grps.milestone.service.VerificationService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class MilestoneController {

    private final MilestoneService milestones;
    private final VerificationService verification;
    private final Clock clock;

    public MilestoneController(MilestoneService milestones, VerificationService verification, Clock clock) {
        this.milestones = milestones;
        this.verification = verification;
        this.clock = clock;
    }

    public record ReasonRequest(String reason) {}

    public record CommentRequest(String comment) {}

    // Student and viewers

    @GetMapping("/students/me/milestones")
    @PreAuthorize("hasRole('STUDENT')")
    public List<MilestoneDto> mine() {
        return milestones.mine();
    }

    @GetMapping("/students/{studentId}/milestones")
    @PreAuthorize("hasAnyRole('ADMIN','SECRETARY','SUPERVISOR','STUDENT')")
    public List<MilestoneDto> forStudent(@PathVariable UUID studentId) {
        return milestones.forStudent(studentId);
    }

    @GetMapping(path = "/attempts/{id}/calendar.ics", produces = "text/calendar")
    public ResponseEntity<String> ics(@PathVariable UUID id) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("presentation.ics").build().toString())
                .contentType(MediaType.parseMediaType("text/calendar; charset=utf-8"))
                .body(milestones.ics(id));
    }

    // Admin scheduling (BR-04)

    @GetMapping("/admin/schedule/available")
    @PreAuthorize("hasRole('ADMIN')")
    public List<SchedulableDto> schedulable(@RequestParam(required = false) UUID programmeId) {
        return milestones.schedulable(programmeId);
    }

    @PostMapping("/admin/milestones/{id}/schedule")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public ScheduleResult schedule(@PathVariable UUID id, @RequestBody ScheduleInput input) {
        return milestones.schedule(id, input);
    }

    @PostMapping("/admin/schedule/bulk")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public List<ScheduleResult> bulk(@RequestBody BulkInput input) {
        return milestones.bulkSchedule(input);
    }

    @PostMapping("/admin/attempts/{id}/reschedule")
    @PreAuthorize("hasRole('ADMIN')")
    public AttemptDto reschedule(@PathVariable UUID id, @RequestBody RescheduleInput input) {
        return milestones.reschedule(id, input);
    }

    @PostMapping("/admin/attempts/{id}/cancel")
    @PreAuthorize("hasRole('ADMIN')")
    public AttemptDto cancel(@PathVariable UUID id, @RequestBody ReasonRequest request) {
        return milestones.cancel(id, request.reason());
    }

    // Secretary calendar and outcomes (BR-05)

    @GetMapping("/secretary/presentations")
    @PreAuthorize("hasAnyRole('SECRETARY','ADMIN')")
    public List<PresentationRowDto> presentations(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID programmeId,
            @RequestParam(required = false) UUID venueId,
            @RequestParam(required = false) String state) {
        LocalDate start = from == null ? Times.today(clock).minusDays(7) : from;
        LocalDate end = to == null ? start.plusDays(37) : to;
        Instant fromInstant = start.atStartOfDay(Times.ZONE).toInstant();
        Instant toInstant = end.plusDays(1).atStartOfDay(Times.ZONE).toInstant();
        return milestones.presentations(fromInstant, toInstant, programmeId, venueId, state);
    }

    @GetMapping("/secretary/awaiting-outcome")
    @PreAuthorize("hasAnyRole('SECRETARY','ADMIN')")
    public List<PresentationRowDto> awaitingOutcome() {
        return milestones.awaitingOutcome();
    }

    @PostMapping("/secretary/attempts/{id}/outcome")
    @PreAuthorize("hasAnyRole('SECRETARY','ADMIN')")
    public AttemptDto recordOutcome(@PathVariable UUID id, @RequestBody OutcomeInput input) {
        return milestones.recordOutcome(id, input);
    }

    @PostMapping("/secretary/attempts/{id}/outcome/correct")
    @PreAuthorize("hasAnyRole('SECRETARY','ADMIN')")
    public AttemptDto correctOutcome(@PathVariable UUID id, @RequestBody OutcomeInput input) {
        return milestones.correctOutcome(id, input);
    }

    // Verification (section 9)

    @GetMapping("/admin/verification-queue")
    @PreAuthorize("hasRole('ADMIN')")
    public List<VerificationItemDto> queue() {
        return verification.queue();
    }

    @PostMapping("/admin/milestones/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public MilestoneDto approve(@PathVariable UUID id) {
        return verification.approve(id);
    }

    @PostMapping("/admin/milestones/{id}/return")
    @PreAuthorize("hasRole('ADMIN')")
    public MilestoneDto returnMilestone(@PathVariable UUID id, @RequestBody CommentRequest request) {
        return verification.returnMilestone(id, request.comment());
    }

    @GetMapping("/admin/verification-queue/count")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Integer> queueCount() {
        return Map.of("count", verification.queue().size());
    }
}
