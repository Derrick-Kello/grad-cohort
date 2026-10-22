package edu.university.grps.publication.repository;

import edu.university.grps.publication.domain.Publication;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PublicationRepository extends JpaRepository<Publication, UUID> {

    List<Publication> findByStudentIdOrderByUpdatedAtDesc(UUID studentId);

    @EntityGraph(attributePaths = {"student", "student.user", "student.programme"})
    Optional<Publication> findWithStudentById(UUID id);

    @EntityGraph(attributePaths = {"student", "student.user", "student.programme"})
    List<Publication> findByReviewStatusInOrderBySubmittedAtAsc(Collection<Publication.ReviewStatus> statuses);

    @EntityGraph(attributePaths = {"student", "student.user", "student.programme"})
    @Query("select p from Publication p where p.reviewStatus = edu.university.grps.publication.domain.Publication.ReviewStatus.APPROVED order by p.reviewedAt desc")
    List<Publication> findApproved();

    @EntityGraph(attributePaths = {"student", "student.user"})
    @Query("select p from Publication p where lower(p.doi) = lower(:doi) and p.id <> :excludeId and p.reviewStatus <> edu.university.grps.publication.domain.Publication.ReviewStatus.DRAFT")
    List<Publication> findByDoi(@Param("doi") String doi, @Param("excludeId") UUID excludeId);

    @Query("select p from Publication p where p.id <> :excludeId and p.reviewStatus <> edu.university.grps.publication.domain.Publication.ReviewStatus.DRAFT and p.reviewStatus <> edu.university.grps.publication.domain.Publication.ReviewStatus.REJECTED")
    List<Publication> findComparable(@Param("excludeId") UUID excludeId);

    @Query("select p from Publication p where p.student.id in :studentIds and p.reviewStatus <> edu.university.grps.publication.domain.Publication.ReviewStatus.DRAFT order by p.updatedAt desc")
    List<Publication> findVisibleForStudents(@Param("studentIds") Collection<UUID> studentIds);

    long countByStudentIdAndReviewStatus(UUID studentId, Publication.ReviewStatus status);
}
