package edu.university.grps.student.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import edu.university.grps.audit.AuditService;
import edu.university.grps.common.ApiException;
import edu.university.grps.common.ErrorCode;
import edu.university.grps.milestone.service.MilestoneService;
import edu.university.grps.notification.service.NotificationService;
import edu.university.grps.programme.repository.ProgrammeRepositories.CohortRepository;
import edu.university.grps.programme.repository.ProgrammeRepositories.ProgrammeRepository;
import edu.university.grps.student.domain.Student;
import edu.university.grps.student.domain.StudentStatus;
import edu.university.grps.student.repository.StudentRepositories.ExtensionRepository;
import edu.university.grps.student.repository.StudentRepositories.StudentRepository;
import edu.university.grps.student.repository.StudentRepositories.SupervisionRepository;
import edu.university.grps.support.Fixtures;
import edu.university.grps.user.repository.AppUserRepository;
import edu.university.grps.user.repository.RoleRepository;
import edu.university.grps.user.service.UserService;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StudentServiceTest {

    @Mock StudentRepository students;
    @Mock SupervisionRepository supervisions;
    @Mock ExtensionRepository extensions;
    @Mock ProgrammeRepository programmes;
    @Mock CohortRepository cohorts;
    @Mock AppUserRepository users;
    @Mock RoleRepository roles;
    @Mock UserService userService;
    @Mock MilestoneService milestones;
    @Mock StudentAccess access;
    @Mock NotificationService notifications;
    @Mock AuditService audit;

    private StudentService service;

    @BeforeEach
    void setUp() {
        service = new StudentService(students, supervisions, extensions, programmes, cohorts, users, roles, userService, milestones,
                access, notifications, audit, Clock.systemUTC());
    }

    @Test
    void BR_17_a_student_cannot_be_enrolled_without_a_supervisor() {
        var input = new StudentService.EnrolInput(null, "a@b.c", "N1", "A", "B", null, UUID.randomUUID(), UUID.randomUUID(),
                LocalDate.now(), null, null);
        assertThatThrownBy(() -> service.enrol(input))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.SUPERVISOR_REQUIRED));
    }

    @Test
    void BR_15_expulsion_needs_a_written_reason() {
        assertThatThrownBy(() -> service.confirmExpulsion(UUID.randomUUID(), " "))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_FAILED));
    }

    @Test
    void BR_15_only_flagged_students_can_be_expelled() {
        Student s = Fixtures.student(StudentStatus.AT_RISK);
        when(access.load(s.getId())).thenReturn(s);
        assertThatThrownBy(() -> service.confirmExpulsion(s.getId(), "No progress"))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.INVALID_STATE));
        assertThat(s.getStatus()).isEqualTo(StudentStatus.AT_RISK);
    }
}
