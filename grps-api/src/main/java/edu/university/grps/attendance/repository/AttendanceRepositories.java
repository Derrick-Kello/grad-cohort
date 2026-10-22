package edu.university.grps.attendance.repository;

import edu.university.grps.attendance.domain.AttendanceRecord;
import edu.university.grps.attendance.domain.ClassSession;
import edu.university.grps.attendance.domain.Course;
import edu.university.grps.attendance.domain.Eligibility;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public final class AttendanceRepositories {

    private AttendanceRepositories() {}

    public interface CourseRepository extends JpaRepository<Course, UUID> {

        @EntityGraph(attributePaths = {"semester", "lecturers"})
        @Query("select c from Course c where (:semesterId is null or c.semester.id = :semesterId) order by c.code")
        List<Course> search(@Param("semesterId") UUID semesterId);

        @EntityGraph(attributePaths = {"semester", "lecturers"})
        @Query("select distinct c from Course c join c.lecturers l where l.id = :userId order by c.code")
        List<Course> findForLecturer(@Param("userId") UUID userId);

        @EntityGraph(attributePaths = {"semester"})
        @Query("select c from Course c join c.students s where s.id = :studentId order by c.code")
        List<Course> findForStudent(@Param("studentId") UUID studentId);

        @EntityGraph(attributePaths = {"semester", "lecturers"})
        Optional<Course> findWithLecturersById(UUID id);

        Optional<Course> findByCodeIgnoreCaseAndSemesterId(String code, UUID semesterId);

        @Query("select count(c) > 0 from Course c join c.lecturers l where c.id = :courseId and l.id = :userId")
        boolean isLecturer(@Param("courseId") UUID courseId, @Param("userId") UUID userId);
    }

    public interface ClassSessionRepository extends JpaRepository<ClassSession, UUID> {

        List<ClassSession> findByCourseIdOrderByHeldOnDescStartsAtDesc(UUID courseId);

        @EntityGraph(attributePaths = {"course"})
        Optional<ClassSession> findWithCourseById(UUID id);

        @Query("select s from ClassSession s where s.course.id = :courseId and s.attendanceTakenAt is not null and s.heldOn <= :asOf")
        List<ClassSession> findHeld(@Param("courseId") UUID courseId, @Param("asOf") java.time.LocalDate asOf);
    }

    public interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, UUID> {

        @Query("select r from AttendanceRecord r where r.session.id = :sessionId")
        List<AttendanceRecord> findBySession(@Param("sessionId") UUID sessionId);

        @Query("select r from AttendanceRecord r where r.session.id in :sessionIds")
        List<AttendanceRecord> findBySessions(@Param("sessionIds") Collection<UUID> sessionIds);
    }

    public interface EligibilityRepository extends JpaRepository<Eligibility, UUID> {

        Optional<Eligibility> findByCourseIdAndStudentId(UUID courseId, UUID studentId);

        @EntityGraph(attributePaths = {"student", "student.user", "student.programme", "course"})
        @Query("select e from Eligibility e where e.course.id = :courseId")
        List<Eligibility> findByCourse(@Param("courseId") UUID courseId);

        @EntityGraph(attributePaths = {"course", "course.semester"})
        @Query("select e from Eligibility e where e.student.id = :studentId")
        List<Eligibility> findByStudent(@Param("studentId") UUID studentId);

        @EntityGraph(attributePaths = {"student", "course"})
        Optional<Eligibility> findWithDetailsById(UUID id);
    }
}
