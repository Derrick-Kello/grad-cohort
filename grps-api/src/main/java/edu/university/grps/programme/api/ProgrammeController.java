package edu.university.grps.programme.api;

import edu.university.grps.common.Times;
import edu.university.grps.programme.api.ProgrammeDtos.CohortDto;
import edu.university.grps.programme.api.ProgrammeDtos.ProgrammeDto;
import edu.university.grps.programme.api.ProgrammeDtos.SemesterDto;
import edu.university.grps.programme.api.ProgrammeDtos.TemplateDto;
import edu.university.grps.programme.api.ProgrammeDtos.VenueDto;
import edu.university.grps.programme.service.ProgrammeService;
import edu.university.grps.programme.service.ProgrammeService.CohortInput;
import edu.university.grps.programme.service.ProgrammeService.ProgrammeInput;
import edu.university.grps.programme.service.ProgrammeService.SemesterInput;
import edu.university.grps.programme.service.ProgrammeService.TemplateInput;
import edu.university.grps.programme.service.ProgrammeService.VenueInput;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class ProgrammeController {

    private final ProgrammeService service;
    private final Clock clock;

    public ProgrammeController(ProgrammeService service, Clock clock) {
        this.service = service;
        this.clock = clock;
    }

    // Reference data any signed-in user can read.

    @GetMapping("/reference/programmes")
    public List<ProgrammeDto> programmes() {
        return service.listProgrammes().stream().map(ProgrammeDto::of).toList();
    }

    @GetMapping("/reference/cohorts")
    public List<CohortDto> cohorts(@RequestParam(required = false) UUID programmeId) {
        return service.listCohorts(programmeId);
    }

    @GetMapping("/reference/venues")
    public List<VenueDto> venues() {
        return service.listVenues().stream().map(VenueDto::of).toList();
    }

    @GetMapping("/reference/semesters")
    public List<SemesterDto> semesters() {
        LocalDate today = Times.today(clock);
        return service.listSemesters().stream().map(s -> SemesterDto.of(s, today)).toList();
    }

    // Admin management.

    @GetMapping("/admin/programmes")
    @PreAuthorize("hasRole('ADMIN')")
    public List<ProgrammeDto> adminProgrammes() {
        return programmes();
    }

    @PostMapping("/admin/programmes")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public ProgrammeDto createProgramme(@RequestBody ProgrammeInput input) {
        return ProgrammeDto.of(service.createProgramme(input));
    }

    @PutMapping("/admin/programmes/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ProgrammeDto updateProgramme(@PathVariable UUID id, @RequestBody ProgrammeInput input) {
        return ProgrammeDto.of(service.updateProgramme(id, input));
    }

    @GetMapping("/admin/programmes/{id}/milestone-templates")
    @PreAuthorize("hasAnyRole('ADMIN','SECRETARY')")
    public List<TemplateDto> templates(@PathVariable UUID id) {
        return service.templates(id).stream().map(TemplateDto::of).toList();
    }

    @PutMapping("/admin/programmes/{id}/milestone-templates")
    @PreAuthorize("hasRole('ADMIN')")
    public List<TemplateDto> replaceTemplates(@PathVariable UUID id, @RequestBody List<TemplateInput> input) {
        return service.replaceTemplates(id, input).stream().map(TemplateDto::of).toList();
    }

    @GetMapping("/admin/semesters")
    @PreAuthorize("hasRole('ADMIN')")
    public List<SemesterDto> adminSemesters() {
        return semesters();
    }

    @PostMapping("/admin/semesters")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public SemesterDto createSemester(@RequestBody SemesterInput input) {
        return SemesterDto.of(service.createSemester(input), Times.today(clock));
    }

    @PutMapping("/admin/semesters/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public SemesterDto updateSemester(@PathVariable UUID id, @RequestBody SemesterInput input) {
        return SemesterDto.of(service.updateSemester(id, input), Times.today(clock));
    }

    @GetMapping("/admin/cohorts")
    @PreAuthorize("hasRole('ADMIN')")
    public List<CohortDto> adminCohorts(@RequestParam(required = false) UUID programmeId) {
        return cohorts(programmeId);
    }

    @PostMapping("/admin/cohorts")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public CohortDto createCohort(@RequestBody CohortInput input) {
        return service.createCohort(input);
    }

    @GetMapping("/admin/venues")
    @PreAuthorize("hasRole('ADMIN')")
    public List<VenueDto> adminVenues() {
        return venues();
    }

    @PostMapping("/admin/venues")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public VenueDto createVenue(@RequestBody VenueInput input) {
        return VenueDto.of(service.createVenue(input));
    }

    @PutMapping("/admin/venues/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public VenueDto updateVenue(@PathVariable UUID id, @RequestBody VenueInput input) {
        return VenueDto.of(service.updateVenue(id, input));
    }
}
