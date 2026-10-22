package edu.university.grps.programme.service;

import edu.university.grps.audit.AuditService;
import edu.university.grps.common.ApiException;
import edu.university.grps.common.ErrorCode;
import edu.university.grps.common.Times;
import edu.university.grps.programme.api.ProgrammeDtos.CohortDto;
import edu.university.grps.programme.domain.AcademicSemester;
import edu.university.grps.programme.domain.Cohort;
import edu.university.grps.programme.domain.MilestoneTemplate;
import edu.university.grps.programme.domain.Programme;
import edu.university.grps.programme.domain.Venue;
import edu.university.grps.programme.repository.ProgrammeRepositories.AcademicSemesterRepository;
import edu.university.grps.programme.repository.ProgrammeRepositories.CohortRepository;
import edu.university.grps.programme.repository.ProgrammeRepositories.MilestoneTemplateRepository;
import edu.university.grps.programme.repository.ProgrammeRepositories.ProgrammeRepository;
import edu.university.grps.programme.repository.ProgrammeRepositories.VenueRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ProgrammeService {

    private final ProgrammeRepository programmes;
    private final MilestoneTemplateRepository templates;
    private final AcademicSemesterRepository semesters;
    private final CohortRepository cohorts;
    private final VenueRepository venues;
    private final AuditService audit;
    private final Clock clock;

    public ProgrammeService(ProgrammeRepository programmes, MilestoneTemplateRepository templates,
                            AcademicSemesterRepository semesters, CohortRepository cohorts, VenueRepository venues,
                            AuditService audit, Clock clock) {
        this.programmes = programmes;
        this.templates = templates;
        this.semesters = semesters;
        this.cohorts = cohorts;
        this.venues = venues;
        this.audit = audit;
        this.clock = clock;
    }

    // Programmes ---------------------------------------------------------------

    public record ProgrammeInput(String code, String name, Programme.Level level, int durationMonths, int semesters,
                                 int requiredMilestones, int checkpointMonths, int checkpointMilestones,
                                 boolean evidenceRequired, int firstMilestoneSemester, int requiredPublications,
                                 Long version) {}

    @Transactional(readOnly = true)
    public List<Programme> listProgrammes() {
        return programmes.findAll(Sort.by("code"));
    }

    @Transactional(readOnly = true)
    public Programme programme(UUID id) {
        return programmes.findById(id).orElseThrow(() -> ApiException.notFound("Programme"));
    }

    public Programme createProgramme(ProgrammeInput in) {
        Programme p = new Programme();
        apply(p, in);
        p = programmes.save(p);
        audit.record("PROGRAMME_CREATED", "Programme", p.getId(), describe(p));
        return p;
    }

    /** FR-PRG-01: editing a programme never touches milestones already generated for enrolled students. */
    public Programme updateProgramme(UUID id, ProgrammeInput in) {
        Programme p = programme(id);
        checkVersion(p.getVersion(), in.version());
        Map<String, Object> before = describe(p);
        apply(p, in);
        audit.record("PROGRAMME_UPDATED", "Programme", p.getId(), null, before, describe(p));
        return p;
    }

    private void apply(Programme p, ProgrammeInput in) {
        if (in.code() == null || in.code().isBlank() || in.name() == null || in.name().isBlank() || in.level() == null) {
            throw ApiException.validation("Code, name and level are required.");
        }
        if (in.durationMonths() < 1 || in.semesters() < 1 || in.requiredMilestones() < 1) {
            throw ApiException.validation("Duration, semesters and milestone count must be positive.");
        }
        if (in.checkpointMonths() > in.durationMonths() || in.checkpointMilestones() > in.requiredMilestones()) {
            throw ApiException.validation("The checkpoint must fall within the programme and need no more milestones than required.");
        }
        p.setCode(in.code().trim().toUpperCase());
        p.setName(in.name().trim());
        p.setLevel(in.level());
        p.setDurationMonths(in.durationMonths());
        p.setSemesters(in.semesters());
        p.setRequiredMilestones(in.requiredMilestones());
        p.setCheckpointMonths(in.checkpointMonths());
        p.setCheckpointMilestones(in.checkpointMilestones());
        p.setEvidenceRequired(in.evidenceRequired());
        p.setFirstMilestoneSemester(Math.max(1, in.firstMilestoneSemester()));
        p.setRequiredPublications(Math.max(0, in.requiredPublications()));
    }

    private static Map<String, Object> describe(Programme p) {
        return Map.of("code", p.getCode(), "name", p.getName(), "level", p.getLevel().name(),
                "durationMonths", p.getDurationMonths(), "requiredMilestones", p.getRequiredMilestones(),
                "checkpointMonths", p.getCheckpointMonths(), "checkpointMilestones", p.getCheckpointMilestones(),
                "evidenceRequired", p.isEvidenceRequired());
    }

    // Milestone templates (FR-PRG-02) -------------------------------------------

    public record TemplateInput(int sequenceNo, String title, String description, boolean finalPreDefense,
                                boolean evidenceRequired) {}

    @Transactional(readOnly = true)
    public List<MilestoneTemplate> templates(UUID programmeId) {
        return templates.findByProgrammeIdOrderBySequenceNo(programmeId);
    }

    /**
     * Replaces the template set. Sequence numbers must run 1..n with n equal to the programme's milestone count,
     * and exactly one template, the last, is the final pre-defense.
     */
    public List<MilestoneTemplate> replaceTemplates(UUID programmeId, List<TemplateInput> inputs) {
        Programme programme = programme(programmeId);
        List<TemplateInput> sorted = inputs.stream().sorted((a, b) -> Integer.compare(a.sequenceNo(), b.sequenceNo())).toList();
        if (sorted.size() != programme.getRequiredMilestones()) {
            throw ApiException.validation("This programme needs exactly " + programme.getRequiredMilestones() + " templates.");
        }
        for (int i = 0; i < sorted.size(); i++) {
            if (sorted.get(i).sequenceNo() != i + 1) throw ApiException.validation("Sequence numbers must run from 1 to " + sorted.size() + ".");
            if (sorted.get(i).title() == null || sorted.get(i).title().isBlank()) throw ApiException.validation("Every template needs a title.");
        }
        long finals = sorted.stream().filter(TemplateInput::finalPreDefense).count();
        if (finals != 1 || !sorted.get(sorted.size() - 1).finalPreDefense()) {
            throw ApiException.validation("Exactly one template, the last one, must be the final pre-defense.");
        }
        List<MilestoneTemplate> existing = templates.findByProgrammeIdOrderBySequenceNo(programmeId);
        // Clear the final flag first so the partial unique index never sees two finals mid-update.
        existing.forEach(t -> t.setFinalPreDefense(false));
        templates.flush();
        for (TemplateInput in : sorted) {
            MilestoneTemplate t = existing.stream().filter(e -> e.getSequenceNo() == in.sequenceNo()).findFirst()
                    .orElseGet(() -> {
                        MilestoneTemplate created = new MilestoneTemplate();
                        created.setProgramme(programme);
                        created.setSequenceNo(in.sequenceNo());
                        return created;
                    });
            t.setTitle(in.title().trim());
            t.setDescription(in.description());
            t.setFinalPreDefense(in.finalPreDefense());
            t.setEvidenceRequired(in.evidenceRequired());
            templates.save(t);
        }
        // Templates beyond the new count stay in place when students already reference them; they are just unused.
        audit.record("TEMPLATES_REPLACED", "Programme", programmeId, Map.of("count", sorted.size()));
        return templates.findByProgrammeIdOrderBySequenceNo(programmeId).stream()
                .filter(t -> t.getSequenceNo() <= sorted.size()).toList();
    }

    // Semesters (FR-PRG-03) -----------------------------------------------------

    public record SemesterInput(String academicYear, int number, LocalDate startsOn, LocalDate endsOn) {}

    @Transactional(readOnly = true)
    public List<AcademicSemester> listSemesters() {
        return semesters.findAllByOrderByStartsOnDesc();
    }

    @Transactional(readOnly = true)
    public Optional<AcademicSemester> currentSemester() {
        return semesters.findContaining(Times.today(clock));
    }

    public AcademicSemester createSemester(SemesterInput in) {
        return saveSemester(new AcademicSemester(), in);
    }

    public AcademicSemester updateSemester(UUID id, SemesterInput in) {
        return saveSemester(semesters.findById(id).orElseThrow(() -> ApiException.notFound("Semester")), in);
    }

    private AcademicSemester saveSemester(AcademicSemester s, SemesterInput in) {
        if (in.academicYear() == null || in.academicYear().isBlank() || in.startsOn() == null || in.endsOn() == null) {
            throw ApiException.validation("Academic year, start and end dates are required.");
        }
        if (in.number() < 1 || in.number() > 2) throw ApiException.validation("Semester number must be 1 or 2.");
        if (!in.endsOn().isAfter(in.startsOn())) throw ApiException.validation("A semester must end after it starts.");
        if (semesters.overlaps(in.startsOn(), in.endsOn(), s.getId())) {
            throw new ApiException(ErrorCode.INVALID_STATE, "Semesters cannot overlap.");
        }
        s.setAcademicYear(in.academicYear().trim());
        s.setNumber(in.number());
        s.setStartsOn(in.startsOn());
        s.setEndsOn(in.endsOn());
        s = semesters.save(s);
        audit.record("SEMESTER_SAVED", "AcademicSemester", s.getId(),
                Map.of("label", s.label(), "startsOn", s.getStartsOn(), "endsOn", s.getEndsOn()));
        return s;
    }

    // Cohorts (FR-PRG-04) -------------------------------------------------------

    public record CohortInput(UUID programmeId, String name, UUID intakeSemesterId) {}

    @Transactional(readOnly = true)
    public List<CohortDto> listCohorts(UUID programmeId) {
        List<Cohort> found = programmeId != null ? cohorts.findByProgrammeIdOrderByNameDesc(programmeId)
                : cohorts.findAll(Sort.by(Sort.Direction.DESC, "name"));
        return found.stream().map(CohortDto::of).toList();
    }

    public CohortDto createCohort(CohortInput in) {
        if (in.name() == null || in.name().isBlank()) throw ApiException.validation("Cohort name is required.");
        Cohort c = new Cohort();
        c.setProgramme(programme(in.programmeId()));
        c.setName(in.name().trim());
        if (in.intakeSemesterId() != null) {
            c.setIntakeSemester(semesters.findById(in.intakeSemesterId()).orElseThrow(() -> ApiException.notFound("Semester")));
        }
        c = cohorts.save(c);
        audit.record("COHORT_CREATED", "Cohort", c.getId(), Map.of("name", c.getName()));
        return CohortDto.of(c);
    }

    @Transactional(readOnly = true)
    public Cohort cohort(UUID id) {
        return cohorts.findById(id).orElseThrow(() -> ApiException.notFound("Cohort"));
    }

    // Venues (FR-SET-03) --------------------------------------------------------

    public record VenueInput(String name, Integer capacity, Boolean active) {}

    @Transactional(readOnly = true)
    public List<Venue> listVenues() {
        return venues.findAllByOrderByName();
    }

    public Venue createVenue(VenueInput in) {
        return saveVenue(new Venue(), in);
    }

    public Venue updateVenue(UUID id, VenueInput in) {
        return saveVenue(venues.findById(id).orElseThrow(() -> ApiException.notFound("Venue")), in);
    }

    private Venue saveVenue(Venue v, VenueInput in) {
        if (in.name() == null || in.name().isBlank()) throw ApiException.validation("Venue name is required.");
        v.setName(in.name().trim());
        v.setCapacity(in.capacity());
        if (in.active() != null) v.setActive(in.active());
        v = venues.save(v);
        audit.record("VENUE_SAVED", "Venue", v.getId(), Map.of("name", v.getName(), "active", v.isActive()));
        return v;
    }

    static void checkVersion(Long current, Long sent) {
        if (sent != null && !Objects.equals(current, sent)) {
            throw new ApiException(ErrorCode.CONCURRENT_UPDATE, "Someone else changed this record. Reload it and try again.");
        }
    }
}
