package edu.university.grps.report.service;

import edu.university.grps.attendance.service.AttendanceService;
import edu.university.grps.attendance.service.AttendanceService.CourseDto;
import edu.university.grps.common.ApiException;
import edu.university.grps.common.Times;
import edu.university.grps.milestone.api.MilestoneDtos.PresentationRowDto;
import edu.university.grps.milestone.api.MilestoneDtos.VerificationItemDto;
import edu.university.grps.milestone.domain.MilestoneStatus;
import edu.university.grps.milestone.domain.StudentMilestone;
import edu.university.grps.milestone.repository.MilestoneRepositories.StudentMilestoneRepository;
import edu.university.grps.milestone.service.MilestoneService;
import edu.university.grps.milestone.service.VerificationService;
import edu.university.grps.programme.domain.Programme;
import edu.university.grps.programme.repository.ProgrammeRepositories.ProgrammeRepository;
import edu.university.grps.publication.domain.Publication;
import edu.university.grps.publication.service.PublicationService;
import edu.university.grps.publication.service.PublicationService.PublicationDto;
import edu.university.grps.student.api.StudentDtos.StudentSummaryDto;
import edu.university.grps.student.domain.Student;
import edu.university.grps.student.domain.StudentStatus;
import edu.university.grps.student.domain.Supervision;
import edu.university.grps.student.repository.StudentRepositories.StudentRepository;
import edu.university.grps.student.repository.StudentRepositories.SupervisionRepository;
import edu.university.grps.student.service.StudentService;
import edu.university.grps.thesis.service.ThesisService;
import edu.university.grps.thesis.service.ThesisService.ThesisDto;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Standard reports (FR-RPT-01..07). Each returns a table that the API serves as JSON, CSV or PDF. */
@Service
@Transactional(readOnly = true)
public class ReportService {

    public record Filters(UUID programmeId, UUID cohortId, LocalDate from, LocalDate to, UUID courseId) {}

    public record Table(String key, String title, List<String> subtitle, List<String> columns, List<List<String>> rows) {}

    public static final Map<String, String> REPORTS = new LinkedHashMap<>();

    static {
        REPORTS.put("programme-progress", "Programme progress");
        REPORTS.put("presentation-schedule", "Presentation schedule");
        REPORTS.put("verification-backlog", "Verification backlog");
        REPORTS.put("publications", "Approved publications");
        REPORTS.put("thesis-screening", "Thesis screening");
        REPORTS.put("attendance-eligibility", "Attendance and eligibility");
        REPORTS.put("supervisor-load", "Supervisor load");
        REPORTS.put("at-risk", "At-risk and flagged students");
    }

    private final StudentRepository students;
    private final StudentMilestoneRepository milestoneRepo;
    private final SupervisionRepository supervisions;
    private final ProgrammeRepository programmes;
    private final StudentService studentService;
    private final MilestoneService milestones;
    private final VerificationService verification;
    private final PublicationService publications;
    private final ThesisService theses;
    private final AttendanceService attendance;
    private final Clock clock;

    public ReportService(StudentRepository students, StudentMilestoneRepository milestoneRepo, SupervisionRepository supervisions,
                         ProgrammeRepository programmes, StudentService studentService, MilestoneService milestones,
                         VerificationService verification, PublicationService publications, ThesisService theses,
                         AttendanceService attendance, Clock clock) {
        this.students = students;
        this.milestoneRepo = milestoneRepo;
        this.supervisions = supervisions;
        this.programmes = programmes;
        this.studentService = studentService;
        this.milestones = milestones;
        this.verification = verification;
        this.publications = publications;
        this.theses = theses;
        this.attendance = attendance;
        this.clock = clock;
    }

    public Table run(String key, Filters f) {
        return switch (key) {
            case "programme-progress" -> programmeProgress(f);
            case "presentation-schedule" -> presentationSchedule(f);
            case "verification-backlog" -> verificationBacklog(f);
            case "publications" -> publicationsReport(f);
            case "thesis-screening" -> thesisScreening(f);
            case "attendance-eligibility" -> attendanceEligibility(f);
            case "supervisor-load" -> supervisorLoad(f);
            case "at-risk" -> atRisk(f);
            default -> throw ApiException.notFound("Report " + key);
        };
    }

