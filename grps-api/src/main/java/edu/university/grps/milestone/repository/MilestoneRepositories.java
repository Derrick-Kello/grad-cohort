package edu.university.grps.milestone.repository;

import edu.university.grps.milestone.domain.MilestoneStatus;
import edu.university.grps.milestone.domain.PresentationAttempt;
import edu.university.grps.milestone.domain.StudentMilestone;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public final class MilestoneRepositories {

    private MilestoneRepositories() {}

    public interface StudentMilestoneRepository extends JpaRepository<StudentMilestone, UUID> {

        List<StudentMilestone> findByStudentIdOrderBySequenceNo(UUID studentId);

        Optional<StudentMilestone> findByStudentIdAndSequenceNo(UUID studentId, int sequenceNo);

        @EntityGraph(attributePaths = {"student", "student.user", "student.programme"})
        Optional<StudentMilestone> findWithStudentById(UUID id);

        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("select m from StudentMilestone m where m.id = :id")
        Optional<StudentMilestone> lockById(@Param("id") UUID id);

        @EntityGraph(attributePaths = {"student", "student.user", "student.programme"})
        List<StudentMilestone> findByStatusOrderByUpdatedAtAsc(MilestoneStatus status);

        @Query("select m from StudentMilestone m where m.student.id in :studentIds order by m.student.id, m.sequenceNo")
        List<StudentMilestone> findByStudentIds(@Param("studentIds") Collection<UUID> studentIds);

        long countByStudentIdAndStatus(UUID studentId, MilestoneStatus status);

        @Query("""
                select m.student.id, count(m) from StudentMilestone m
                where m.student.id in :studentIds and m.status = edu.university.grps.milestone.domain.MilestoneStatus.APPROVED
                group by m.student.id
                """)
        List<Object[]> countApprovedByStudents(@Param("studentIds") Collection<UUID> studentIds);
    }

    public interface PresentationAttemptRepository extends JpaRepository<PresentationAttempt, UUID> {

        List<PresentationAttempt> findByMilestoneIdOrderByAttemptNo(UUID milestoneId);

        @Query("select a from PresentationAttempt a where a.milestone.id in :milestoneIds order by a.attemptNo")
        List<PresentationAttempt> findByMilestoneIds(@Param("milestoneIds") Collection<UUID> milestoneIds);

        @EntityGraph(attributePaths = {"milestone", "milestone.student", "milestone.student.user", "venue"})
        Optional<PresentationAttempt> findWithMilestoneById(UUID id);

        @Query("select coalesce(max(a.attemptNo), 0) from PresentationAttempt a where a.milestone.id = :milestoneId")
        int maxAttemptNo(@Param("milestoneId") UUID milestoneId);

        @Query("""
                select count(a) from PresentationAttempt a
                where a.milestone.id = :milestoneId and a.outcome = edu.university.grps.milestone.domain.PresentationAttempt.Outcome.FAILED
                """)
        int countFailed(@Param("milestoneId") UUID milestoneId);

        @Query("""
                select a from PresentationAttempt a
                where a.milestone.id = :milestoneId and a.cancelledAt is null and a.outcome is null
                """)
        Optional<PresentationAttempt> findOpen(@Param("milestoneId") UUID milestoneId);

        /** Open attempts for one student that overlap the window (FR-MIL-04). */
        @Query("""
                select a from PresentationAttempt a
                where a.milestone.student.id = :studentId and a.cancelledAt is null and a.outcome is null
                  and a.scheduledStart < :end and a.scheduledEnd > :start and a.id <> :excludeId
                """)
        List<PresentationAttempt> studentClashes(@Param("studentId") UUID studentId, @Param("start") Instant start,
                                                 @Param("end") Instant end, @Param("excludeId") UUID excludeId);

        @Query("""
                select a from PresentationAttempt a
                where a.venue.id = :venueId and a.cancelledAt is null and a.outcome is null
                  and a.scheduledStart < :end and a.scheduledEnd > :start and a.id <> :excludeId
                """)
        List<PresentationAttempt> venueClashes(@Param("venueId") UUID venueId, @Param("start") Instant start,
                                               @Param("end") Instant end, @Param("excludeId") UUID excludeId);

        @Query("""
                select distinct a from PresentationAttempt a join a.panel p
                where p.userId in :userIds and a.cancelledAt is null and a.outcome is null
                  and a.scheduledStart < :end and a.scheduledEnd > :start and a.id <> :excludeId
                """)
        List<PresentationAttempt> panelClashes(@Param("userIds") Collection<UUID> userIds, @Param("start") Instant start,
                                               @Param("end") Instant end, @Param("excludeId") UUID excludeId);

        @EntityGraph(attributePaths = {"milestone", "milestone.student", "milestone.student.user",
                "milestone.student.programme", "venue"})
        @Query("""
                select a from PresentationAttempt a
                where a.scheduledStart >= :from and a.scheduledStart < :to
                order by a.scheduledStart
                """)
        List<PresentationAttempt> findInRange(@Param("from") Instant from, @Param("to") Instant to);

        @EntityGraph(attributePaths = {"milestone", "milestone.student", "milestone.student.user", "venue"})
        @Query("""
                select a from PresentationAttempt a
                where a.cancelledAt is null and a.outcome is null and a.scheduledStart > :now and a.scheduledStart <= :until
                """)
        List<PresentationAttempt> upcomingOpen(@Param("now") Instant now, @Param("until") Instant until);

        @EntityGraph(attributePaths = {"milestone", "milestone.student", "milestone.student.user",
                "milestone.student.programme", "venue"})
        @Query("""
                select a from PresentationAttempt a
                where a.cancelledAt is null and a.outcome is null and a.scheduledStart <= :now
                order by a.scheduledStart
                """)
        List<PresentationAttempt> awaitingOutcome(@Param("now") Instant now);
    }
}
