package edu.university.grps.milestone.service;

import edu.university.grps.audit.AuditService;
import edu.university.grps.common.ApiException;
import edu.university.grps.common.ErrorCode;
import edu.university.grps.common.Times;
import edu.university.grps.evidence.service.EvidenceService;
import edu.university.grps.milestone.api.MilestoneDtos.AttemptDto;
import edu.university.grps.milestone.api.MilestoneDtos.MilestoneDto;
import edu.university.grps.milestone.api.MilestoneDtos.PanelMemberDto;
import edu.university.grps.milestone.api.MilestoneDtos.PresentationRowDto;
import edu.university.grps.milestone.api.MilestoneDtos.ScheduleResult;
import edu.university.grps.milestone.api.MilestoneDtos.SchedulableDto;
import edu.university.grps.milestone.domain.MilestoneStatus;
import edu.university.grps.milestone.domain.PanelMember;
import edu.university.grps.milestone.domain.PresentationAttempt;
import edu.university.grps.milestone.domain.StudentMilestone;
import edu.university.grps.milestone.repository.MilestoneRepositories.PresentationAttemptRepository;
import edu.university.grps.milestone.repository.MilestoneRepositories.StudentMilestoneRepository;
import edu.university.grps.notification.domain.NotificationCode;
import edu.university.grps.notification.service.NotificationService;
import edu.university.grps.programme.domain.MilestoneTemplate;
import edu.university.grps.programme.domain.Programme;
import edu.university.grps.programme.domain.Venue;
import edu.university.grps.programme.repository.ProgrammeRepositories.MilestoneTemplateRepository;
import edu.university.grps.programme.repository.ProgrammeRepositories.VenueRepository;
import edu.university.grps.security.CurrentUser;
import edu.university.grps.settings.service.SettingsService;
import edu.university.grps.student.domain.Student;
import edu.university.grps.student.domain.StudentStatus;
import edu.university.grps.student.service.DueDates;
import edu.university.grps.student.service.StudentAccess;
import edu.university.grps.user.domain.RoleCode;
import edu.university.grps.user.repository.AppUserRepository;
import edu.university.grps.user.service.UserDirectory;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Presentation milestones: generation, sequential gating (BR-03), scheduling with clash checks (FR-MIL-04),
 * outcomes and attempts (FR-MIL-07..09). The admin schedules; the secretary records what happened.
 */
@Service
@Transactional
public class MilestoneService {

    static final UUID NONE = new UUID(0, 0);
    private static final DateTimeFormatter ICS_TIME = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

    private final StudentMilestoneRepository milestones;
    private final PresentationAttemptRepository attempts;
    private final MilestoneTemplateRepository templates;
    private final VenueRepository venues;
    private final AppUserRepository users;
    private final StudentAccess access;
    private final EvidenceService evidence;
    private final SettingsService settings;
    private final NotificationService notifications;
    private final UserDirectory directory;
    private final AuditService audit;
    private final Clock clock;

    public MilestoneService(StudentMilestoneRepository milestones, PresentationAttemptRepository attempts,
                            MilestoneTemplateRepository templates, VenueRepository venues, AppUserRepository users,
                            StudentAccess access, EvidenceService evidence, SettingsService settings,
                            NotificationService notifications, UserDirectory directory, AuditService audit, Clock clock) {
        this.milestones = milestones;
        this.attempts = attempts;
        this.templates = templates;
        this.venues = venues;
        this.users = users;
        this.access = access;
        this.evidence = evidence;
        this.settings = settings;
        this.notifications = notifications;
        this.directory = directory;
        this.audit = audit;
        this.clock = clock;
    }

    // Generation and unlocking ------------------------------------------------

    /** FR-PRG-05: milestones are created from the programme template at enrolment, all LOCKED. */
    public void generateFor(Student s) {
        Programme programme = s.getProgramme();
        List<MilestoneTemplate> list = templates.findByProgrammeIdOrderBySequenceNo(programme.getId()).stream()
                .filter(t -> t.getSequenceNo() <= programme.getRequiredMilestones()).toList();
        if (list.size() != programme.getRequiredMilestones() || list.stream().noneMatch(MilestoneTemplate::isFinalPreDefense)) {
            throw ApiException.rule("The " + programme.getCode() + " milestone templates are incomplete. Fix them under Programmes first.");
        }
        for (MilestoneTemplate t : list) {
            StudentMilestone m = new StudentMilestone();
            m.setStudent(s);
            m.setTemplate(t);
            m.setSequenceNo(t.getSequenceNo());
            m.setTitle(t.getTitle());
            m.setFinalPreDefense(t.isFinalPreDefense());
            m.setEvidenceRequired(t.isEvidenceRequired() || programme.isEvidenceRequired());
            m.setStatus(MilestoneStatus.LOCKED);
            milestones.save(m);
        }
        milestones.flush();
        audit.record("MILESTONES_GENERATED", "Student", s.getId(), s.getId(), null, Map.of("count", list.size()));
        openFirstIfDue(s);
    }

