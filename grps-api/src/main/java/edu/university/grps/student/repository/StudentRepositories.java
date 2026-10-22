package edu.university.grps.student.repository;

import edu.university.grps.student.domain.Extension;
import edu.university.grps.student.domain.Student;
import edu.university.grps.student.domain.StudentStatus;
import edu.university.grps.student.domain.Supervision;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public final class StudentRepositories {

    private StudentRepositories() {}

    public interface StudentRepository extends JpaRepository<Student, UUID> {

        @EntityGraph(attributePaths = {"user", "programme", "cohort"})
        Optional<Student> findByUserId(UUID userId);

        @EntityGraph(attributePaths = {"user", "programme", "cohort"})
        Optional<Student> findWithDetailsById(UUID id);

        @EntityGraph(attributePaths = {"user", "programme", "cohort"})
        List<Student> findByStatusIn(Collection<StudentStatus> statuses);

        @EntityGraph(attributePaths = {"user", "programme", "cohort"})
        List<Student> findAllByIdIn(Collection<UUID> ids);

        @EntityGraph(attributePaths = {"user", "programme", "cohort"})
        @Query("""
                select s from Student s
                where (:programmeId is null or s.programme.id = :programmeId)
                  and (:cohortId is null or s.cohort.id = :cohortId)
                  and (:status is null or s.status = :status)
                  and (:q = '' or lower(s.user.firstName) like :q or lower(s.user.lastName) like :q
                       or lower(s.user.email) like :q or lower(s.user.staffOrStudentNo) like :q)
                """)
        Page<Student> search(@Param("q") String q, @Param("programmeId") UUID programmeId,
                             @Param("cohortId") UUID cohortId, @Param("status") StudentStatus status, Pageable pageable);

        @EntityGraph(attributePaths = {"user", "programme", "cohort"})
        @Query("""
                select s from Student s
                where s.status in :statuses
                  and (:programmeId is null or s.programme.id = :programmeId)
                  and (:cohortId is null or s.cohort.id = :cohortId)
                order by s.programmeEndOn asc
                """)
        List<Student> findByStatuses(@Param("statuses") Collection<StudentStatus> statuses,
                                     @Param("programmeId") UUID programmeId, @Param("cohortId") UUID cohortId);

        @EntityGraph(attributePaths = {"user", "programme", "cohort"})
        @Query("""
                select s from Student s
                where (:programmeId is null or s.programme.id = :programmeId)
                  and (:cohortId is null or s.cohort.id = :cohortId)
                """)
        List<Student> findForReport(@Param("programmeId") UUID programmeId, @Param("cohortId") UUID cohortId);
    }

    public interface SupervisionRepository extends JpaRepository<Supervision, UUID> {

        @EntityGraph(attributePaths = {"supervisor"})
        @Query("select s from Supervision s where s.student.id = :studentId and s.endsOn is null")
        List<Supervision> findOpenByStudent(@Param("studentId") UUID studentId);

        @EntityGraph(attributePaths = {"supervisor"})
        @Query("select s from Supervision s where s.student.id = :studentId order by s.startsOn desc, s.createdAt desc")
        List<Supervision> findHistory(@Param("studentId") UUID studentId);

        @EntityGraph(attributePaths = {"supervisor"})
        @Query("select s from Supervision s where s.student.id in :studentIds and s.endsOn is null")
        List<Supervision> findOpenByStudents(@Param("studentIds") Collection<UUID> studentIds);

        @Query("select s.student.id from Supervision s where s.supervisor.id = :userId and s.endsOn is null")
        List<UUID> findOpenStudentIds(@Param("userId") UUID userId);

        @Query("""
                select count(s) > 0 from Supervision s
                where s.supervisor.id = :userId and s.student.id = :studentId and s.endsOn is null
                """)
        boolean isOpenSupervisor(@Param("userId") UUID userId, @Param("studentId") UUID studentId);

        @EntityGraph(attributePaths = {"supervisor", "student"})
        @Query("select s from Supervision s where s.endsOn is null")
        List<Supervision> findAllOpen();
    }

    public interface ExtensionRepository extends JpaRepository<Extension, UUID> {

        List<Extension> findByStudentIdOrderByApprovedOnDesc(UUID studentId);

        @Query("select coalesce(sum(e.months), 0) from Extension e where e.student.id = :studentId")
        int totalMonths(@Param("studentId") UUID studentId);
    }
}
