package edu.university.grps.dashboard.service;

import edu.university.grps.attendance.service.AttendanceService;
import edu.university.grps.attendance.service.AttendanceService.StudentAttendanceDto;
import edu.university.grps.common.Times;
import edu.university.grps.milestone.api.MilestoneDtos.MilestoneDto;
import edu.university.grps.milestone.api.MilestoneDtos.PresentationRowDto;
import edu.university.grps.milestone.domain.MilestoneStatus;
import edu.university.grps.milestone.service.MilestoneService;
import edu.university.grps.milestone.service.VerificationService;
import edu.university.grps.publication.domain.Publication;
import edu.university.grps.publication.service.PublicationService;
import edu.university.grps.publication.service.PublicationService.PublicationDto;
import edu.university.grps.security.CurrentUser;
import edu.university.grps.settings.service.SettingsService;
import edu.university.grps.student.api.StudentDtos.StudentSummaryDto;
import edu.university.grps.student.domain.Student;
import edu.university.grps.student.domain.StudentStatus;
import edu.university.grps.student.repository.StudentRepositories.StudentRepository;
import edu.university.grps.student.service.StudentAccess;
import edu.university.grps.student.service.StudentService;
import edu.university.grps.thesis.domain.ThesisSubmission;
import edu.university.grps.thesis.service.ThesisService;
import edu.university.grps.thesis.service.ThesisService.ThesisDto;
import edu.university.grps.thesis.service.ThesisService.ThesisOverviewDto;
import edu.university.grps.user.service.UserDirectory;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read models for the role dashboards, the student achievements timeline and supervisor views. */
@Service
@Transactional(readOnly = true)
public class DashboardService {

    private final StudentAccess access;
    private final StudentService students;
    private final StudentRepository studentRepository;
    private final MilestoneService milestones;
    private final VerificationService verification;
    private final PublicationService publications;
    private final ThesisService theses;
    private final AttendanceService attendance;
    private final UserDirectory directory;
    private final SettingsService settings;
    private final Clock clock;

    public DashboardService(StudentAccess access, StudentService students, StudentRepository studentRepository,
                            MilestoneService milestones, VerificationService verification, PublicationService publications,
                            ThesisService theses, AttendanceService attendance, UserDirectory directory,
                            SettingsService settings, Clock clock) {
        this.access = access;
        this.students = students;
        this.studentRepository = studentRepository;
        this.milestones = milestones;
        this.verification = verification;
        this.publications = publications;
        this.theses = theses;
        this.attendance = attendance;
        this.directory = directory;
        this.settings = settings;
        this.clock = clock;
    }

    // Student ---------------------------------------------------------------------------

    public record NextAction(String text, String link) {}

    public record StudentDashboardDto(StudentSummaryDto student, String statusLabel, NextAction nextAction,
                                      List<MilestoneDto> milestones, PresentationRowDto nextPresentation,
                                      long approvedPublications, String thesisStatus, boolean thesisUnlocked,
                                      List<StudentAttendanceDto> attendanceWarnings) {}

    /** FR-DSH-01: programme, semester, approved out of required, next action, countdowns and status. */
    public StudentDashboardDto studentDashboard() {
        Student s = access.currentStudent();
        return dashboardFor(s);
    }

    public StudentDashboardDto dashboardFor(Student s) {
        StudentSummaryDto summary = students.summary(s);
        List<MilestoneDto> ms = milestones.buildViews(s);
        ThesisOverviewDto thesis = theses.overviewFor(s.getId());
        String thesisStatus = thesis.versions().isEmpty() ? null : thesis.versions().get(0).status();
        List<PresentationRowDto> upcoming = milestones.upcomingFor(List.of(s.getId()), 60).stream()
                .filter(r -> "SCHEDULED".equals(r.state())).toList();
        List<StudentAttendanceDto> warnings = attendance.forStudent(s).stream()
                .filter(a -> a.attendancePct() != null && !a.status().startsWith("ELIGIBLE (")
                        && a.attendancePct().compareTo(a.threshold().add(java.math.BigDecimal.valueOf(5))) < 0).toList();
        return new StudentDashboardDto(summary, statusLabel(s.getStatus()), nextAction(s, ms, thesis),
                ms, upcoming.isEmpty() ? null : upcoming.get(0), publications.approvedCount(s.getId()), thesisStatus,
                !thesis.locked(), warnings);
    }

