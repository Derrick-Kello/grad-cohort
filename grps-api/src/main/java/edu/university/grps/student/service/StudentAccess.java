package edu.university.grps.student.service;

import edu.university.grps.common.ApiException;
import edu.university.grps.common.ErrorCode;
import edu.university.grps.security.CurrentUser;
import edu.university.grps.student.domain.Student;
import edu.university.grps.student.domain.Supervision;
import edu.university.grps.student.repository.StudentRepositories.StudentRepository;
import edu.university.grps.student.repository.StudentRepositories.SupervisionRepository;
import edu.university.grps.user.domain.RoleCode;
import edu.university.grps.user.service.UserDirectory;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ownership checks (SEC-04, SEC-05). A record the caller may not see is reported as 404, never 403,
 * so IDs do not reveal what exists.
 */
@Service
@Transactional(readOnly = true)
public class StudentAccess {

    private final StudentRepository students;
    private final SupervisionRepository supervisions;
    private final UserDirectory directory;

    public StudentAccess(StudentRepository students, SupervisionRepository supervisions, UserDirectory directory) {
        this.students = students;
        this.supervisions = supervisions;
        this.directory = directory;
    }

    /** The student record of the signed-in user. */
    public Student currentStudent() {
        return students.findByUserId(CurrentUser.id())
                .orElseThrow(() -> ApiException.notFound("Student record"));
    }

    public Optional<Student> currentStudentIfAny() {
        return CurrentUser.optionalId().flatMap(students::findByUserId);
    }

    public Student load(UUID studentId) {
        return students.findWithDetailsById(studentId).orElseThrow(() -> ApiException.notFound("Student"));
    }

    /** Admins and secretaries see every student; students see themselves; supervisors see supervisees. */
    public Student requireViewable(UUID studentId) {
        Student student = load(studentId);
        if (!canView(student)) throw ApiException.notFound("Student");
        return student;
    }

    public boolean canView(Student student) {
        if (CurrentUser.isAdmin() || CurrentUser.hasRole(RoleCode.SECRETARY)) return true;
        UUID me = CurrentUser.id();
        if (student.getUser().getId().equals(me)) return true;
        return CurrentUser.hasRole(RoleCode.SUPERVISOR) && supervisions.isOpenSupervisor(me, student.getId());
    }

    public boolean isOwnStudent(Student student) {
        return student.getUser().getId().equals(CurrentUser.optionalId().orElse(null));
    }

    public boolean isSupervisorOf(UUID studentId) {
        return CurrentUser.hasRole(RoleCode.SUPERVISOR)
                && supervisions.isOpenSupervisor(CurrentUser.id(), studentId);
    }

    /** BR-20: closed students cannot be scheduled or submit work. */
    public void requireWritable(Student student) {
        if (student.isClosed()) {
            throw new ApiException(ErrorCode.INVALID_STATE,
                    "This student's record is " + student.getStatus().name().toLowerCase().replace('_', ' ')
                            + "; no further work can be scheduled or submitted.");
        }
    }

    public List<UUID> supervisorIds(UUID studentId) {
        return supervisions.findOpenByStudent(studentId).stream().map(s -> s.getSupervisor().getId()).toList();
    }

    public Optional<Supervision> primarySupervision(UUID studentId) {
        return supervisions.findOpenByStudent(studentId).stream()
                .filter(s -> s.getType() == Supervision.Type.PRIMARY).findFirst();
    }

    /** Student plus primary and co-supervisors (FR-USR-05: co-supervisors get the same notifications). */
    public List<UUID> studentAndSupervisors(Student student) {
        List<UUID> ids = new ArrayList<>();
        ids.add(student.getUser().getId());
        ids.addAll(supervisorIds(student.getId()));
        return ids;
    }

    public List<UUID> admins() {
        return directory.activeIdsWithRole(RoleCode.ADMIN);
    }

    public List<UUID> secretaries() {
        return directory.activeIdsWithRole(RoleCode.SECRETARY);
    }

    public List<UUID> librarians() {
        return directory.activeIdsWithRole(RoleCode.LIBRARIAN);
    }

    public List<UUID> supervisedStudentIds(UUID supervisorUserId) {
        return supervisions.findOpenStudentIds(supervisorUserId);
    }
}
