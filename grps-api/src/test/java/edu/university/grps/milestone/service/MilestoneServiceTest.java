package edu.university.grps.milestone.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edu.university.grps.audit.AuditService;
import edu.university.grps.common.ApiException;
import edu.university.grps.common.ErrorCode;
import edu.university.grps.evidence.service.EvidenceService;
import edu.university.grps.milestone.domain.MilestoneStatus;
import edu.university.grps.milestone.domain.PresentationAttempt;
import edu.university.grps.milestone.domain.StudentMilestone;
import edu.university.grps.milestone.repository.MilestoneRepositories.PresentationAttemptRepository;
import edu.university.grps.milestone.repository.MilestoneRepositories.StudentMilestoneRepository;
import edu.university.grps.milestone.service.MilestoneService.OutcomeInput;
import edu.university.grps.milestone.service.MilestoneService.ScheduleInput;
import edu.university.grps.notification.service.NotificationService;
import edu.university.grps.programme.domain.Venue;
import edu.university.grps.programme.repository.ProgrammeRepositories.MilestoneTemplateRepository;
import edu.university.grps.programme.repository.ProgrammeRepositories.VenueRepository;
import edu.university.grps.settings.service.SettingsService;
import edu.university.grps.student.domain.Student;
import edu.university.grps.student.domain.StudentStatus;
import edu.university.grps.student.service.StudentAccess;
import edu.university.grps.support.Fixtures;
import edu.university.grps.user.domain.RoleCode;
import edu.university.grps.user.repository.AppUserRepository;
import edu.university.grps.user.service.UserDirectory;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
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
class MilestoneServiceTest {

    @Mock StudentMilestoneRepository milestones;
    @Mock PresentationAttemptRepository attempts;
    @Mock MilestoneTemplateRepository templates;
    @Mock VenueRepository venues;
    @Mock AppUserRepository users;
    @Mock StudentAccess access;
    @Mock EvidenceService evidence;
    @Mock SettingsService settings;
    @Mock NotificationService notifications;
    @Mock UserDirectory directory;
    @Mock AuditService audit;

    private final Clock clock = Clock.fixed(Instant.parse("2026-10-19T08:00:00Z"), ZoneOffset.UTC);
    private MilestoneService service;
    private Student student;
    private Venue venue;

    @BeforeEach
    void setUp() {
        service = new MilestoneService(milestones, attempts, templates, venues, users, access, evidence, settings, notifications,
                directory, audit, clock);
        student = Fixtures.student(StudentStatus.ACTIVE);
        venue = Fixtures.withId(new Venue());
        venue.setName("Room A");
        venue.setActive(true);
        lenient().when(settings.integer(SettingsService.MAX_ATTEMPTS)).thenReturn(3);
        lenient().when(directory.names(any())).thenReturn(new HashMap<>());
        Fixtures.signIn(UUID.randomUUID(), RoleCode.ADMIN);
    }

    @AfterEach
    void tearDown() {
        Fixtures.signOut();
    }

    private ScheduleInput tomorrow() {
        return new ScheduleInput(Instant.parse("2026-10-20T09:00:00Z"), 45, venue.getId(), null, List.of());
    }