    static String statusLabel(StudentStatus status) {
        return switch (status) {
            case ACTIVE -> "On track";
            case AT_RISK -> "At risk";
            case FLAGGED_FOR_EXPULSION -> "Flagged";
            case EXPELLED -> "Expelled";
            case COMPLETED -> "Completed";
            case WITHDRAWN -> "Withdrawn";
        };
    }

    private NextAction nextAction(Student s, List<MilestoneDto> ms, ThesisOverviewDto thesis) {
        switch (s.getStatus()) {
            case EXPELLED -> { return new NextAction("Your record is closed: expulsion confirmed.", "/student"); }
            case WITHDRAWN -> { return new NextAction("Your record is closed: withdrawn.", "/student"); }
            case COMPLETED -> { return new NextAction("All milestones approved and thesis accepted. Congratulations.", "/student/achievements"); }
            default -> { }
        }
        MilestoneDto next = ms.stream().filter(m -> !"APPROVED".equals(m.status())).findFirst().orElse(null);
        if (next != null) {
            return switch (MilestoneStatus.valueOf(next.status())) {
                case LOCKED -> new NextAction(next.label() + ": " + next.lockedReason(), "/student/presentations");
                case AVAILABLE -> new NextAction(next.label() + " (" + next.title() + ") is open. The graduate school will schedule it.",
                        "/student/presentations");
                case SCHEDULED -> new NextAction("Prepare for " + next.label() + " on "
                        + Times.formatDateTime(next.currentAttempt().scheduledStart()) + ".", "/student/presentations");
                case AWAITING_VERIFICATION -> next.evidenceRequired() && next.evidenceCount() == 0
                        ? new NextAction("Upload evidence for " + next.label() + " so it can be approved.", "/student/evidence")
                        : new NextAction(next.label() + " passed and is awaiting verification.", "/student/evidence");
                case RETURNED -> new NextAction(next.label() + " was returned. Upload corrected evidence.", "/student/evidence");
                case APPROVED -> new NextAction("", "/student");
            };
        }
        if (thesis.versions().isEmpty()) return new NextAction("Submit your thesis.", "/student/thesis");
        ThesisDto latest = thesis.versions().get(0);
        return switch (ThesisSubmission.Status.valueOf(latest.status())) {
            case FORMAT_FAILED -> new NextAction("Fix the format problems and upload a new thesis version.", "/student/thesis");
            case SCREENING_REJECTED -> new NextAction("Revise your thesis and upload a new version.", "/student/thesis");
            case ACCEPTED_FOR_EXAMINATION -> new NextAction("Your thesis is accepted for examination.", "/student/thesis");
            default -> new NextAction("Your thesis is being checked.", "/student/thesis");
        };
    }

    // Achievements (FR-ACH-01, FR-ACH-02) --------------------------------------------

    public record AchievementDto(String type, String title, String detail, Instant at, String approvedBy, String badge) {}

    public List<AchievementDto> achievements() {
        return achievementsFor(access.currentStudent());
    }

    public List<AchievementDto> achievementsFor(UUID studentId) {
        return achievementsFor(access.requireViewable(studentId));
    }

