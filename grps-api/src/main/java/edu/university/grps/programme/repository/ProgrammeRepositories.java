package edu.university.grps.programme.repository;

import edu.university.grps.programme.domain.AcademicSemester;
import edu.university.grps.programme.domain.Cohort;
import edu.university.grps.programme.domain.MilestoneTemplate;
import edu.university.grps.programme.domain.Programme;
import edu.university.grps.programme.domain.Venue;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Repositories for the programme module. */
public final class ProgrammeRepositories {

    private ProgrammeRepositories() {}

    public interface ProgrammeRepository extends JpaRepository<Programme, UUID> {
        Optional<Programme> findByCodeIgnoreCase(String code);
    }

    public interface MilestoneTemplateRepository extends JpaRepository<MilestoneTemplate, UUID> {
        List<MilestoneTemplate> findByProgrammeIdOrderBySequenceNo(UUID programmeId);
    }

    public interface AcademicSemesterRepository extends JpaRepository<AcademicSemester, UUID> {
        List<AcademicSemester> findAllByOrderByStartsOnDesc();

        @Query("select s from AcademicSemester s where s.startsOn <= :date and s.endsOn >= :date")
        Optional<AcademicSemester> findContaining(@Param("date") LocalDate date);

        @Query("""
                select count(s) > 0 from AcademicSemester s
                where s.startsOn <= :endsOn and s.endsOn >= :startsOn and (:excludeId is null or s.id <> :excludeId)
                """)
        boolean overlaps(@Param("startsOn") LocalDate startsOn, @Param("endsOn") LocalDate endsOn,
                         @Param("excludeId") UUID excludeId);
    }

    public interface CohortRepository extends JpaRepository<Cohort, UUID> {
        List<Cohort> findByProgrammeIdOrderByNameDesc(UUID programmeId);

        Optional<Cohort> findByProgrammeIdAndNameIgnoreCase(UUID programmeId, String name);
    }

    public interface VenueRepository extends JpaRepository<Venue, UUID> {
        List<Venue> findAllByOrderByName();
    }
}
