package edu.university.grps.deadline.service;

import edu.university.grps.audit.AuditService;
import edu.university.grps.common.Times;
import edu.university.grps.deadline.domain.DeadlineNotice;
import edu.university.grps.deadline.repository.DeadlineNoticeRepository;
import edu.university.grps.milestone.service.MilestoneService;
import edu.university.grps.notification.domain.NotificationCode;
import edu.university.grps.notification.service.NotificationService;
import edu.university.grps.programme.domain.Programme;
import edu.university.grps.student.domain.Student;
import edu.university.grps.student.domain.StudentStatus;
import edu.university.grps.student.repository.StudentRepositories.StudentRepository;
import edu.university.grps.student.service.DueDates;
import edu.university.grps.student.service.StudentAccess;
import edu.university.grps.student.service.StudentService;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Nightly deadline evaluation (section 14, FR-DLN-02..06). Warns at months 12, 15 and 17 before the checkpoint
 * and at 18, 21 and 23 before programme end; sets AT_RISK at the checkpoint and FLAGGED_FOR_EXPULSION at
 * programme end. A person always makes the final expulsion decision (BR-15).
 */
@Service
public class DeadlineService {

    private static final Logger log = LoggerFactory.getLogger(DeadlineService.class);
    static final int[] WARNING_MONTHS_BEFORE = {6, 3, 1};
    static final String CHECKPOINT_REASON = "Checkpoint missed: ";

    public record Summary(int evaluated, int warnings, int atRisk, int flagged, int restored, int deactivated) {}

    private final StudentRepository students;
    private final DeadlineNoticeRepository notices;
    private final StudentService studentService;
    private final MilestoneService milestones;
    private final StudentAccess access;
    private final NotificationService notifications;
    private final AuditService audit;
    private final TransactionTemplate tx;
    private final Clock clock;

    public DeadlineService(StudentRepository students, DeadlineNoticeRepository notices, StudentService studentService,
                           MilestoneService milestones, StudentAccess access, NotificationService notifications,
                           AuditService audit, TransactionTemplate tx, Clock clock) {
        this.students = students;
        this.notices = notices;
        this.studentService = studentService;
        this.milestones = milestones;
        this.access = access;
        this.notifications = notifications;
        this.audit = audit;
        this.tx = tx;
        this.clock = clock;
    }

    /** Evaluates every ACTIVE and AT_RISK student, one transaction each so one bad record cannot stop the run. */
    public Summary evaluateAll() {
        List<UUID> ids = tx.execute(s -> students.findByStatusIn(StudentService.MONITORED).stream().map(Student::getId).toList());
        int[] counts = new int[5];
        for (UUID id : ids == null ? List.<UUID>of() : ids) {
            try {
                tx.executeWithoutResult(s -> evaluate(students.findWithDetailsById(id).orElseThrow(), counts));
            } catch (RuntimeException e) {
                log.error("Deadline evaluation failed for student {}", id, e);
            }
        }
        int deactivated = deactivateExpelled();
        Summary summary = new Summary(ids == null ? 0 : ids.size(), counts[0], counts[1], counts[2], counts[3], deactivated);
        log.info("Deadline evaluation finished: {}", summary);
        return summary;
    }

