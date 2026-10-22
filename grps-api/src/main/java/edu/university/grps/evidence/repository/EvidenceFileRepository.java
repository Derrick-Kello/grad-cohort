package edu.university.grps.evidence.repository;

import edu.university.grps.evidence.domain.EvidenceFile;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EvidenceFileRepository extends JpaRepository<EvidenceFile, UUID> {

    @Query("select e from EvidenceFile e where e.milestone.id = :milestoneId order by e.createdAt desc")
    List<EvidenceFile> findByMilestone(@Param("milestoneId") UUID milestoneId);

    @Query("""
            select count(e) from EvidenceFile e
            where e.milestone.id = :milestoneId and e.supersededBy is null
              and e.scanStatus <> edu.university.grps.evidence.domain.EvidenceFile.ScanStatus.INFECTED
            """)
    long countCurrent(@Param("milestoneId") UUID milestoneId);

    @Query("""
            select count(e) from EvidenceFile e
            where e.milestone.id = :milestoneId and e.supersededBy is null
              and e.scanStatus = edu.university.grps.evidence.domain.EvidenceFile.ScanStatus.CLEAN
            """)
    long countCurrentClean(@Param("milestoneId") UUID milestoneId);

    @Query("""
            select e.milestone.id, count(e) from EvidenceFile e
            where e.milestone.id in :ids and e.supersededBy is null
              and e.scanStatus <> edu.university.grps.evidence.domain.EvidenceFile.ScanStatus.INFECTED
            group by e.milestone.id
            """)
    List<Object[]> countCurrentByMilestones(@Param("ids") Collection<UUID> ids);

    @EntityGraph(attributePaths = {"milestone", "milestone.student", "milestone.student.user"})
    Optional<EvidenceFile> findWithMilestoneById(UUID id);

    List<EvidenceFile> findByScanStatus(EvidenceFile.ScanStatus status);
}
