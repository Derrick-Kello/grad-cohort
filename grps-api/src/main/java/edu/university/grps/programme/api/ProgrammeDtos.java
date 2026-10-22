package edu.university.grps.programme.api;

import edu.university.grps.programme.domain.AcademicSemester;
import edu.university.grps.programme.domain.Cohort;
import edu.university.grps.programme.domain.MilestoneTemplate;
import edu.university.grps.programme.domain.Programme;
import edu.university.grps.programme.domain.Venue;
import java.time.LocalDate;
import java.util.UUID;

public final class ProgrammeDtos {

    private ProgrammeDtos() {}

    public record ProgrammeDto(UUID id, String code, String name, String level, int durationMonths, int semesters,
                               int requiredMilestones, int checkpointMonths, int checkpointMilestones,
                               boolean evidenceRequired, int firstMilestoneSemester, int requiredPublications,
                               Long version) {
        public static ProgrammeDto of(Programme p) {
            return new ProgrammeDto(p.getId(), p.getCode(), p.getName(), p.getLevel().name(), p.getDurationMonths(),
                    p.getSemesters(), p.getRequiredMilestones(), p.getCheckpointMonths(), p.getCheckpointMilestones(),
                    p.isEvidenceRequired(), p.getFirstMilestoneSemester(), p.getRequiredPublications(), p.getVersion());
        }
    }

    public record TemplateDto(UUID id, int sequenceNo, String title, String description, boolean finalPreDefense,
                              boolean evidenceRequired) {
        public static TemplateDto of(MilestoneTemplate t) {
            return new TemplateDto(t.getId(), t.getSequenceNo(), t.getTitle(), t.getDescription(), t.isFinalPreDefense(),
                    t.isEvidenceRequired());
        }
    }

    public record SemesterDto(UUID id, String academicYear, int number, String label, LocalDate startsOn,
                              LocalDate endsOn, boolean current) {
        public static SemesterDto of(AcademicSemester s, LocalDate today) {
            return new SemesterDto(s.getId(), s.getAcademicYear(), s.getNumber(), s.label(), s.getStartsOn(),
                    s.getEndsOn(), s.contains(today));
        }
    }

    public record CohortDto(UUID id, UUID programmeId, String programmeCode, String name, UUID intakeSemesterId,
                            String intakeSemesterLabel) {
        public static CohortDto of(Cohort c) {
            return new CohortDto(c.getId(), c.getProgramme().getId(), c.getProgramme().getCode(), c.getName(),
                    c.getIntakeSemester() == null ? null : c.getIntakeSemester().getId(),
                    c.getIntakeSemester() == null ? null : c.getIntakeSemester().label());
        }
    }

    public record VenueDto(UUID id, String name, Integer capacity, boolean active) {
        public static VenueDto of(Venue v) {
            return new VenueDto(v.getId(), v.getName(), v.getCapacity(), v.isActive());
        }
    }
}