    void evaluate(Student s, int[] counts) {
        if (!StudentService.MONITORED.contains(s.getStatus())) return;
        Programme p = s.getProgramme();
        LocalDate today = Times.today(clock);
        studentService.recomputeDueDates(s);
        s.setCurrentSemester(DueDates.semesterOn(p, s.getEnrolledOn(), today));
        long approved = milestones.approvedCounts(List.of(s.getId())).getOrDefault(s.getId(), 0L);
        int checkpointNeed = p.getCheckpointMilestones();
        int required = p.getRequiredMilestones();
        List<UUID> recipients = new ArrayList<>(access.studentAndSupervisors(s));
        recipients.addAll(access.admins());
        LocalDate checkpoint = s.getCheckpointDueOn();
        LocalDate end = s.getProgrammeEndOn();

        // FR-DLN-06: programme end with outstanding milestones flags for expulsion.
        if (!today.isBefore(end) && approved < required) {
            StudentStatus old = s.getStatus();
            s.setStatus(StudentStatus.FLAGGED_FOR_EXPULSION);
            s.setFlaggedAt(clock.instant());
            s.setStatusReason("Programme ended on " + Times.formatDate(end) + " with " + (required - approved)
                    + " milestone(s) outstanding.");
            audit.record("FLAGGED_FOR_EXPULSION", "Student", s.getId(), s.getId(), Map.of("status", old.name()),
                    Map.of("status", "FLAGGED_FOR_EXPULSION", "approved", approved, "required", required));
            notifications.notify(recipients, NotificationCode.N17, s.getUser().getFullName() + " flagged for expulsion",
                    s.getStatusReason() + " The graduate school will review the case before any decision.", "/admin/at-risk");
            counts[2]++;
            return;
        }

        // FR-DLN-04: checkpoint missed sets AT_RISK.
        if (!today.isBefore(checkpoint) && approved < checkpointNeed && s.getStatus() == StudentStatus.ACTIVE) {
            s.setStatus(StudentStatus.AT_RISK);
            s.setStatusReason(CHECKPOINT_REASON + approved + " of " + checkpointNeed + " presentations approved by "
                    + Times.formatDate(checkpoint) + ".");
            audit.record("STATUS_AT_RISK", "Student", s.getId(), s.getId(), Map.of("status", "ACTIVE"),
                    Map.of("status", "AT_RISK", "approved", approved, "checkpoint", checkpoint));
            notifications.notify(recipients, NotificationCode.N16, s.getUser().getFullName() + " is at risk",
                    s.getStatusReason() + " All " + required + " must be approved by " + Times.formatDate(end) + ".", "/admin/at-risk");
            counts[1]++;
        }

        // A checkpoint AT_RISK clears once the checkpoint requirement is met.
        if (s.getStatus() == StudentStatus.AT_RISK && approved >= checkpointNeed && s.getStatusReason() != null
                && s.getStatusReason().startsWith(CHECKPOINT_REASON)) {
            s.setStatus(StudentStatus.ACTIVE);
            s.setStatusReason("Checkpoint requirement met.");
            audit.record("STATUS_RESTORED", "Student", s.getId(), s.getId(), Map.of("status", "AT_RISK"), Map.of("status", "ACTIVE"));
            counts[3]++;
        }

        // FR-DLN-03: checkpoint warnings at 6, 3 and 1 month(s) before (months 12, 15, 17).
        if (today.isBefore(checkpoint) && approved < checkpointNeed) {
            if (warnOnce(s, "CHK", checkpoint, today, recipients, "Checkpoint in " + daysText(today, checkpoint),
                    s.getUser().getFullName() + " has " + approved + " of " + checkpointNeed + " presentations approved. "
                            + checkpointNeed + " must be approved by the checkpoint on " + Times.formatDate(checkpoint) + ".")) {
                counts[0]++;
            }
        }
        // FR-DLN-05: programme-end warnings at 6, 3 and 1 month(s) before (months 18, 21, 23).
        if (today.isBefore(end) && approved < required) {
            if (warnOnce(s, "END", end, today, recipients, "Programme ends in " + daysText(today, end),
                    s.getUser().getFullName() + " has " + (required - approved) + " milestone(s) outstanding. All " + required
                            + " must be approved by " + Times.formatDate(end) + " or the record is flagged for expulsion.")) {
                counts[0]++;
            }
        }
    }

    /** Sends the latest due warning once; earlier missed ones are marked done without sending. */
    private boolean warnOnce(Student s, String kind, LocalDate due, LocalDate today, List<UUID> recipients, String title, String body) {
        Integer latest = null;
        for (int months : WARNING_MONTHS_BEFORE) {
            if (!today.isBefore(due.minusMonths(months))) latest = months;
        }
        if (latest == null) return false;
        boolean sent = false;
        for (int months : WARNING_MONTHS_BEFORE) {
            if (months < latest) continue;
            String key = kind + "-" + months + "-" + due;
            if (notices.existsByStudentIdAndNoticeKey(s.getId(), key)) continue;
            DeadlineNotice n = new DeadlineNotice();
            n.setStudentId(s.getId());
            n.setNoticeKey(key);
            notices.save(n);
            if (months == latest) {
                notifications.notify(recipients, NotificationCode.N15, title, body, "/student");
                sent = true;
            }
        }
        return sent;
    }

    /** FR-DLN-08: expelled students lose sign-in 30 days after confirmation; the record is kept. */
    private int deactivateExpelled() {
        Integer n = tx.execute(s -> {
            int count = 0;
            for (Student st : students.findByStatusIn(EnumSet.of(StudentStatus.EXPELLED))) {
                if (st.getExpelledAt() != null && st.getExpelledAt().plus(Duration.ofDays(30)).isBefore(clock.instant())
                        && st.getUser().isActive()) {
                    st.getUser().setActive(false);
                    audit.record("USER_DEACTIVATED", "AppUser", st.getUser().getId(), st.getId(), Map.of("active", true),
                            Map.of("active", false, "reason", "30 days after expulsion"));
                    count++;
                }
            }
            return count;
        });
        return n == null ? 0 : n;
    }

    /** MilestoneUnlockJob: opens milestone 1 for students entering semester 2. */
    public int unlockFirstMilestones() {
        List<UUID> ids = tx.execute(s -> students.findByStatusIn(StudentService.MONITORED).stream().map(Student::getId).toList());
        int opened = 0;
        for (UUID id : ids == null ? List.<UUID>of() : ids) {
            Boolean done = tx.execute(s -> milestones.openFirstIfDue(students.findWithDetailsById(id).orElseThrow()));
            if (Boolean.TRUE.equals(done)) opened++;
        }
        return opened;
    }

    private static String daysText(LocalDate today, LocalDate due) {
        long days = ChronoUnit.DAYS.between(today, due);
        if (days > 45) return Math.round(days / 30.4) + " months";
        return days + " day" + (days == 1 ? "" : "s");
    }
}
