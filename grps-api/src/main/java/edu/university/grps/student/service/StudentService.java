package edu.university.grps.student.service;

import edu.university.grps.audit.AuditService;
import edu.university.grps.common.ApiException;
import edu.university.grps.common.ErrorCode;
import edu.university.grps.common.Times;
import edu.university.grps.milestone.service.MilestoneService;
import edu.university.grps.notification.domain.NotificationCode;
import edu.university.grps.notification.service.NotificationService;
import edu.university.grps.programme.domain.Cohort;
import edu.university.grps.programme.domain.Programme;
import edu.university.grps.programme.repository.ProgrammeRepositories.CohortRepository;
import edu.university.grps.programme.repository.ProgrammeRepositories.ProgrammeRepository;
import edu.university.grps.student.api.StudentDtos.ExtensionDto;
import edu.university.grps.student.api.StudentDtos.StudentDetailDto;
import edu.university.grps.student.api.StudentDtos.StudentSummaryDto;
import edu.university.grps.student.api.StudentDtos.SupervisionDto;
import edu.university.grps.student.domain.Extension;
import edu.university.grps.student.domain.Student;
import edu.university.grps.student.domain.StudentStatus;
import edu.university.grps.student.domain.Supervision;
import edu.university.grps.student.repository.StudentRepositories.ExtensionRepository;
import edu.university.grps.student.repository.StudentRepositories.StudentRepository;
import edu.university.grps.student.repository.StudentRepositories.SupervisionRepository;
import edu.university.grps.user.domain.AppUser;
import edu.university.grps.user.domain.RoleCode;
import edu.university.grps.user.repository.AppUserRepository;
import edu.university.grps.user.repository.RoleRepository;
import edu.university.grps.user.service.UserService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class StudentService {

    private final StudentRepository students;
    private final SupervisionRepository supervisions;
    private final ExtensionRepository extensions;
    private final ProgrammeRepository programmes;
    private final CohortRepository cohorts;
    private final AppUserRepository users;
    private final RoleRepository roles;
    private final UserService userService;
    private final MilestoneService milestones;
    private final StudentAccess access;
    private final NotificationService notifications;
    private final AuditService audit;
    private final Clock clock;

    public StudentService(StudentRepository students, SupervisionRepository supervisions, ExtensionRepository extensions,
                          ProgrammeRepository programmes, CohortRepository cohorts, AppUserRepository users,
                          RoleRepository roles, UserService userService, MilestoneService milestones,
                          StudentAccess access, NotificationService notifications, AuditService audit, Clock clock) {
        this.students = students;
        this.supervisions = supervisions;
        this.extensions = extensions;
        this.programmes = programmes;
        this.cohorts = cohorts;
        this.users = users;
        this.roles = roles;
        this.userService = userService;
        this.milestones = milestones;
        this.access = access;
        this.notifications = notifications;
        this.audit = audit;
        this.clock = clock;
    }

    // Queries ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Page<StudentSummaryDto> search(String q, UUID programmeId, UUID cohortId, StudentStatus status, Pageable pageable) {
        String pattern = q == null || q.isBlank() ? "" : "%" + q.trim().toLowerCase() + "%";
        Page<Student> page = students.search(pattern, programmeId, cohortId, status, pageable);
        return new PageImpl<>(summaries(page.getContent()), pageable, page.getTotalElements());
    }

    @Transactional(readOnly = true)
    public StudentDetailDto detail(UUID id) {
        Student s = access.requireViewable(id);
        return new StudentDetailDto(summary(s),
                supervisions.findHistory(id).stream().map(SupervisionDto::of).toList(),
                extensions.findByStudentIdOrderByApprovedOnDesc(id).stream().map(ExtensionDto::of).toList());
    }

    @Transactional(readOnly = true)
    public StudentSummaryDto summary(Student s) {
        return summaries(List.of(s)).get(0);
    }

    @Transactional(readOnly = true)
    public List<StudentSummaryDto> summaries(Collection<Student> list) {
        if (list.isEmpty()) return List.of();
        List<UUID> ids = list.stream().map(Student::getId).toList();
        Map<UUID, Long> approved = milestones.approvedCounts(ids);
        Map<UUID, List<Supervision>> open = supervisions.findOpenByStudents(ids).stream()
                .collect(Collectors.groupingBy(sv -> sv.getStudent().getId()));
        LocalDate today = Times.today(clock);
        List<StudentSummaryDto> out = new ArrayList<>();
        for (Student s : list) {
            List<Supervision> svs = open.getOrDefault(s.getId(), List.of());
            Supervision primary = svs.stream().filter(x -> x.getType() == Supervision.Type.PRIMARY).findFirst().orElse(null);
            String co = svs.stream().filter(x -> x.getType() == Supervision.Type.CO)
                    .map(x -> x.getSupervisor().getFullName()).collect(Collectors.joining(", "));
            out.add(new StudentSummaryDto(s.getId(), s.getUser().getId(), s.getUser().getFullName(),
                    s.getUser().getStaffOrStudentNo(), s.getUser().getEmail(), s.getProgramme().getId(),
                    s.getProgramme().getCode(), s.getProgramme().getName(), s.getProgramme().getLevel().name(),
                    s.getCohort().getId(), s.getCohort().getName(), s.getEnrolledOn(), s.getCurrentSemester(),
                    s.getStatus().name(), s.getStatusReason(), approved.getOrDefault(s.getId(), 0L),
                    s.getProgramme().getRequiredMilestones(), s.getCheckpointDueOn(), s.getProgrammeEndOn(),
                    ChronoUnit.DAYS.between(today, s.getCheckpointDueOn()),
                    ChronoUnit.DAYS.between(today, s.getProgrammeEndOn()),
                    primary == null ? null : primary.getSupervisor().getId(),
                    primary == null ? null : primary.getSupervisor().getFullName(), co.isEmpty() ? null : co,
                    s.getUser().isActive(), s.getFlaggedAt()));
        }
        return out;
    }

    // Enrolment (FR-PRG-05, FR-USR-04) ---------------------------------------

    public record EnrolInput(UUID userId, String email, String number, String firstName, String lastName, String phone,
                             UUID programmeId, UUID cohortId, LocalDate enrolledOn, UUID supervisorUserId,
                             UUID coSupervisorUserId) {}

    /** Creates the student, generates LOCKED milestones from the programme template and assigns the supervisor. */
    public StudentSummaryDto enrol(EnrolInput in) {
        if (in.supervisorUserId() == null) {
            throw new ApiException(ErrorCode.SUPERVISOR_REQUIRED, "A student cannot be enrolled without a supervisor.");
        }
        Programme programme = programmes.findById(in.programmeId()).orElseThrow(() -> ApiException.notFound("Programme"));
        Cohort cohort = cohorts.findById(in.cohortId()).orElseThrow(() -> ApiException.notFound("Cohort"));
        if (!cohort.getProgramme().getId().equals(programme.getId())) {
            throw ApiException.validation("The cohort belongs to a different programme.");
        }
        if (in.enrolledOn() == null) throw ApiException.validation("Enrolment date is required.");
        AppUser supervisor = requireSupervisor(in.supervisorUserId());
        AppUser coSupervisor = in.coSupervisorUserId() == null ? null : requireSupervisor(in.coSupervisorUserId());

        AppUser user;
        if (in.userId() != null) {
            user = users.findById(in.userId()).orElseThrow(() -> ApiException.notFound("User"));
            if (students.findByUserId(user.getId()).isPresent()) {
                throw new ApiException(ErrorCode.DUPLICATE, "This account is already enrolled as a student.");
            }
            if (!user.hasRole(RoleCode.STUDENT)) {
                user.getRoles().add(roles.findByCode(RoleCode.STUDENT).orElseThrow());
            }
        } else {
            user = userService.create(new UserService.UserInput(in.email(), in.number(), in.firstName(), in.lastName(),
                    in.phone(), EnumSet.of(RoleCode.STUDENT), null));
        }
        if (user.getId().equals(supervisor.getId()) || (coSupervisor != null && user.getId().equals(coSupervisor.getId()))) {
            throw ApiException.rule("A student cannot supervise themselves.");
        }

        Student s = new Student();
        s.setUser(user);
        s.setProgramme(programme);
        s.setCohort(cohort);
        s.setEnrolledOn(in.enrolledOn());
        DueDates due = DueDates.compute(programme, in.enrolledOn(), 0);
        s.setCheckpointDueOn(due.checkpointDue());
        s.setProgrammeEndOn(due.programmeEnd());
        s.setCurrentSemester(DueDates.semesterOn(programme, in.enrolledOn(), Times.today(clock)));
        s.setStatus(StudentStatus.ACTIVE);
        s = students.save(s);

        openSupervision(s, supervisor, Supervision.Type.PRIMARY);
        if (coSupervisor != null) openSupervision(s, coSupervisor, Supervision.Type.CO);
        audit.record("STUDENT_ENROLLED", "Student", s.getId(), s.getId(), null,
                Map.of("programme", programme.getCode(), "cohort", cohort.getName(), "enrolledOn", in.enrolledOn(),
                        "supervisor", supervisor.getFullName()));
        milestones.generateFor(s);
        notifications.notify(List.of(user.getId(), supervisor.getId()), NotificationCode.N19, "Supervisor assigned",
                supervisor.getFullName() + " is the supervisor for " + user.getFullName() + ".", "/student");
        return summary(s);
    }

    private AppUser requireSupervisor(UUID userId) {
        AppUser sup = users.findById(userId).orElseThrow(() -> ApiException.notFound("Supervisor"));
        if (!sup.isActive() || !sup.hasRole(RoleCode.LECTURER) || !sup.hasRole(RoleCode.SUPERVISOR)) {
            throw new ApiException(ErrorCode.SUPERVISOR_REQUIRED,
                    sup.getFullName() + " must be an active account with both the Lecturer and Supervisor roles.");
        }
        return sup;
    }

    private Supervision openSupervision(Student s, AppUser supervisor, Supervision.Type type) {
        Supervision sv = new Supervision();
        sv.setStudent(s);
        sv.setSupervisor(supervisor);
        sv.setType(type);
        sv.setStartsOn(Times.today(clock));
        return supervisions.save(sv);
    }

    // Supervision changes (FR-USR-04, FR-USR-05) -----------------------------

    public record SupervisorInput(UUID supervisorUserId, Supervision.Type type) {}

    /** Replaces the primary supervisor (keeping history) or adds a co-supervisor. Sends N-19. */
    public StudentDetailDto assignSupervisor(UUID studentId, SupervisorInput in) {
        Student s = access.load(studentId);
        AppUser supervisor = requireSupervisor(in.supervisorUserId());
        if (supervisor.getId().equals(s.getUser().getId())) throw ApiException.rule("A student cannot supervise themselves.");
        Supervision.Type type = in.type() == null ? Supervision.Type.PRIMARY : in.type();
        List<Supervision> open = supervisions.findOpenByStudent(studentId);
        if (open.stream().anyMatch(x -> x.getSupervisor().getId().equals(supervisor.getId()))) {
            throw new ApiException(ErrorCode.DUPLICATE, supervisor.getFullName() + " already supervises this student.");
        }
        List<UUID> recipients = new ArrayList<>(List.of(s.getUser().getId(), supervisor.getId()));
        String previous = null;
        if (type == Supervision.Type.PRIMARY) {
            for (Supervision old : open) {
                if (old.getType() == Supervision.Type.PRIMARY) {
                    old.setEndsOn(Times.today(clock));
                    recipients.add(old.getSupervisor().getId());
                    previous = old.getSupervisor().getFullName();
                }
            }
            supervisions.flush();
        }
        openSupervision(s, supervisor, type);
        audit.record("SUPERVISOR_ASSIGNED", "Student", s.getId(), s.getId(),
                previous == null ? null : Map.of("supervisor", previous),
                Map.of("supervisor", supervisor.getFullName(), "type", type.name()));
        String body = type == Supervision.Type.PRIMARY
                ? supervisor.getFullName() + " is now the supervisor for " + s.getUser().getFullName()
                        + (previous == null ? "." : ", replacing " + previous + ".")
                : supervisor.getFullName() + " is now a co-supervisor for " + s.getUser().getFullName() + ".";
        notifications.notify(recipients, NotificationCode.N19, "Supervisor changed", body, "/student");
        return detail(studentId);
    }

    public StudentDetailDto endCoSupervision(UUID studentId, UUID supervisionId) {
        Supervision sv = supervisions.findById(supervisionId)
                .filter(x -> x.getStudent().getId().equals(studentId))
                .orElseThrow(() -> ApiException.notFound("Supervision"));
        if (sv.getType() != Supervision.Type.CO) {
            throw ApiException.rule("The primary supervisor can only be replaced, not removed (BR-17).");
        }
        if (sv.getEndsOn() == null) {
            sv.setEndsOn(Times.today(clock));
            audit.record("CO_SUPERVISOR_REMOVED", "Student", studentId, studentId, null,
                    Map.of("supervisor", sv.getSupervisor().getFullName()));
        }
        return detail(studentId);
    }

    // Extensions (FR-PRG-06) ---------------------------------------------------

    public record ExtensionInput(int months, String reason, String approvedBy, LocalDate approvedOn) {}

    public StudentDetailDto recordExtension(UUID studentId, ExtensionInput in) {
        Student s = access.load(studentId);
        if (in.months() < 1 || in.months() > 24) throw ApiException.validation("Extension must be between 1 and 24 months.");
        if (in.reason() == null || in.reason().isBlank()) throw ApiException.validation("A reason is required.");
        if (in.approvedBy() == null || in.approvedBy().isBlank()) throw ApiException.validation("Record who approved the extension.");
        Extension e = new Extension();
        e.setStudent(s);
        e.setMonths(in.months());
        e.setReason(in.reason().trim());
        e.setApprovedBy(in.approvedBy().trim());
        e.setApprovedOn(in.approvedOn() == null ? Times.today(clock) : in.approvedOn());
        extensions.save(e);
        extensions.flush();
        LocalDate oldCheckpoint = s.getCheckpointDueOn();
        LocalDate oldEnd = s.getProgrammeEndOn();
        recomputeDueDates(s);
        audit.record("EXTENSION_RECORDED", "Student", s.getId(), s.getId(),
                Map.of("checkpointDueOn", oldCheckpoint, "programmeEndOn", oldEnd),
                Map.of("months", in.months(), "reason", in.reason(), "checkpointDueOn", s.getCheckpointDueOn(),
                        "programmeEndOn", s.getProgrammeEndOn()));
        notifications.notify(access.studentAndSupervisors(s), NotificationCode.N15, "Deadlines extended",
                "An extension of " + in.months() + " month(s) was recorded. Checkpoint: "
                        + Times.formatDate(s.getCheckpointDueOn()) + ". Programme end: " + Times.formatDate(s.getProgrammeEndOn()) + ".",
                "/student", false);
        return detail(studentId);
    }

    public void recomputeDueDates(Student s) {
        DueDates due = DueDates.compute(s.getProgramme(), s.getEnrolledOn(), extensions.totalMonths(s.getId()));
        s.setCheckpointDueOn(due.checkpointDue());
        s.setProgrammeEndOn(due.programmeEnd());
    }

    // Expulsion decisions (FR-DLN-08, BR-15) ---------------------------------

    public StudentDetailDto confirmExpulsion(UUID studentId, String reason) {
        requireReason(reason);
        Student s = access.load(studentId);
        if (s.getStatus() != StudentStatus.FLAGGED_FOR_EXPULSION) {
            throw ApiException.invalidState("Only a student flagged for expulsion can be expelled.");
        }
        s.setStatus(StudentStatus.EXPELLED);
        s.setStatusReason(reason.trim());
        s.setExpelledAt(clock.instant());
        milestones.cancelOpenAttempts(s, "Student expelled");
        audit.record("EXPULSION_CONFIRMED", "Student", s.getId(), s.getId(),
                Map.of("status", StudentStatus.FLAGGED_FOR_EXPULSION.name()), Map.of("status", "EXPELLED", "reason", reason));
        notifications.notify(access.studentAndSupervisors(s), NotificationCode.N18, "Expulsion confirmed",
                "The graduate school has confirmed the expulsion of " + s.getUser().getFullName() + ". Reason: " + reason.trim()
                        + ". Sign-in will be deactivated after 30 days; the record is kept.", "/student");
        return detail(studentId);
    }

    /** Clears the flag; the student returns to ACTIVE, or AT_RISK if the checkpoint was missed. */
    public StudentDetailDto clearFlag(UUID studentId, String reason) {
        requireReason(reason);
        Student s = access.load(studentId);
        if (s.getStatus() != StudentStatus.FLAGGED_FOR_EXPULSION) throw ApiException.invalidState("This student is not flagged.");
        recomputeDueDates(s);
        long approved = milestones.approvedCounts(List.of(s.getId())).getOrDefault(s.getId(), 0L);
        LocalDate today = Times.today(clock);
        boolean checkpointMissed = !today.isBefore(s.getCheckpointDueOn()) && approved < s.getProgramme().getCheckpointMilestones();
        StudentStatus next = checkpointMissed ? StudentStatus.AT_RISK : StudentStatus.ACTIVE;
        s.setStatus(next);
        s.setStatusReason(reason.trim());
        s.setFlaggedAt(null);
        audit.record("EXPULSION_FLAG_CLEARED", "Student", s.getId(), s.getId(),
                Map.of("status", StudentStatus.FLAGGED_FOR_EXPULSION.name()), Map.of("status", next.name(), "reason", reason,
                        "programmeEndOn", s.getProgrammeEndOn()));
        notifications.notify(access.studentAndSupervisors(s), NotificationCode.N18, "Expulsion flag cleared",
                "The expulsion flag on " + s.getUser().getFullName() + " was cleared. Status: "
                        + next.name().replace('_', ' ').toLowerCase() + ". Programme end: " + Times.formatDate(s.getProgrammeEndOn()) + ".",
                "/student");
        return detail(studentId);
    }

    public StudentDetailDto withdraw(UUID studentId, String reason) {
        requireReason(reason);
        Student s = access.load(studentId);
        if (s.isClosed()) throw ApiException.invalidState("This record is already closed.");
        StudentStatus old = s.getStatus();
        s.setStatus(StudentStatus.WITHDRAWN);
        s.setStatusReason(reason.trim());
        milestones.cancelOpenAttempts(s, "Student withdrawn");
        audit.record("STUDENT_WITHDRAWN", "Student", s.getId(), s.getId(), Map.of("status", old.name()),
                Map.of("status", "WITHDRAWN", "reason", reason));
        return detail(studentId);
    }

    private static void requireReason(String reason) {
        if (reason == null || reason.isBlank()) throw ApiException.validation("A written reason is required.");
    }

    public static final Set<StudentStatus> MONITORED = EnumSet.of(StudentStatus.ACTIVE, StudentStatus.AT_RISK);
}