    /** Milestone 1 opens at the start of semester 2 (MilestoneUnlockJob). */
    public boolean openFirstIfDue(Student s) {
        if (s.isClosed()) return false;
        LocalDate opens = DueDates.compute(s.getProgramme(), s.getEnrolledOn(), 0).firstMilestoneOpens();
        if (Times.today(clock).isBefore(opens)) return false;
        StudentMilestone first = milestones.findByStudentIdAndSequenceNo(s.getId(), 1).orElse(null);
        if (first == null || first.getStatus() != MilestoneStatus.LOCKED) return false;
        makeAvailable(first, s);
        return true;
    }

    void makeAvailable(StudentMilestone m, Student s) {
        m.setStatus(MilestoneStatus.AVAILABLE);
        m.setAvailableAt(clock.instant());
        audit.record("MILESTONE_AVAILABLE", "StudentMilestone", m.getId(), s.getId(), null, Map.of("milestone", m.label()));
        notifications.notify(access.studentAndSupervisors(s), NotificationCode.N01, m.label() + " is now available",
                m.label() + " (" + m.getTitle() + ") for " + s.getUser().getFullName()
                        + " can now be scheduled. The graduate school will set the date.", "/student/presentations");
    }

    @Transactional(readOnly = true)
    public Map<UUID, Long> approvedCounts(Collection<UUID> studentIds) {
        Map<UUID, Long> out = new HashMap<>();
        if (studentIds.isEmpty()) return out;
        for (Object[] row : milestones.countApprovedByStudents(studentIds)) out.put((UUID) row[0], (Long) row[1]);
        return out;
    }