    public List<AchievementDto> achievementsFor(Student s) {
        List<AchievementDto> out = new ArrayList<>();
        for (MilestoneDto m : milestones.buildViews(s)) {
            if (!"APPROVED".equals(m.status())) continue;
            out.add(new AchievementDto("MILESTONE", m.label() + ": " + m.title(),
                    m.finalPreDefense() ? "Final pre-defense approved" : "Presentation approved", m.approvedAt(), m.approvedByName(),
                    m.finalPreDefense() ? "Pre-defense complete" : "Presentation " + m.sequenceNo()));
        }
        List<PublicationDto> pubs = publications.approvedForStudent(s.getId()).stream()
                .sorted(Comparator.comparing(PublicationDto::reviewedAt)).toList();
        for (int i = 0; i < pubs.size(); i++) {
            PublicationDto p = pubs.get(i);
            out.add(new AchievementDto("PUBLICATION", p.title(), label(p) , p.reviewedAt(), p.reviewedByName(),
                    i == 0 ? "First publication" : null));
        }
        for (ThesisDto t : theses.overviewFor(s.getId()).versions()) {
            if ("ACCEPTED_FOR_EXAMINATION".equals(t.status())) {
                out.add(new AchievementDto("THESIS", "Thesis accepted for examination", t.title() + " (version " + t.versionNo() + ")",
                        t.updatedAt(), t.overrideByName() == null ? "Automatic screening" : t.overrideByName(), "Thesis accepted"));
            }
        }
        out.sort(Comparator.comparing(AchievementDto::at, Comparator.nullsLast(Comparator.naturalOrder())));
        return out;
    }

    private static String label(PublicationDto p) {
        String type = Publication.Type.valueOf(p.type()).name().replace('_', ' ').toLowerCase();
        return Character.toUpperCase(type.charAt(0)) + type.substring(1) + (p.venueName() == null ? "" : ", " + p.venueName())
                + (p.year() == null ? "" : " (" + p.year() + ")");
    }

    // Supervisor -------------------------------------------------------------------------

    public record SupervisorDashboardDto(List<StudentSummaryDto> students, List<PresentationRowDto> upcoming,
                                         Map<String, Long> byStatus, long awaitingVerification) {}

    public record SupervisorStudentDto(StudentDashboardDto progress, List<PublicationDto> publications,
                                       ThesisOverviewDto thesis, List<StudentAttendanceDto> attendance,
                                       List<AchievementDto> achievements) {}

    public SupervisorDashboardDto supervisorDashboard() {
        List<UUID> ids = access.supervisedStudentIds(CurrentUser.id());
        List<Student> list = ids.isEmpty() ? List.of() : studentRepository.findAllByIdIn(ids);
        List<StudentSummaryDto> summaries = students.summaries(list).stream()
                .sorted(Comparator.comparing(StudentSummaryDto::name)).toList();
        Map<String, Long> byStatus = new java.util.LinkedHashMap<>();
        for (StudentStatus st : StudentStatus.values()) byStatus.put(st.name(), 0L);
        summaries.forEach(s -> byStatus.merge(s.status(), 1L, Long::sum));
        long awaiting = ids.isEmpty() ? 0 : list.stream().mapToLong(s ->
                milestones.buildViews(s).stream().filter(m -> "AWAITING_VERIFICATION".equals(m.status())).count()).sum();
        return new SupervisorDashboardDto(summaries, milestones.upcomingFor(ids, 30), byStatus, awaiting);
    }

    public SupervisorStudentDto supervisorStudent(UUID studentId) {
        Student s = access.requireViewable(studentId);
        List<PublicationDto> pubs = publications.forSupervisor().stream().filter(p -> p.studentId().equals(studentId)).toList();
        if (!access.isSupervisorOf(studentId)) pubs = publications.approvedForStudent(studentId);
        return new SupervisorStudentDto(dashboardFor(s), pubs, theses.overviewFor(studentId), attendance.forStudent(s),
                achievementsFor(s));
    }

    // Admin, secretary, librarian, lecturer -------------------------------------------------

    public record AdminDashboardDto(Map<String, Long> studentsByStatus, long verificationQueue, long oldestVerificationDays,
                                    long presentationsThisWeek, long awaitingOutcome, long publicationsAwaitingReview,
                                    long thesesInPipeline, long thesesNeedingManualScreening, long schedulable,
                                    List<StudentSummaryDto> atRisk) {}

