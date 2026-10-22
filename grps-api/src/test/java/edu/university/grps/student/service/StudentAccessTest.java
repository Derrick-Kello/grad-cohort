package edu.university.grps.student.service;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import edu.university.grps.common.ApiException;
import edu.university.grps.student.domain.StudentStatus;
import edu.university.grps.student.repository.StudentRepositories.StudentRepository;
import edu.university.grps.student.repository.StudentRepositories.SupervisionRepository;
import edu.university.grps.support.Fixtures;
import edu.university.grps.user.service.UserDirectory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StudentAccessTest {

    @Mock StudentRepository students;
    @Mock SupervisionRepository supervisions;
    @Mock UserDirectory directory;

    @Test
    void BR_20_expelled_and_completed_students_cannot_submit_work() {
        StudentAccess access = new StudentAccess(students, supervisions, directory);
        assertThatThrownBy(() -> access.requireWritable(Fixtures.student(StudentStatus.EXPELLED))).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> access.requireWritable(Fixtures.student(StudentStatus.COMPLETED))).isInstanceOf(ApiException.class);
        assertThatNoException().isThrownBy(() -> access.requireWritable(Fixtures.student(StudentStatus.AT_RISK)));
    }
}