    /** FR-RPT-01: students by status, approved-milestone distribution and average days between milestones. */
    private Table programmeProgress(Filters f) {
        List<Student> list = students.findForReport(f.programmeId(), f.cohortId());
        Map<String, List<Student>> groups = list.stream().collect(Collectors.groupingBy(
                s -> s.getProgramme().getCode() + "|" + s.getCohort().getName(), LinkedHashMap::new, Collectors.toList()));
        Map<UUID, List<StudentMilestone>> byStudent = list.isEmpty() ? Map.of()
                : milestoneRepo.findByStudentIds(list.stream().map(Student::getId).toList()).stream()
                .collect(Collectors.groupingBy(m -> m.getStudent().getId()));
        List<List<String>> rows = new ArrayList<>();
        for (var e : groups.entrySet()) {
            String[] parts = e.getKey().split("\\|", 2);
            List<Student> group = e.getValue();
            Map<StudentStatus, Long> byStatus = group.stream().collect(Collectors.groupingBy(Student::getStatus, Collectors.counting()));
            int required = group.get(0).getProgramme().getRequiredMilestones();
            long[] dist = new long[required + 1];
            List<Long> gaps = new ArrayList<>();
            for (Student s : group) {
                List<StudentMilestone> ms = byStudent.getOrDefault(s.getId(), List.of());
                int approved = (int) ms.stream().filter(m -> m.getStatus() == MilestoneStatus.APPROVED).count();
                dist[Math.min(approved, required)]++;
                List<Instant> dates = ms.stream().filter(m -> m.getApprovedAt() != null).map(StudentMilestone::getApprovedAt).sorted().toList();
                Instant prev = s.getEnrolledOn().atStartOfDay(Times.ZONE).toInstant();
                for (Instant d : dates) {
                    gaps.add(ChronoUnit.DAYS.between(prev, d));
                    prev = d;
                }
            }
            StringBuilder distText = new StringBuilder();
            for (int i = 0; i <= required; i++) distText.append(i == 0 ? "" : "  ").append(i).append(": ").append(dist[i]);
            rows.add(List.of(parts[0], parts[1], String.valueOf(group.size()),
                    count(byStatus, StudentStatus.ACTIVE), count(byStatus, StudentStatus.AT_RISK),
                    count(byStatus, StudentStatus.FLAGGED_FOR_EXPULSION), count(byStatus, StudentStatus.EXPELLED),
                    count(byStatus, StudentStatus.COMPLETED), count(byStatus, StudentStatus.WITHDRAWN), distText.toString(),
                    gaps.isEmpty() ? "" : String.valueOf(Math.round(gaps.stream().mapToLong(Long::longValue).average().orElse(0)))));
        }
        return new Table("programme-progress", REPORTS.get("programme-progress"), filtersText(f),
                List.of("Programme", "Cohort", "Students", "Active", "At risk", "Flagged", "Expelled", "Completed", "Withdrawn",
                        "Approved milestones (count: students)", "Avg days between milestones"), rows);
    }

    /** FR-RPT-02: presentations by date range, venue and outcome. */
    private Table presentationSchedule(Filters f) {
        LocalDate from = f.from() == null ? Times.today(clock).minusDays(30) : f.from();
        LocalDate to = f.to() == null ? Times.today(clock).plusDays(30) : f.to();
        List<PresentationRowDto> list = milestones.presentations(from.atStartOfDay(Times.ZONE).toInstant(),
                to.plusDays(1).atStartOfDay(Times.ZONE).toInstant(), f.programmeId(), null, null);
        List<List<String>> rows = list.stream().map(r -> List.of(Times.formatDateTime(r.scheduledStart()), r.studentName(),
                r.studentNumber(), r.programmeCode(), "Presentation " + r.sequenceNo() + ": " + r.milestoneTitle(),
                String.valueOf(r.attemptNo()), r.venueName() == null ? "Online" : r.venueName(), pretty(r.state()))).toList();
        return new Table("presentation-schedule", REPORTS.get("presentation-schedule"),
                List.of(Times.formatDate(from) + " to " + Times.formatDate(to)),
                List.of("Date and time", "Student", "Number", "Programme", "Milestone", "Attempt", "Venue", "Outcome"), rows);
    }

