package edu.university.grps.milestone.service;

import edu.university.grps.audit.AuditService;
import edu.university.grps.comment.domain.ReviewComment;
import edu.university.grps.comment.service.CommentService;
import edu.university.grps.common.ApiException;
import edu.university.grps.common.ErrorCode;
import edu.university.grps.evidence.service.EvidenceService;
import edu.university.grps.milestone.api.MilestoneDtos.MilestoneDto;
import edu.university.grps.milestone.api.MilestoneDtos.VerificationItemDto;
import edu.university.grps.milestone.domain.MilestoneStatus;
import edu.university.grps.milestone.domain.PresentationAttempt;
import edu.university.grps.milestone.domain.StudentMilestone;
import edu.university.grps.milestone.repository.MilestoneRepositories.PresentationAttemptRepository;
import edu.university.grps.milestone.repository.MilestoneRepositories.StudentMilestoneRepository;
import edu.university.grps.notification.domain.NotificationCode;
import edu.university.grps.notification.service.NotificationService;
import edu.university.grps.security.CurrentUser;
import edu.university.grps.student.domain.Student;
import edu.university.grps.student.service.StudentAccess;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Admin verification of passed milestones (section 9). A passed presentation counts only once approved (BR-06). */
@Service
@Transactional
public class VerificationService {

    private final StudentMilestoneRepository milestones;
    private final PresentationAttemptRepository attempts;
    private final MilestoneService milestoneService;
    private final EvidenceService evidence;
    private final CommentService comments;
    private final StudentAccess access;
    private final NotificationService notifications;
    private final AuditService audit;
    private final Clock clock;

    public VerificationService(StudentMilestoneRepository milestones, PresentationAttemptRepository attempts,
                               MilestoneService milestoneService, EvidenceService evidence, CommentService comments,
                               StudentAccess access, NotificationService notifications, AuditService audit, Clock clock) {
        this.milestones = milestones;
        this.attempts = attempts;
        this.milestoneService = milestoneService;
        this.evidence = evidence;
        this.comments = comments;
        this.access = access;
        this.notifications = notifications;
        this.audit = audit;
        this.clock = clock;
    }

    /** FR-VER-01: oldest first, with outcome date, evidence count and days waiting. */
    @Transactional(readOnly = true)
    public List<VerificationItemDto> queue() {
        List<StudentMilestone> waiting = milestones.findByStatusOrderByUpdatedAtAsc(MilestoneStatus.AWAITING_VERIFICATION);
        Map<UUID, Long> counts = evidence.countCurrent(waiting.stream().map(StudentMilestone::getId).toList());
        Instant now = clock.instant();
        return waiting.stream().map(m -> {
            Student s = m.getStudent();
            PresentationAttempt passed = attempts.findByMilestoneIdOrderByAttemptNo(m.getId()).stream()
                    .filter(a -> a.getOutcome() == PresentationAttempt.Outcome.PASSED)
                    .reduce((first, second) -> second).orElse(null);
            Instant outcomeAt = passed == null ? m.getUpdatedAt() : passed.getRecordedAt();
            return new VerificationItemDto(m.getId(), s.getId(), s.getUser().getFullName(), s.getUser().getStaffOrStudentNo(),
                    s.getProgramme().getCode(), m.getSequenceNo(), m.getTitle(), m.isEvidenceRequired(), m.getStatus().name(),
                    outcomeAt, counts.getOrDefault(m.getId(), 0L), ChronoUnit.DAYS.between(outcomeAt, now),
                    passed == null ? null : passed.getId(), passed == null ? null : passed.getScore(),
                    passed == null ? null : passed.getPanelRemarks());
        }).sorted(Comparator.comparing(VerificationItemDto::outcomeAt)).toList();
    }

    /**
     * FR-VER-02 / FR-MIL-11: sets APPROVED and makes the next milestone AVAILABLE in the same transaction.
     * BR-07: milestones that need evidence cannot be approved until a clean file exists.
     */
    public MilestoneDto approve(UUID milestoneId) {
        StudentMilestone m = milestones.lockById(milestoneId).orElseThrow(() -> ApiException.notFound("Milestone"));
        Student s = m.getStudent();
        if (m.getStatus() != MilestoneStatus.AWAITING_VERIFICATION) {
            throw ApiException.invalidState(m.label() + " is not awaiting verification.");
        }
        access.requireWritable(s);
        if (m.isEvidenceRequired() && evidence.countCurrentClean(m.getId()) == 0) {
            throw new ApiException(ErrorCode.EVIDENCE_REQUIRED,
                    m.label() + " needs at least one evidence file before it can be approved.");
        }
        m.setStatus(MilestoneStatus.APPROVED);
        m.setApprovedBy(CurrentUser.id());
        m.setApprovedAt(clock.instant());
        StudentMilestone next = milestones.findByStudentIdAndSequenceNo(s.getId(), m.getSequenceNo() + 1).orElse(null);
        audit.record("MILESTONE_APPROVED", "StudentMilestone", m.getId(), s.getId(),
                Map.of("status", MilestoneStatus.AWAITING_VERIFICATION.name()), Map.of("status", "APPROVED", "milestone", m.label()));
        String body;
        String link;
        if (m.isFinalPreDefense()) {
            body = m.label() + ", the final pre-defense, is approved. Thesis submission is now open.";
            link = "/student/thesis";
        } else {
            body = m.label() + " (" + m.getTitle() + ") is approved."
                    + (next == null ? "" : " " + next.label() + " is now available for scheduling.");
            link = "/student/achievements";
        }
        notifications.notify(access.studentAndSupervisors(s), NotificationCode.N07, m.label() + " approved", body, link);
        if (next != null && next.getStatus() == MilestoneStatus.LOCKED) milestoneService.makeAvailable(next, s);
        return milestoneService.buildViews(s).stream().filter(x -> x.id().equals(milestoneId)).findFirst().orElseThrow();
    }

    /** FR-VER-03: comment mandatory; the student can upload again, which moves it back to the queue. */
    public MilestoneDto returnMilestone(UUID milestoneId, String comment) {
        if (comment == null || comment.isBlank()) throw ApiException.validation("Explain what needs to change.");
        StudentMilestone m = milestones.lockById(milestoneId).orElseThrow(() -> ApiException.notFound("Milestone"));
        Student s = m.getStudent();
        if (m.getStatus() != MilestoneStatus.AWAITING_VERIFICATION) {
            throw ApiException.invalidState(m.label() + " is not awaiting verification.");
        }
        m.setStatus(MilestoneStatus.RETURNED);
        comments.record(ReviewComment.TargetType.MILESTONE, m.getId(), comment.trim());
        audit.record("MILESTONE_RETURNED", "StudentMilestone", m.getId(), s.getId(),
                Map.of("status", MilestoneStatus.AWAITING_VERIFICATION.name()), Map.of("status", "RETURNED", "comment", comment.trim()));
        notifications.notify(access.studentAndSupervisors(s), NotificationCode.N08, m.label() + " returned",
                m.label() + " was returned by the graduate school: \"" + comment.trim() + "\" Upload the corrected evidence to resubmit.",
                "/student/evidence");
        return milestoneService.buildViews(s).stream().filter(x -> x.id().equals(milestoneId)).findFirst().orElseThrow();
    }
}
