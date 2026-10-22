package edu.university.grps.milestone.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edu.university.grps.audit.AuditService;
import edu.university.grps.comment.service.CommentService;
import edu.university.grps.common.ApiException;
import edu.university.grps.common.ErrorCode;
import edu.university.grps.evidence.service.EvidenceService;
import edu.university.grps.milestone.api.MilestoneDtos.MilestoneDto;
import edu.university.grps.milestone.domain.MilestoneStatus;
import edu.university.grps.milestone.domain.StudentMilestone;
import edu.university.grps.milestone.repository.MilestoneRepositories.PresentationAttemptRepository;
import edu.university.grps.milestone.repository.MilestoneRepositories.StudentMilestoneRepository;
import edu.university.grps.notification.service.NotificationService;
import edu.university.grps.student.domain.Student;
import edu.university.grps.student.domain.StudentStatus;
import edu.university.grps.student.service.StudentAccess;
import edu.university.grps.support.Fixtures;
import edu.university.grps.user.domain.RoleCode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VerificationServiceTest {

    @Mock StudentMilestoneRepository milestones;
    @Mock PresentationAttemptRepository attempts;
    @Mock MilestoneService milestoneService;
    @Mock EvidenceService evidence;
    @Mock CommentService comments;
    @Mock StudentAccess access;
    @Mock NotificationService notifications;
    @Mock AuditService audit;

    private VerificationService service;
    private Student student;

    @BeforeEach
    void setUp() {
        service = new VerificationService(milestones, attempts, milestoneService, evidence, comments, access, notifications, audit,
                Clock.fixed(Instant.parse("2026-10-19T08:00:00Z"), ZoneOffset.UTC));
        student = Fixtures.student(StudentStatus.ACTIVE);
        Fixtures.signIn(UUID.randomUUID(), RoleCode.ADMIN);
    }

    @AfterEach
    void tearDown() {
        Fixtures.signOut();
    }

    private MilestoneDto view(StudentMilestone m) {
        return new MilestoneDto(m.getId(), student.getId(), m.getSequenceNo(), m.label(), m.getTitle(), false, true,
                m.getStatus().name(), null, null, null, null, 1, 0, 3, null, List.of());
    }

    @Test
    void BR_07_masters_milestone_needs_evidence_before_approval() {
        StudentMilestone m = Fixtures.milestone(student, 1, MilestoneStatus.AWAITING_VERIFICATION);
        when(milestones.lockById(m.getId())).thenReturn(Optional.of(m));
        when(evidence.countCurrentClean(m.getId())).thenReturn(0L);

        assertThatThrownBy(() -> service.approve(m.getId()))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.EVIDENCE_REQUIRED));
        assertThat(m.getStatus()).isEqualTo(MilestoneStatus.AWAITING_VERIFICATION);
    }

    @Test
    void BR_06_approval_counts_the_milestone_and_unlocks_the_next() {
        StudentMilestone m = Fixtures.milestone(student, 1, MilestoneStatus.AWAITING_VERIFICATION);
        StudentMilestone next = Fixtures.milestone(student, 2, MilestoneStatus.LOCKED);
        when(milestones.lockById(m.getId())).thenReturn(Optional.of(m));
        when(evidence.countCurrentClean(m.getId())).thenReturn(1L);
        when(milestones.findByStudentIdAndSequenceNo(student.getId(), 2)).thenReturn(Optional.of(next));
        when(milestoneService.buildViews(student)).thenAnswer(inv -> List.of(view(m)));

        service.approve(m.getId());

        assertThat(m.getStatus()).isEqualTo(MilestoneStatus.APPROVED);
        assertThat(m.getApprovedAt()).isNotNull();
        verify(milestoneService).makeAvailable(next, student);
    }

    @Test
    void BR_06_only_milestones_awaiting_verification_can_be_approved() {
        StudentMilestone m = Fixtures.milestone(student, 1, MilestoneStatus.SCHEDULED);
        when(milestones.lockById(m.getId())).thenReturn(Optional.of(m));

        assertThatThrownBy(() -> service.approve(m.getId()))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.INVALID_STATE));
    }

    @Test
    void FR_VER_03_returning_needs_a_comment() {
        assertThatThrownBy(() -> service.returnMilestone(UUID.randomUUID(), "  "))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_FAILED));
    }

    @Test
    void FR_VER_03_returned_milestone_has_status_returned() {
        StudentMilestone m = Fixtures.milestone(student, 1, MilestoneStatus.AWAITING_VERIFICATION);
        when(milestones.lockById(m.getId())).thenReturn(Optional.of(m));
        when(milestoneService.buildViews(student)).thenAnswer(inv -> List.of(view(m)));

        service.returnMilestone(m.getId(), "Panel sheet unsigned");

        assertThat(m.getStatus()).isEqualTo(MilestoneStatus.RETURNED);
        verify(comments).record(any(), any(), any());
    }
}