    /** FR-RPT-03: milestones awaiting verification with days waiting. */
    private Table verificationBacklog(Filters f) {
        List<VerificationItemDto> list = verification.queue();
        List<List<String>> rows = list.stream()
                .filter(v -> f.programmeId() == null || programmeCode(f.programmeId()).equals(v.programmeCode()))
                .map(v -> List.of(v.studentName(), v.studentNumber(), v.programmeCode(), "Presentation " + v.sequenceNo() + ": " + v.title(),
                        v.outcomeAt() == null ? "" : Times.formatDateTime(v.outcomeAt()), String.valueOf(v.evidenceCount()),
                        String.valueOf(v.daysWaiting()))).toList();
        return new Table("verification-backlog", REPORTS.get("verification-backlog"), filtersText(f),
                List.of("Student", "Number", "Programme", "Milestone", "Outcome recorded", "Evidence files", "Days waiting"), rows);
    }

    /** FR-RPT-04: approved publications by student, programme, type and year. */
    private Table publicationsReport(Filters f) {
        List<PublicationDto> list = publications.approved();
        String code = f.programmeId() == null ? null : programmeCode(f.programmeId());
        List<List<String>> rows = list.stream()
                .filter(p -> code == null || code.equals(p.programmeCode()))
                .sorted(Comparator.comparing(PublicationDto::studentName))
                .map(p -> List.of(p.studentName(), p.studentNumber(), p.programmeCode(), p.title(),
                        pretty(Publication.Type.valueOf(p.type()).name()), p.venueName() == null ? "" : p.venueName(),
                        p.year() == null ? "" : String.valueOf(p.year()), p.doi() == null ? "" : p.doi(),
                        p.reviewedAt() == null ? "" : Times.formatDateTime(p.reviewedAt()))).toList();
        return new Table("publications", REPORTS.get("publications"), filtersText(f),
                List.of("Student", "Number", "Programme", "Title", "Type", "Venue", "Year", "DOI", "Approved"), rows);
    }

    /** FR-RPT-05: submissions, format pass rate, screening pass rate and overrides. */
    private Table thesisScreening(Filters f) {
        List<ThesisDto> list = theses.adminList(null);
        String code = f.programmeId() == null ? null : programmeCode(f.programmeId());
        Map<String, List<ThesisDto>> byProgramme = list.stream().filter(t -> code == null || code.equals(t.programmeCode()))
                .collect(Collectors.groupingBy(ThesisDto::programmeCode, LinkedHashMap::new, Collectors.toList()));
        List<List<String>> rows = new ArrayList<>();
        for (var e : byProgramme.entrySet()) {
            List<ThesisDto> ts = e.getValue();
            long formatChecked = ts.stream().filter(t -> !List.of("UPLOADED", "FORMAT_CHECKING").contains(t.status())).count();
            long formatFailed = ts.stream().filter(t -> "FORMAT_FAILED".equals(t.status())).count();
            long screened = ts.stream().filter(t -> t.similarityPct() != null).count();
            long screenRejected = ts.stream().filter(t -> "SCREENING_REJECTED".equals(t.status())
                    || (t.overrideReason() != null)).count();
            long overrides = ts.stream().filter(t -> t.overrideReason() != null).count();
            long accepted = ts.stream().filter(t -> "ACCEPTED_FOR_EXAMINATION".equals(t.status())).count();
            rows.add(List.of(e.getKey(), String.valueOf(ts.size()), rate(formatChecked - formatFailed, formatChecked),
                    rate(screened - screenRejected, screened), String.valueOf(overrides), String.valueOf(accepted)));
        }
        return new Table("thesis-screening", REPORTS.get("thesis-screening"), filtersText(f),
                List.of("Programme", "Submissions", "Format pass rate", "Screening pass rate", "Overrides", "Accepted"), rows);
    }

