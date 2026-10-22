package edu.university.grps.thesis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import edu.university.grps.audit.AuditService;
import edu.university.grps.common.ApiException;
import edu.university.grps.common.ErrorCode;
import edu.university.grps.milestone.domain.MilestoneStatus;
import edu.university.grps.milestone.repository.MilestoneRepositories.StudentMilestoneRepository;
import edu.university.grps.notification.service.NotificationService;
import edu.university.grps.publication.service.PublicationService;
import edu.university.grps.settings.service.SettingsService;
import edu.university.grps.storage.FileStore;
import edu.university.grps.storage.SignedLinks;
import edu.university.grps.storage.UploadChecks;
import edu.university.grps.student.domain.Student;
import edu.university.grps.student.domain.StudentStatus;
import edu.university.grps.student.service.StudentAccess;
import edu.university.grps.support.Fixtures;
import edu.university.grps.thesis.domain.ThesisSubmission;
import edu.university.grps.thesis.repository.ThesisRepositories.FormatCheckResultRepository;
import edu.university.grps.thesis.repository.ThesisRepositories.ThesisSubmissionRepository;
import edu.university.grps.user.service.UserDirectory;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class ThesisServiceTest {

    @Mock ThesisSubmissionRepository theses;
    @Mock FormatCheckResultRepository results;
    @Mock StudentMilestoneRepository milestones;
    @Mock PublicationService publications;
    @Mock StudentAccess access;
    @Mock UploadChecks uploads;
    @Mock FileStore store;
    @Mock SignedLinks links;
    @Mock SettingsService settings;
    @Mock NotificationService notifications;
    @Mock UserDirectory directory;
    @Mock AuditService audit;
    @Mock ApplicationEventPublisher events;

    private ThesisService service;
    private Student student;

    @BeforeEach
    void setUp() {
        service = new ThesisService(theses, results, milestones, publications, access, uploads, store, links, settings,
                notifications, directory, audit, events, Clock.fixed(Instant.parse("2026-10-19T08:00:00Z"), ZoneOffset.UTC));
        student = Fixtures.student(StudentStatus.ACTIVE);
        lenient().when(settings.decimal(SettingsService.SIMILARITY_LIMIT)).thenReturn(BigDecimal.valueOf(20));
        lenient().when(settings.decimal(SettingsService.AI_LIMIT)).thenReturn(BigDecimal.valueOf(20));
    }

    private ThesisSubmission screening() {
        ThesisSubmission t = Fixtures.withId(new ThesisSubmission());
        t.setStudent(student);
        t.setVersionNo(1);
        t.setStatus(ThesisSubmission.Status.SCREENING);
        return t;
    }

    @Test
    void BR_11_similarity_above_20_percent_is_rejected() {
        ThesisSubmission t = screening();
        service.applyScreening(t, new BigDecimal("23"), new BigDecimal("5"), null);
        assertThat(t.getStatus()).isEqualTo(ThesisSubmission.Status.SCREENING_REJECTED);
        assertThat(t.getLimitAppliedPct()).isEqualByComparingTo("20");
    }

    @Test
    void BR_11_ai_score_above_20_percent_is_rejected() {
        ThesisSubmission t = screening();
        service.applyScreening(t, new BigDecimal("4"), new BigDecimal("20.1"), null);
        assertThat(t.getStatus()).isEqualTo(ThesisSubmission.Status.SCREENING_REJECTED);
    }

    @Test
    void BR_11_scores_at_the_limit_pass_and_the_thesis_is_accepted_for_examination() {
        ThesisSubmission t = screening();
        when(milestones.findByStudentIdOrderBySequenceNo(student.getId())).thenReturn(List.of(
                Fixtures.milestone(student, 1, MilestoneStatus.APPROVED), Fixtures.milestone(student, 5, MilestoneStatus.APPROVED)));

        service.applyScreening(t, new BigDecimal("20"), new BigDecimal("14"), null);

        assertThat(t.getStatus()).isEqualTo(ThesisSubmission.Status.ACCEPTED_FOR_EXAMINATION);
        assertThat(student.getStatus()).isEqualTo(StudentStatus.COMPLETED);
    }

    @Test
    void BR_09_thesis_submission_requires_the_final_pre_defense_to_be_approved() {
        when(access.currentStudent()).thenReturn(student);
        when(milestones.findByStudentIdOrderBySequenceNo(student.getId())).thenReturn(List.of(
                Fixtures.milestone(student, 4, MilestoneStatus.APPROVED), Fixtures.milestone(student, 5, MilestoneStatus.AVAILABLE)));
        var file = new MockMultipartFile("file", "t.docx", null, new byte[] {1});

        assertThatThrownBy(() -> service.submit(file, "Title", "Abstract", "k"))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.THESIS_LOCKED));
    }
}