    // Views ---------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<MilestoneDto> forStudent(UUID studentId) {
        return buildViews(access.requireViewable(studentId));
    }

    @Transactional(readOnly = true)
    public List<MilestoneDto> mine() {
        return buildViews(access.currentStudent());
    }

    @Transactional(readOnly = true)
    public List<MilestoneDto> buildViews(Student s) {
        List<StudentMilestone> list = milestones.findByStudentIdOrderBySequenceNo(s.getId());
        List<UUID> ids = list.stream().map(StudentMilestone::getId).toList();
        Map<UUID, List<PresentationAttempt>> byMilestone = ids.isEmpty() ? Map.of()
                : attempts.findByMilestoneIds(ids).stream().collect(Collectors.groupingBy(a -> a.getMilestone().getId()));
        Map<UUID, Long> evidenceCounts = evidence.countCurrent(ids);
        Set<UUID> people = new HashSet<>();
        list.forEach(m -> people.add(m.getApprovedBy()));
        byMilestone.values().forEach(as -> as.forEach(a -> {
            people.add(a.getRecordedBy());
            a.getPanel().forEach(p -> people.add(p.getUserId()));
        }));
        Map<UUID, String> names = directory.names(people);
        int max = settings.integer(SettingsService.MAX_ATTEMPTS);
        LocalDate opens = DueDates.compute(s.getProgramme(), s.getEnrolledOn(), 0).firstMilestoneOpens();

        List<MilestoneDto> out = new ArrayList<>();
        for (StudentMilestone m : list) {
            List<AttemptDto> as = byMilestone.getOrDefault(m.getId(), List.of()).stream()
                    .sorted(Comparator.comparingInt(PresentationAttempt::getAttemptNo))
                    .map(a -> toAttemptDto(a, names)).toList();
            AttemptDto current = as.isEmpty() ? null : as.get(as.size() - 1);
            int failed = (int) as.stream().filter(a -> "FAILED".equals(a.outcome())).count();
            out.add(new MilestoneDto(m.getId(), s.getId(), m.getSequenceNo(), m.label(), m.getTitle(), m.isFinalPreDefense(),
                    m.isEvidenceRequired(), m.getStatus().name(), lockedReason(m, s, opens), m.getAvailableAt(),
                    m.getApprovedAt(), names.get(m.getApprovedBy()), evidenceCounts.getOrDefault(m.getId(), 0L), failed, max,
                    current, as));
        }
        return out;
    }

    private String lockedReason(StudentMilestone m, Student s, LocalDate opens) {
        if (m.getStatus() != MilestoneStatus.LOCKED) return null;
        if (s.isClosed()) return "Closed: the student's record is " + s.getStatus().name().toLowerCase().replace('_', ' ') + ".";
        if (m.getSequenceNo() == 1) {
            return "Opens at the start of semester " + s.getProgramme().getFirstMilestoneSemester() + " (" + Times.formatDate(opens) + ").";
        }
        return "Locked until Presentation " + (m.getSequenceNo() - 1) + " is approved.";
    }

    // Scheduling (FR-MIL-02..05) --------------------------------------------------

    public record PanelInput(UUID userId, String externalName, PanelMember.Role role) {}

    public record ScheduleInput(Instant start, Integer durationMinutes, UUID venueId, String onlineLink,
                                List<PanelInput> panel) {}

    public record RescheduleInput(Instant start, Integer durationMinutes, UUID venueId, String onlineLink,
                                  List<PanelInput> panel, String reason) {}

    public record BulkInput(List<UUID> milestoneIds, Instant start, Integer slotMinutes, Integer breakMinutes,
                            UUID venueId, String onlineLink, List<PanelInput> panel) {}

    /** BR-03 / BR-04: only the admin schedules, and only when the previous milestone is APPROVED. */
    public ScheduleResult schedule(UUID milestoneId, ScheduleInput in) {
        StudentMilestone m = milestones.lockById(milestoneId).orElseThrow(() -> ApiException.notFound("Milestone"));
        Student s = m.getStudent();
        access.requireWritable(s);
        requireSchedulable(m);
        int failed = attempts.countFailed(m.getId());
        int max = settings.integer(SettingsService.MAX_ATTEMPTS);
        if (failed >= max) {
            throw new ApiException(ErrorCode.MAX_ATTEMPTS_REACHED, m.label() + " has reached the maximum of " + max
                    + " failed attempts. Raise the limit in Settings or record an extension before scheduling again.",
                    Map.of("failedAttempts", failed, "maxAttempts", max));
        }
        Window w = window(in.start(), in.durationMinutes());
        Venue venue = resolveVenue(in.venueId(), in.onlineLink());
        List<PanelInput> panel = validatePanel(in.panel());
        List<String> warnings = checkClashes(s, venue, panel, w, NONE);

        PresentationAttempt a = new PresentationAttempt();
        a.setMilestone(m);
        a.setAttemptNo(attempts.maxAttemptNo(m.getId()) + 1);
        a.setScheduledStart(w.start());
        a.setScheduledEnd(w.end());
        a.setVenue(venue);
        a.setOnlineLink(blankToNull(in.onlineLink()));
        setPanel(a, panel);
        a = attempts.save(a);
        m.setStatus(MilestoneStatus.SCHEDULED);

        audit.record("PRESENTATION_SCHEDULED", "PresentationAttempt", a.getId(), s.getId(), null,
                Map.of("milestone", m.label(), "attemptNo", a.getAttemptNo(), "start", w.start(), "end", w.end(),
                        "venue", venue == null ? "online" : venue.getName()));
        List<UUID> recipients = new ArrayList<>(access.studentAndSupervisors(s));
        recipients.addAll(access.secretaries());
        notifications.notify(recipients, NotificationCode.N02, m.label() + " scheduled",
                s.getUser().getFullName() + "'s " + m.label() + " (" + m.getTitle() + ") is scheduled for "
                        + Times.formatDateTime(w.start()) + " at " + where(venue, a.getOnlineLink()) + ".",
                "/student/presentations");
        return new ScheduleResult(toAttemptDto(a, directory.names(panelUserIds(a))), warnings);
    }

    /** FR-MIL-03: slots are assigned in order; each student still gets an individual record. All or nothing. */
    public List<ScheduleResult> bulkSchedule(BulkInput in) {
        if (in.milestoneIds() == null || in.milestoneIds().isEmpty()) throw ApiException.validation("Choose at least one student.");
        if (in.start() == null) throw ApiException.validation("Choose a start time.");
        int slot = in.slotMinutes() == null ? 45 : in.slotMinutes();
        int gap = in.breakMinutes() == null ? 0 : Math.max(0, in.breakMinutes());
        List<ScheduleResult> results = new ArrayList<>();
        Instant start = in.start();
        for (UUID id : in.milestoneIds()) {
            try {
                results.add(schedule(id, new ScheduleInput(start, slot, in.venueId(), in.onlineLink(), in.panel())));
            } catch (ApiException e) {
                String who = milestones.findWithStudentById(id).map(m -> m.getStudent().getUser().getFullName()).orElse("a student");
                throw new ApiException(e.code(), "Nothing was scheduled. Problem with " + who + ": " + e.getMessage(), e.properties());
            }
            start = start.plus(Duration.ofMinutes(slot + gap));
        }
        return results;
    }

    /** FR-MIL-05: reason mandatory; student, supervisor and secretary notified; history kept in the audit log. */
    public AttemptDto reschedule(UUID attemptId, RescheduleInput in) {
        requireReason(in.reason());
        PresentationAttempt a = attempts.findWithMilestoneById(attemptId).orElseThrow(() -> ApiException.notFound("Presentation"));
        if (!a.isOpen()) throw ApiException.invalidState("Only a scheduled presentation without an outcome can be rescheduled.");
        Student s = a.getMilestone().getStudent();
        access.requireWritable(s);
        Window w = window(in.start(), in.durationMinutes());
        Venue venue = resolveVenue(in.venueId(), in.onlineLink());
        List<PanelInput> panel = in.panel() == null ? currentPanel(a) : validatePanel(in.panel());
        checkClashes(s, venue, panel, w, a.getId());
        Map<String, Object> before = Map.of("start", a.getScheduledStart(), "end", a.getScheduledEnd(),
                "venue", a.getVenue() == null ? "online" : a.getVenue().getName());
        a.setScheduledStart(w.start());
        a.setScheduledEnd(w.end());
        a.setVenue(venue);
        a.setOnlineLink(blankToNull(in.onlineLink()));
        a.setReminder48Sent(false);
        a.setReminder24Sent(false);
        if (in.panel() != null) setPanel(a, panel);
        audit.record("PRESENTATION_RESCHEDULED", "PresentationAttempt", a.getId(), s.getId(), before,
                Map.of("start", w.start(), "end", w.end(), "venue", venue == null ? "online" : venue.getName(), "reason", in.reason()));
        notifyChange(a, s, "rescheduled to " + Times.formatDateTime(w.start()) + " at " + where(venue, a.getOnlineLink())
                + ". Reason: " + in.reason().trim());
        return toAttemptDto(a, directory.names(panelUserIds(a)));
    }

    public AttemptDto cancel(UUID attemptId, String reason) {
        requireReason(reason);
        PresentationAttempt a = attempts.findWithMilestoneById(attemptId).orElseThrow(() -> ApiException.notFound("Presentation"));
        if (!a.isOpen()) throw ApiException.invalidState("Only a scheduled presentation without an outcome can be cancelled.");
        StudentMilestone m = a.getMilestone();
        Student s = m.getStudent();
        a.setCancelledAt(clock.instant());
        a.setCancelledReason(reason.trim());
        if (m.getStatus() == MilestoneStatus.SCHEDULED) m.setStatus(MilestoneStatus.AVAILABLE);
        audit.record("PRESENTATION_CANCELLED", "PresentationAttempt", a.getId(), s.getId(), null,
                Map.of("milestone", m.label(), "reason", reason));
        notifyChange(a, s, "cancelled. Reason: " + reason.trim() + ". It will be scheduled again.");
        return toAttemptDto(a, directory.names(panelUserIds(a)));
    }

    /** Cancels any open attempt when a student's record is closed (BR-20). */
    public void cancelOpenAttempts(Student s, String reason) {
        for (StudentMilestone m : milestones.findByStudentIdOrderBySequenceNo(s.getId())) {
            attempts.findOpen(m.getId()).ifPresent(a -> {
                a.setCancelledAt(clock.instant());
                a.setCancelledReason(reason);
                if (m.getStatus() == MilestoneStatus.SCHEDULED) m.setStatus(MilestoneStatus.AVAILABLE);
            });
        }
    }

    private void notifyChange(PresentationAttempt a, Student s, String what) {
        StudentMilestone m = a.getMilestone();
        List<UUID> recipients = new ArrayList<>(access.studentAndSupervisors(s));
        recipients.addAll(access.secretaries());
        notifications.notify(recipients, NotificationCode.N03, m.label() + " changed",
                s.getUser().getFullName() + "'s " + m.label() + " (" + m.getTitle() + ") was " + what, "/student/presentations");
    }

    private void requireSchedulable(StudentMilestone m) {
        switch (m.getStatus()) {
            case AVAILABLE -> {
                if (m.getSequenceNo() > 1) {
                    StudentMilestone previous = milestones.findByStudentIdAndSequenceNo(m.getStudent().getId(), m.getSequenceNo() - 1)
                            .orElseThrow();
                    if (previous.getStatus() != MilestoneStatus.APPROVED) throw locked(m, previous);
                }
            }
            case LOCKED -> {
                StudentMilestone blocking = milestones.findByStudentIdOrderBySequenceNo(m.getStudent().getId()).stream()
                        .filter(x -> x.getSequenceNo() < m.getSequenceNo() && x.getStatus() != MilestoneStatus.APPROVED)
                        .reduce((first, second) -> second).orElse(null);
                if (blocking == null) {
                    throw new ApiException(ErrorCode.MILESTONE_LOCKED, m.label()
                            + " cannot be scheduled yet; it opens at the start of semester "
                            + m.getStudent().getProgramme().getFirstMilestoneSemester() + ".");
                }
                throw locked(m, blocking);
            }
            case SCHEDULED -> throw ApiException.invalidState(m.label() + " is already scheduled. Reschedule it instead.");
            default -> throw ApiException.invalidState(m.label() + " is " + m.getStatus().name().toLowerCase().replace('_', ' ')
                    + " and cannot be scheduled.");
        }
    }

    private static ApiException locked(StudentMilestone m, StudentMilestone blocking) {
        return new ApiException(ErrorCode.MILESTONE_LOCKED,
                m.label() + " cannot be scheduled until " + blocking.label() + " is approved.",
                Map.of("blockingMilestoneId", blocking.getId(), "blockingSequenceNo", blocking.getSequenceNo()));
    }

    private record Window(Instant start, Instant end) {}

    private Window window(Instant start, Integer durationMinutes) {
        if (start == null) throw ApiException.validation("Choose a date and start time.");
        int minutes = durationMinutes == null ? 45 : durationMinutes;
        if (minutes < 10 || minutes > 480) throw ApiException.validation("Duration must be between 10 and 480 minutes.");
        if (!start.isAfter(clock.instant())) throw ApiException.validation("The presentation must start in the future.");
        return new Window(start, start.plus(Duration.ofMinutes(minutes)));
    }

    private Venue resolveVenue(UUID venueId, String onlineLink) {
        if (venueId == null) {
            if (onlineLink == null || onlineLink.isBlank()) throw ApiException.validation("Choose a venue or give an online link.");
            if (!onlineLink.trim().matches("(?i)^https?://\\S+$")) throw ApiException.validation("The online link must start with http:// or https://.");
            return null;
        }
        Venue v = venues.findById(venueId).orElseThrow(() -> ApiException.notFound("Venue"));
        if (!v.isActive()) throw ApiException.validation(v.getName() + " is not in use.");
        return v;
    }

    private List<PanelInput> validatePanel(List<PanelInput> panel) {
        if (panel == null) return List.of();
        List<PanelInput> out = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        for (PanelInput p : panel) {
            if (p.userId() == null && (p.externalName() == null || p.externalName().isBlank())) continue;
            if (p.userId() != null) {
                if (!seen.add(p.userId())) throw ApiException.validation("A panel member is listed twice.");
                users.findById(p.userId()).filter(u -> u.isActive()).orElseThrow(() -> ApiException.notFound("Panel member"));
            }
            out.add(new PanelInput(p.userId(), p.externalName() == null ? null : p.externalName().trim(),
                    p.role() == null ? PanelMember.Role.MEMBER : p.role()));
        }
        if (out.stream().filter(p -> p.role() == PanelMember.Role.CHAIR).count() > 1) {
            throw ApiException.validation("A panel has at most one chair.");
        }
        return out;
    }

    private List<PanelInput> currentPanel(PresentationAttempt a) {
        return a.getPanel().stream().map(p -> new PanelInput(p.getUserId(), p.getExternalName(), p.getRole())).toList();
    }

    /** Student and venue clashes are refused; panel clashes come back as warnings (FR-MIL-04). */
    private List<String> checkClashes(Student s, Venue venue, List<PanelInput> panel, Window w, UUID excludeId) {
        if (!attempts.studentClashes(s.getId(), w.start(), w.end(), excludeId).isEmpty()) {
            throw new ApiException(ErrorCode.SCHEDULE_CLASH,
                    s.getUser().getFullName() + " already has a presentation at an overlapping time.");
        }
        if (venue != null && !attempts.venueClashes(venue.getId(), w.start(), w.end(), excludeId).isEmpty()) {
            throw new ApiException(ErrorCode.SCHEDULE_CLASH, venue.getName() + " is already booked at an overlapping time.");
        }
        List<UUID> panelIds = panel.stream().map(PanelInput::userId).filter(Objects::nonNull).toList();
        if (panelIds.isEmpty()) return List.of();
        Set<UUID> busy = new HashSet<>();
        attempts.panelClashes(panelIds, w.start(), w.end(), excludeId)
                .forEach(a -> a.getPanel().forEach(p -> { if (panelIds.contains(p.getUserId())) busy.add(p.getUserId()); }));
        Map<UUID, String> names = directory.names(busy);
        return busy.stream().map(id -> names.get(id) + " is on another panel at an overlapping time.").toList();
    }

    private void setPanel(PresentationAttempt a, List<PanelInput> panel) {
        a.getPanel().clear();
        for (PanelInput p : panel) {
            PanelMember pm = new PanelMember();
            pm.setAttempt(a);
            pm.setUserId(p.userId());
            pm.setExternalName(p.userId() == null ? p.externalName() : null);
            pm.setRole(p.role());
            a.getPanel().add(pm);
        }
    }

    // Outcomes (FR-MIL-07..10, FR-MIL-13) -----------------------------------------

    public record OutcomeInput(PresentationAttempt.Outcome outcome, String remarks, BigDecimal score, String reason) {}

    /** BR-05: the secretary (or admin) records PASSED, FAILED or NOT_HELD on or after the scheduled date. */
    public AttemptDto recordOutcome(UUID attemptId, OutcomeInput in) {
        PresentationAttempt a = attempts.findWithMilestoneById(attemptId).orElseThrow(() -> ApiException.notFound("Presentation"));
        if (a.isCancelled()) throw ApiException.invalidState("This presentation was cancelled.");
        if (a.getOutcome() != null) throw ApiException.invalidState("An outcome is already recorded. Use a correction instead.");
        validateOutcome(in);
        LocalDate scheduledDate = Times.local(a.getScheduledStart()).toLocalDate();
        if (Times.today(clock).isBefore(scheduledDate)) {
            throw ApiException.rule("The outcome can only be recorded on or after " + Times.formatDate(scheduledDate) + ".");
        }
        StudentMilestone m = a.getMilestone();
        Student s = m.getStudent();
        access.requireWritable(s);
        a.setOutcome(in.outcome());
        a.setPanelRemarks(blankToNull(in.remarks()));
        a.setScore(in.score());
        a.setRecordedBy(CurrentUser.id());
        a.setRecordedAt(clock.instant());
        audit.record("OUTCOME_RECORDED", "PresentationAttempt", a.getId(), s.getId(), null,
                Map.of("milestone", m.label(), "attemptNo", a.getAttemptNo(), "outcome", in.outcome().name()));
        applyOutcome(a, m, s, false);
        return toAttemptDto(a, directory.names(panelUserIds(a)));
    }

    /** FR-MIL-13: the secretary can correct within 48 hours; after that only the admin. Every change is audited. */
    public AttemptDto correctOutcome(UUID attemptId, OutcomeInput in) {
        PresentationAttempt a = attempts.findWithMilestoneById(attemptId).orElseThrow(() -> ApiException.notFound("Presentation"));
        if (a.getOutcome() == null) throw ApiException.invalidState("No outcome has been recorded yet.");
        requireReason(in.reason());
        validateOutcome(in);
        StudentMilestone m = a.getMilestone();
        Student s = m.getStudent();
        if (m.getStatus() == MilestoneStatus.APPROVED) {
            throw ApiException.invalidState(m.label() + " is already approved; its outcome can no longer change.");
        }
        if (attempts.maxAttemptNo(m.getId()) != a.getAttemptNo()) {
            throw ApiException.invalidState("Only the latest attempt's outcome can be corrected.");
        }
        int hours = settings.integer(SettingsService.SECRETARY_CORRECTION_HOURS);
        boolean withinWindow = a.getRecordedAt().plus(Duration.ofHours(hours)).isAfter(clock.instant());
        if (!CurrentUser.isAdmin() && !withinWindow) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Outcomes can be corrected by the secretary within " + hours
                    + " hours. Ask an admin to correct this one.");
        }
        Map<String, Object> before = new LinkedHashMap<>();
        before.put("outcome", a.getOutcome().name());
        before.put("remarks", a.getPanelRemarks());
        before.put("score", a.getScore());
        a.setOutcome(in.outcome());
        a.setPanelRemarks(blankToNull(in.remarks()));
        a.setScore(in.score());
        a.setRecordedBy(CurrentUser.id());
        audit.record("OUTCOME_CORRECTED", "PresentationAttempt", a.getId(), s.getId(), before,
                Map.of("outcome", in.outcome().name(), "reason", in.reason()));
        applyOutcome(a, m, s, true);
        return toAttemptDto(a, directory.names(panelUserIds(a)));
    }

    private void applyOutcome(PresentationAttempt a, StudentMilestone m, Student s, boolean correction) {
        String prefix = correction ? "Corrected outcome: " : "";
        if (a.getOutcome() == PresentationAttempt.Outcome.PASSED) {
            m.setStatus(MilestoneStatus.AWAITING_VERIFICATION);
            String evidenceHint = m.isEvidenceRequired() && evidence.countCurrentClean(m.getId()) == 0
                    ? " Upload your evidence (slides, report, signed panel sheet) so the graduate school can approve it."
                    : " The graduate school will now verify it.";
            notifications.notify(access.studentAndSupervisors(s), NotificationCode.N05, prefix + m.label() + " passed",
                    s.getUser().getFullName() + " passed " + m.label() + " (" + m.getTitle() + ")." + evidenceHint,
                    "/student/evidence");
            notifications.notify(access.admins(), NotificationCode.N05, prefix + m.label() + " passed: awaiting verification",
                    s.getUser().getFullName() + " passed " + m.label() + ". It is in the verification queue.", "/admin/verification");
            return;
        }
        m.setStatus(MilestoneStatus.AVAILABLE);
        String what = a.getOutcome() == PresentationAttempt.Outcome.FAILED ? "was not passed" : "did not take place";
        notifications.notify(access.studentAndSupervisors(s), NotificationCode.N05, prefix + m.label() + " " + what,
                s.getUser().getFullName() + "'s " + m.label() + " (" + m.getTitle() + ") " + what + "."
                        + (a.getPanelRemarks() == null ? "" : " Panel remarks: " + a.getPanelRemarks())
                        + " A new attempt will be scheduled.", "/student/presentations");
        if (a.getOutcome() == PresentationAttempt.Outcome.FAILED) {
            int failed = attempts.countFailed(m.getId());
            int max = settings.integer(SettingsService.MAX_ATTEMPTS);
            if (failed >= max && s.getStatus() == StudentStatus.ACTIVE) {
                s.setStatus(StudentStatus.AT_RISK);
                s.setStatusReason("Reached the maximum of " + max + " failed attempts for " + m.label() + ".");
                audit.record("STATUS_AT_RISK", "Student", s.getId(), s.getId(), Map.of("status", "ACTIVE"),
                        Map.of("status", "AT_RISK", "reason", s.getStatusReason()));
                List<UUID> recipients = new ArrayList<>(access.studentAndSupervisors(s));
                recipients.addAll(access.admins());
                notifications.notify(recipients, NotificationCode.N16, s.getUser().getFullName() + " is at risk",
                        s.getStatusReason(), "/admin/at-risk");
            }
        }
    }

    private static void validateOutcome(OutcomeInput in) {
        if (in.outcome() == null) throw ApiException.validation("Choose an outcome.");
        if (in.outcome() == PresentationAttempt.Outcome.FAILED && (in.remarks() == null || in.remarks().isBlank())) {
            throw ApiException.validation("Panel remarks are required when the presentation is not passed.");
        }
        if (in.score() != null && (in.score().signum() < 0 || in.score().compareTo(BigDecimal.valueOf(100)) > 0)) {
            throw ApiException.validation("Score must be between 0 and 100.");
        }
    }

    // Lists for the admin board and secretary calendar ---------------------------------

    @Transactional(readOnly = true)
    public List<SchedulableDto> schedulable(UUID programmeId) {
        List<StudentMilestone> list = milestones.findByStatusOrderByUpdatedAtAsc(MilestoneStatus.AVAILABLE);
        Instant now = clock.instant();
        List<SchedulableDto> out = new ArrayList<>();
        for (StudentMilestone m : list) {
            Student s = m.getStudent();
            if (s.isClosed()) continue;
            if (programmeId != null && !s.getProgramme().getId().equals(programmeId)) continue;
            out.add(new SchedulableDto(m.getId(), s.getId(), s.getUser().getFullName(), s.getUser().getStaffOrStudentNo(),
                    s.getProgramme().getCode(), s.getCohort().getName(), m.getSequenceNo(), m.getTitle(),
                    m.isFinalPreDefense(), attempts.maxAttemptNo(m.getId()) + 1, m.getAvailableAt(),
                    m.getAvailableAt() == null ? 0 : ChronoUnit.DAYS.between(m.getAvailableAt(), now), s.getStatus().name()));
        }
        out.sort(Comparator.comparing(SchedulableDto::daysAvailable).reversed());
        return out;
    }

    @Transactional(readOnly = true)
    public List<PresentationRowDto> presentations(Instant from, Instant to, UUID programmeId, UUID venueId, String state) {
        List<PresentationAttempt> list = attempts.findInRange(from, to);
        return rows(list.stream()
                .filter(a -> programmeId == null || a.getMilestone().getStudent().getProgramme().getId().equals(programmeId))
                .filter(a -> venueId == null || (a.getVenue() != null && a.getVenue().getId().equals(venueId)))
                .filter(a -> state == null || state.isBlank() || state.equalsIgnoreCase(attemptState(a))));
    }

    @Transactional(readOnly = true)
    public List<PresentationRowDto> awaitingOutcome() {
        return rows(attempts.awaitingOutcome(clock.instant()).stream());
    }

    @Transactional(readOnly = true)
    public List<PresentationRowDto> upcomingFor(Collection<UUID> studentIds, int days) {
        if (studentIds.isEmpty()) return List.of();
        Instant now = clock.instant();
        return rows(attempts.findInRange(now.minus(Duration.ofDays(1)), now.plus(Duration.ofDays(days))).stream()
                .filter(a -> studentIds.contains(a.getMilestone().getStudent().getId())));
    }

    private List<PresentationRowDto> rows(Stream<PresentationAttempt> stream) {
        List<PresentationAttempt> list = stream.toList();
        Set<UUID> people = new HashSet<>();
        list.forEach(a -> a.getPanel().forEach(p -> people.add(p.getUserId())));
        Map<UUID, String> names = directory.names(people);
        return list.stream().map(a -> {
            StudentMilestone m = a.getMilestone();
            Student s = m.getStudent();
            return new PresentationRowDto(a.getId(), m.getId(), s.getId(), s.getUser().getFullName(),
                    s.getUser().getStaffOrStudentNo(), s.getProgramme().getCode(), m.getSequenceNo(), m.getTitle(),
                    m.isFinalPreDefense(), a.getAttemptNo(), a.getScheduledStart(), a.getScheduledEnd(),
                    a.getVenue() == null ? null : a.getVenue().getName(), a.getOnlineLink(),
                    a.getOutcome() == null ? null : a.getOutcome().name(), attemptState(a), panelDtos(a, names));
        }).toList();
    }

    /** FR-MIL-14: .ics download for a scheduled presentation. */
    @Transactional(readOnly = true)
    public String ics(UUID attemptId) {
        PresentationAttempt a = attempts.findWithMilestoneById(attemptId).orElseThrow(() -> ApiException.notFound("Presentation"));
        Student s = a.getMilestone().getStudent();
        if (!access.canView(s)) throw ApiException.notFound("Presentation");
        StudentMilestone m = a.getMilestone();
        String location = where(a.getVenue(), a.getOnlineLink());
        return String.join("\r\n",
                "BEGIN:VCALENDAR", "VERSION:2.0", "PRODID:-//GRPS//Presentations//EN", "CALSCALE:GREGORIAN",
                "METHOD:PUBLISH", "BEGIN:VEVENT",
                "UID:" + a.getId() + "@grps",
                "DTSTAMP:" + ICS_TIME.format(clock.instant()),
                "DTSTART:" + ICS_TIME.format(a.getScheduledStart()),
                "DTEND:" + ICS_TIME.format(a.getScheduledEnd()),
                "SUMMARY:" + icsEscape(m.label() + ": " + m.getTitle() + " (" + s.getUser().getFullName() + ")"),
                "LOCATION:" + icsEscape(location),
                "DESCRIPTION:" + icsEscape("Attempt " + a.getAttemptNo() + ". " + (a.isCancelled() ? "CANCELLED. " : "")
                        + "Graduate Research Progress System."),
                a.isCancelled() ? "STATUS:CANCELLED" : "STATUS:CONFIRMED",
                "END:VEVENT", "END:VCALENDAR", "");
    }

    // Helpers ---------------------------------------------------------------------

    public AttemptDto toAttemptDto(PresentationAttempt a, Map<UUID, String> names) {
        return new AttemptDto(a.getId(), a.getMilestone().getId(), a.getAttemptNo(), a.getScheduledStart(), a.getScheduledEnd(),
                a.getVenue() == null ? null : a.getVenue().getId(), a.getVenue() == null ? null : a.getVenue().getName(),
                a.getOnlineLink(), a.getOutcome() == null ? null : a.getOutcome().name(), a.getScore(), a.getPanelRemarks(),
                names.getOrDefault(a.getRecordedBy(), a.getRecordedBy() == null ? null : directory.name(a.getRecordedBy())),
                a.getRecordedAt(), a.getCancelledReason(), a.getCancelledAt(), panelDtos(a, names), attemptState(a));
    }

    private static List<PanelMemberDto> panelDtos(PresentationAttempt a, Map<UUID, String> names) {
        return a.getPanel().stream().map(p -> new PanelMemberDto(p.getUserId(),
                p.getUserId() == null ? p.getExternalName() : names.get(p.getUserId()), p.getExternalName(), p.getRole().name())).toList();
    }

    private static List<UUID> panelUserIds(PresentationAttempt a) {
        List<UUID> ids = new ArrayList<>();
        a.getPanel().forEach(p -> ids.add(p.getUserId()));
        ids.add(a.getRecordedBy());
        return ids;
    }

    static String attemptState(PresentationAttempt a) {
        if (a.isCancelled()) return "CANCELLED";
        if (a.getOutcome() != null) return a.getOutcome().name();
        return "SCHEDULED";
    }

    private static String where(Venue venue, String onlineLink) {
        if (venue != null && onlineLink != null) return venue.getName() + " (also online: " + onlineLink + ")";
        if (venue != null) return venue.getName();
        return "online: " + onlineLink;
    }

    private static String icsEscape(String s) {
        return s.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace("\n", "\\n");
    }

    private static void requireReason(String reason) {
        if (reason == null || reason.isBlank()) throw ApiException.validation("A reason is required.");
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    public static boolean isStaffViewer() {
        return CurrentUser.isAdmin() || CurrentUser.hasRole(RoleCode.SECRETARY);
    }
}