    /** FR-RPT-06: per course, or per student when a course is chosen. */
    private Table attendanceEligibility(Filters f) {
        if (f.courseId() != null) {
            CourseDto c = attendance.course(f.courseId());
            var list = attendance.eligibilityList(f.courseId(), f.to());
            List<List<String>> rows = list.stream().map(r -> List.of(r.name(), r.number(), r.programmeCode(),
                    String.valueOf(r.sessionsCounted()), String.valueOf(r.sessionsAttended()),
                    r.attendancePct() == null ? "" : r.attendancePct() + "%", r.status())).toList();
            return new Table("attendance-eligibility", c.code() + " " + c.title() + ": attendance and eligibility",
                    List.of(c.semesterLabel()), List.of("Student", "Number", "Programme", "Sessions counted", "Attended", "Attendance", "Status"), rows);
        }
        List<List<String>> rows = new ArrayList<>();
        for (CourseDto c : attendance.listCourses(null)) {
            var list = attendance.eligibilityList(c.id(), f.to());
            long eligible = list.stream().filter(r -> r.status().startsWith("ELIGIBLE")).count();
            long exceptions = list.stream().filter(r -> r.status().contains("EXCEPTION")).count();
            rows.add(List.of(c.code(), c.title(), c.semesterLabel(), String.valueOf(list.size()), String.valueOf(c.sessionsHeld()),
                    String.valueOf(eligible), String.valueOf(list.size() - eligible), String.valueOf(exceptions)));
        }
        return new Table("attendance-eligibility", REPORTS.get("attendance-eligibility"), filtersText(f),
                List.of("Course", "Title", "Semester", "Students", "Sessions held", "Eligible", "Not eligible", "Exceptions"), rows);
    }

    /** FR-RPT-07: supervisees per supervisor with their statuses. */
    private Table supervisorLoad(Filters f) {
        Map<UUID, List<Supervision>> bySupervisor = supervisions.findAllOpen().stream()
                .filter(s -> f.programmeId() == null || s.getStudent().getProgramme().getId().equals(f.programmeId()))
                .collect(Collectors.groupingBy(s -> s.getSupervisor().getId()));
        List<List<String>> rows = new ArrayList<>();
        for (var e : bySupervisor.entrySet()) {
            List<Supervision> svs = e.getValue();
            Map<StudentStatus, Long> byStatus = svs.stream().collect(Collectors.groupingBy(s -> s.getStudent().getStatus(), Collectors.counting()));
            long primary = svs.stream().filter(s -> s.getType() == Supervision.Type.PRIMARY).count();
            rows.add(List.of(svs.get(0).getSupervisor().getFullName(), String.valueOf(svs.size()), String.valueOf(primary),
                    String.valueOf(svs.size() - primary), count(byStatus, StudentStatus.ACTIVE), count(byStatus, StudentStatus.AT_RISK),
                    count(byStatus, StudentStatus.FLAGGED_FOR_EXPULSION), count(byStatus, StudentStatus.COMPLETED)));
        }
        rows.sort(Comparator.comparing((List<String> r) -> Integer.parseInt(r.get(1))).reversed());
        return new Table("supervisor-load", REPORTS.get("supervisor-load"), filtersText(f),
                List.of("Supervisor", "Supervisees", "Primary", "Co-supervised", "Active", "At risk", "Flagged", "Completed"), rows);
    }

    /** FR-DLN-11 in report form. */
    private Table atRisk(Filters f) {
        List<StudentSummaryDto> list = studentService.summaries(students.findByStatuses(
                EnumSet.of(StudentStatus.AT_RISK, StudentStatus.FLAGGED_FOR_EXPULSION), f.programmeId(), f.cohortId()));
        List<List<String>> rows = list.stream().map(s -> List.of(s.name(), s.number(), s.programmeCode(), s.cohortName(),
                pretty(s.status()), s.approvedMilestones() + " / " + s.requiredMilestones(), Times.formatDate(s.checkpointDueOn()),
                Times.formatDate(s.programmeEndOn()), String.valueOf(s.daysToProgrammeEnd()), s.supervisorName() == null ? "" : s.supervisorName(),
                s.statusReason() == null ? "" : s.statusReason())).toList();
        return new Table("at-risk", REPORTS.get("at-risk"), filtersText(f),
                List.of("Student", "Number", "Programme", "Cohort", "Status", "Approved", "Checkpoint", "Programme end", "Days left",
                        "Supervisor", "Reason"), rows);
    }

    private List<String> filtersText(Filters f) {
        List<String> out = new ArrayList<>();
        if (f.programmeId() != null) out.add("Programme: " + programmeCode(f.programmeId()));
        out.add("Generated " + Times.formatDateTime(clock.instant()));
        return out;
    }

    private String programmeCode(UUID id) {
        return programmes.findById(id).map(Programme::getCode).orElse("?");
    }

    private static String count(Map<StudentStatus, Long> m, StudentStatus s) {
        return String.valueOf(m.getOrDefault(s, 0L));
    }

    private static String rate(long ok, long total) {
        return total == 0 ? "" : Math.round(ok * 100.0 / total) + "%";
    }

    private static String pretty(String code) {
        String s = code.replace('_', ' ').toLowerCase();
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
