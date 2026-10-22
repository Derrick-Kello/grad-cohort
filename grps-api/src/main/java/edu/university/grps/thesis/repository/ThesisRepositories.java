package edu.university.grps.thesis.repository;

import edu.university.grps.thesis.domain.FormatCheckResult;
import edu.university.grps.thesis.domain.FormatRule;
import edu.university.grps.thesis.domain.ThesisSubmission;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public final class ThesisRepositories {

    private ThesisRepositories() {}

    public interface ThesisSubmissionRepository extends JpaRepository<ThesisSubmission, UUID> {

        List<ThesisSubmission> findByStudentIdOrderByVersionNoDesc(UUID studentId);

        Optional<ThesisSubmission> findFirstByStudentIdOrderByVersionNoDesc(UUID studentId);

        @EntityGraph(attributePaths = {"student", "student.user", "student.programme"})
        Optional<ThesisSubmission> findWithStudentById(UUID id);

        @EntityGraph(attributePaths = {"student", "student.user", "student.programme"})
        List<ThesisSubmission> findByStatusIn(Collection<ThesisSubmission.Status> statuses);

        @EntityGraph(attributePaths = {"student", "student.user", "student.programme"})
        @Query("select t from ThesisSubmission t order by t.createdAt desc")
        List<ThesisSubmission> findAllWithStudents();

        @Query("select coalesce(max(t.versionNo), 0) from ThesisSubmission t where t.student.id = :studentId")
        int maxVersion(@Param("studentId") UUID studentId);
    }

    public interface FormatCheckResultRepository extends JpaRepository<FormatCheckResult, UUID> {

        @Query("select r from FormatCheckResult r where r.thesis.id = :thesisId order by r.ruleCode")
        List<FormatCheckResult> findByThesis(@Param("thesisId") UUID thesisId);

        @Modifying
        @Query("delete from FormatCheckResult r where r.thesis.id = :thesisId")
        void deleteByThesis(@Param("thesisId") UUID thesisId);
    }

    public interface FormatRuleRepository extends JpaRepository<FormatRule, UUID> {

        @Query("select r from FormatRule r where r.programme.id = :programmeId order by r.ruleCode")
        List<FormatRule> findByProgramme(@Param("programmeId") UUID programmeId);
    }
}