    @Test
    void BR_03_milestone_cannot_be_scheduled_until_the_previous_one_is_approved() {
        StudentMilestone first = Fixtures.milestone(student, 1, MilestoneStatus.AVAILABLE);
        StudentMilestone second = Fixtures.milestone(student, 2, MilestoneStatus.LOCKED);
        when(milestones.lockById(second.getId())).thenReturn(Optional.of(second));
        when(milestones.findByStudentIdOrderBySequenceNo(student.getId())).thenReturn(List.of(first, second));

        assertThatThrownBy(() -> service.schedule(second.getId(), tomorrow()))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> {
                    assertThat(((ApiException) e).code()).isEqualTo(ErrorCode.MILESTONE_LOCKED);
                    assertThat(e.getMessage()).isEqualTo("Presentation 2 cannot be scheduled until Presentation 1 is approved.");
                });
        verify(attempts, never()).save(any());
    }

    @Test
    void BR_03_available_milestone_is_scheduled() {
        StudentMilestone first = Fixtures.milestone(student, 1, MilestoneStatus.AVAILABLE);
        when(milestones.lockById(first.getId())).thenReturn(Optional.of(first));
        when(venues.findById(venue.getId())).thenReturn(Optional.of(venue));
        when(attempts.studentClashes(any(), any(), any(), any())).thenReturn(List.of());
        when(attempts.venueClashes(any(), any(), any(), any())).thenReturn(List.of());
        when(attempts.save(any())).thenAnswer(inv -> Fixtures.withId(inv.getArgument(0)));

        var result = service.schedule(first.getId(), tomorrow());

        assertThat(first.getStatus()).isEqualTo(MilestoneStatus.SCHEDULED);
        assertThat(result.attempt().attemptNo()).isEqualTo(1);
    }

    @Test
    void FR_MIL_04_student_clash_is_refused() {
        StudentMilestone first = Fixtures.milestone(student, 1, MilestoneStatus.AVAILABLE);
        when(milestones.lockById(first.getId())).thenReturn(Optional.of(first));
        when(venues.findById(venue.getId())).thenReturn(Optional.of(venue));
        when(attempts.studentClashes(any(), any(), any(), any())).thenReturn(List.of(new PresentationAttempt()));

        assertThatThrownBy(() -> service.schedule(first.getId(), tomorrow()))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.SCHEDULE_CLASH));
    }

    @Test
    void BR_19_failed_milestones_can_be_reattempted_only_up_to_the_maximum() {
        StudentMilestone first = Fixtures.milestone(student, 1, MilestoneStatus.AVAILABLE);
        when(milestones.lockById(first.getId())).thenReturn(Optional.of(first));
        when(attempts.countFailed(first.getId())).thenReturn(3);

        assertThatThrownBy(() -> service.schedule(first.getId(), tomorrow()))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.MAX_ATTEMPTS_REACHED));
    }

    @Test
    void BR_20_closed_students_cannot_be_scheduled() {
        Student expelled = Fixtures.student(StudentStatus.EXPELLED);
        StudentMilestone first = Fixtures.milestone(expelled, 1, MilestoneStatus.AVAILABLE);
        when(milestones.lockById(first.getId())).thenReturn(Optional.of(first));
        doThrow(ApiException.invalidState("closed")).when(access).requireWritable(expelled);

        assertThatThrownBy(() -> service.schedule(first.getId(), tomorrow())).isInstanceOf(ApiException.class);
        verify(attempts, never()).save(any());
    }

    @Test
    void FR_MIL_07_remarks_are_required_for_a_failed_outcome() {
        StudentMilestone first = Fixtures.milestone(student, 1, MilestoneStatus.SCHEDULED);
        PresentationAttempt a = Fixtures.withId(new PresentationAttempt());
        a.setMilestone(first);
        a.setScheduledStart(Instant.parse("2026-10-18T09:00:00Z"));
        a.setScheduledEnd(Instant.parse("2026-10-18T09:45:00Z"));
        when(attempts.findWithMilestoneById(a.getId())).thenReturn(Optional.of(a));

        assertThatThrownBy(() -> service.recordOutcome(a.getId(), new OutcomeInput(PresentationAttempt.Outcome.FAILED, " ", null, null)))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_FAILED));
    }

    @Test
    void FR_MIL_10_passed_milestone_moves_to_awaiting_verification() {
        StudentMilestone first = Fixtures.milestone(student, 1, MilestoneStatus.SCHEDULED);
        PresentationAttempt a = Fixtures.withId(new PresentationAttempt());
        a.setMilestone(first);
        a.setScheduledStart(Instant.parse("2026-10-18T09:00:00Z"));
        a.setScheduledEnd(Instant.parse("2026-10-18T09:45:00Z"));
        when(attempts.findWithMilestoneById(a.getId())).thenReturn(Optional.of(a));

        service.recordOutcome(a.getId(), new OutcomeInput(PresentationAttempt.Outcome.PASSED, null, null, null));

        assertThat(first.getStatus()).isEqualTo(MilestoneStatus.AWAITING_VERIFICATION);
    }

    @Test
    void FR_MIL_09_reaching_the_maximum_failed_attempts_sets_the_student_at_risk() {
        StudentMilestone first = Fixtures.milestone(student, 1, MilestoneStatus.SCHEDULED);
        PresentationAttempt a = Fixtures.withId(new PresentationAttempt());
        a.setMilestone(first);
        a.setScheduledStart(Instant.parse("2026-10-18T09:00:00Z"));
        a.setScheduledEnd(Instant.parse("2026-10-18T09:45:00Z"));
        when(attempts.findWithMilestoneById(a.getId())).thenReturn(Optional.of(a));
        when(attempts.countFailed(first.getId())).thenReturn(3);

        service.recordOutcome(a.getId(), new OutcomeInput(PresentationAttempt.Outcome.FAILED, "Not ready", null, null));

        assertThat(first.getStatus()).isEqualTo(MilestoneStatus.AVAILABLE);
        assertThat(student.getStatus()).isEqualTo(StudentStatus.AT_RISK);
    }
}
