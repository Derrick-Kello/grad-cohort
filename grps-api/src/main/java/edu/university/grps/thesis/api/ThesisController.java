package edu.university.grps.thesis.api;

import edu.university.grps.common.PdfTables;
import edu.university.grps.common.Times;
import edu.university.grps.storage.SignedLinks;
import edu.university.grps.thesis.domain.ThesisSubmission;
import edu.university.grps.thesis.service.FormatRuleService;
import edu.university.grps.thesis.service.FormatRuleService.RuleDto;
import edu.university.grps.thesis.service.FormatRuleService.RuleInput;
import edu.university.grps.thesis.service.ThesisService;
import edu.university.grps.thesis.service.ThesisService.FormatReportDto;
import edu.university.grps.thesis.service.ThesisService.ThesisDto;
import edu.university.grps.thesis.service.ThesisService.ThesisOverviewDto;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1")
public class ThesisController {

    private final ThesisService service;
    private final FormatRuleService formatRules;
    private final Clock clock;

    public ThesisController(ThesisService service, FormatRuleService formatRules, Clock clock) {
        this.service = service;
        this.formatRules = formatRules;
        this.clock = clock;
    }

    public record ReasonRequest(String reason) {}

    @GetMapping("/thesis/me")
    @PreAuthorize("hasRole('STUDENT')")
    public ThesisOverviewDto mine() {
        return service.overview();
    }

    @PostMapping(path = "/thesis", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('STUDENT')")
    @ResponseStatus(HttpStatus.CREATED)
    public ThesisDto submit(@RequestPart("file") MultipartFile file, @RequestParam String title,
                            @RequestParam("abstract") String abstractText, @RequestParam String keywords) {
        return service.submit(file, title, abstractText, keywords);
    }

    @GetMapping("/thesis/student/{studentId}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR','SECRETARY','STUDENT')")
    public ThesisOverviewDto forStudent(@PathVariable UUID studentId) {
        return service.overviewFor(studentId);
    }

    @GetMapping("/thesis/{id}")
    public ThesisDto get(@PathVariable UUID id) {
        return service.get(id);
    }

    @GetMapping("/thesis/{id}/file")
    public SignedLinks.Link file(@PathVariable UUID id) {
        return service.fileLink(id);
    }

    @GetMapping("/thesis/{id}/format-report")
    public FormatReportDto formatReport(@PathVariable UUID id) {
        return service.formatReport(id);
    }

    /** FR-THS-04: the student can download the format report. */
    @GetMapping(path = "/thesis/{id}/format-report.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> formatReportPdf(@PathVariable UUID id) {
        FormatReportDto report = service.formatReport(id);
        ThesisDto thesis = service.get(id);
        List<List<String>> rows = report.results().stream().map(r -> List.of(r.ruleCode(),
                r.skipped() ? "Skipped" : r.passed() ? "Pass" : "Fail", r.detail(), r.location() == null ? "" : r.location())).toList();
        byte[] pdf = PdfTables.render("Thesis format check report",
                List.of(thesis.studentName() + " (" + thesis.studentNumber() + "), version " + thesis.versionNo() + ", " + thesis.fileName(),
                        "Result: " + report.status().replace('_', ' ').toLowerCase()
                                + (report.pageCount() == null ? "" : ". Pages: " + report.pageCount()),
                        "Generated " + Times.formatDateTime(clock.instant())),
                List.of("Rule", "Result", "Detail", "Location"), rows);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("format-report-v" + thesis.versionNo() + ".pdf").build().toString())
                .contentType(MediaType.APPLICATION_PDF).body(pdf);
    }

    @GetMapping("/thesis/{id}/screening-report")
    public Map<String, Object> screeningReport(@PathVariable UUID id) {
        ThesisDto t = service.get(id);
        var body = new java.util.LinkedHashMap<String, Object>();
        body.put("thesisId", t.id());
        body.put("versionNo", t.versionNo());
        body.put("status", t.status());
        body.put("similarityPct", t.similarityPct());
        body.put("aiPct", t.aiPct());
        body.put("similarityLimit", t.similarityLimit());
        body.put("aiLimit", t.aiLimit());
        body.put("manual", t.screeningManual());
        body.put("providerReportUrl", t.providerReportUrl());
        body.put("hasProviderReport", t.hasProviderReport());
        body.put("overrideReason", t.overrideReason());
        body.put("overrideByName", t.overrideByName());
        return body;
    }

    @GetMapping("/thesis/{id}/provider-report")
    public SignedLinks.Link providerReport(@PathVariable UUID id) {
        return service.providerReportLink(id);
    }

    // Admin

    @GetMapping("/admin/thesis")
    @PreAuthorize("hasRole('ADMIN')")
    public List<ThesisDto> adminList(@RequestParam(required = false) ThesisSubmission.Status status) {
        return service.adminList(status);
    }

    @PostMapping(path = "/admin/thesis/{id}/manual-screening", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ThesisDto manualScreening(@PathVariable UUID id, @RequestParam BigDecimal similarityPct, @RequestParam BigDecimal aiPct,
                                     @RequestPart("report") MultipartFile report) {
        return service.manualScreening(id, similarityPct, aiPct, report);
    }

    @PostMapping("/admin/thesis/{id}/override")
    @PreAuthorize("hasRole('ADMIN')")
    public ThesisDto override(@PathVariable UUID id, @RequestBody ReasonRequest request) {
        return service.override(id, request.reason());
    }

    @GetMapping("/admin/programmes/{id}/format-rules")
    @PreAuthorize("hasRole('ADMIN')")
    public List<RuleDto> rules(@PathVariable UUID id) {
        return formatRules.list(id);
    }

    @PutMapping("/admin/programmes/{id}/format-rules")
    @PreAuthorize("hasRole('ADMIN')")
    public List<RuleDto> updateRules(@PathVariable UUID id, @RequestBody List<RuleInput> input) {
        return formatRules.update(id, input);
    }
}