    public AdminDashboardDto adminDashboard() {
        Map<StudentStatus, Long> counts = new EnumMap<>(StudentStatus.class);
        for (StudentStatus st : StudentStatus.values()) counts.put(st, 0L);
        studentRepository.findAll().forEach(s -> counts.merge(s.getStatus(), 1L, Long::sum));
        Map<String, Long> byStatus = new java.util.LinkedHashMap<>();
        counts.forEach((k, v) -> byStatus.put(k.name(), v));
        var queue = verification.queue();
        Instant now = clock.instant();
        LocalDate today = Times.today(clock);
        Instant weekStart = today.atStartOfDay(Times.ZONE).toInstant();
        long week = milestones.presentations(weekStart, weekStart.plus(Duration.ofDays(7)), null, null, "SCHEDULED").size();
        List<ThesisDto> all = theses.adminList(null);
        long pipeline = all.stream().filter(t -> List.of("UPLOADED", "FORMAT_CHECKING", "FORMAT_PASSED", "SCREENING").contains(t.status())).count();
        long manual = all.stream().filter(t -> "SCREENING".equals(t.status()) && t.updatedAt().isBefore(now.minus(Duration.ofMinutes(15)))).count();
        List<StudentSummaryDto> risk = students.summaries(studentRepository.findByStatuses(
                java.util.EnumSet.of(StudentStatus.AT_RISK, StudentStatus.FLAGGED_FOR_EXPULSION), null, null)).stream().limit(8).toList();
        return new AdminDashboardDto(byStatus, queue.size(), queue.isEmpty() ? 0 : queue.get(0).daysWaiting(), week,
                milestones.awaitingOutcome().size(), publications.queue(null, null, null).size(), pipeline, manual,
                milestones.schedulable(null).size(), risk);
    }

    public record SecretaryDashboardDto(List<PresentationRowDto> today, List<PresentationRowDto> awaitingOutcome,
                                        long thisWeek) {}

    public SecretaryDashboardDto secretaryDashboard() {
        LocalDate today = Times.today(clock);
        Instant start = today.atStartOfDay(Times.ZONE).toInstant();
        List<PresentationRowDto> todays = milestones.presentations(start, start.plus(Duration.ofDays(1)), null, null, null);
        long week = milestones.presentations(start, start.plus(Duration.ofDays(7)), null, null, "SCHEDULED").size();
        return new SecretaryDashboardDto(todays, milestones.awaitingOutcome(), week);
    }

    public record LibrarianDashboardDto(long awaitingReview, long oldestDays, long approvedTotal, List<PublicationDto> oldest) {}

    public LibrarianDashboardDto librarianDashboard() {
        List<PublicationDto> queue = publications.queue(null, null, null);
        return new LibrarianDashboardDto(queue.size(), queue.isEmpty() ? 0 : queue.get(0).daysWaiting(),
                publications.approved().size(), queue.stream().limit(5).toList());
    }

    public record LecturerCourseSummary(AttendanceService.CourseDto course, long belowThreshold, long belowWarning) {}

    public List<LecturerCourseSummary> lecturerDashboard() {
        List<LecturerCourseSummary> out = new ArrayList<>();
        java.math.BigDecimal warning = settings.decimal(SettingsService.ATTENDANCE_WARNING);
        for (AttendanceService.CourseDto c : attendance.myCourses()) {
            var rows = attendance.eligibilityList(c.id(), null);
            long below = rows.stream().filter(r -> "NOT_ELIGIBLE".equals(r.status())).count();
            long warn = rows.stream().filter(r -> r.attendancePct() != null && "ELIGIBLE".equals(r.status())
                    && r.attendancePct().compareTo(warning) < 0).count();
            out.add(new LecturerCourseSummary(c, below, warn));
        }
        return out;
    }

    public String name(UUID id) {
        return directory.name(id);
    }
}
