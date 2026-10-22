package edu.university.grps.report.api;

import edu.university.grps.common.Exports;
import edu.university.grps.report.service.ReportService;
import edu.university.grps.report.service.ReportService.Filters;
import edu.university.grps.report.service.ReportService.Table;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/reports")
@PreAuthorize("hasAnyRole('ADMIN','SECRETARY')")
public class ReportController {

    private final ReportService reports;

    public ReportController(ReportService reports) {
        this.reports = reports;
    }

    public record ReportInfo(String key, String title) {}

    @GetMapping
    public List<ReportInfo> list() {
        return ReportService.REPORTS.entrySet().stream().map(e -> new ReportInfo(e.getKey(), e.getValue())).toList();
    }

    /** FR-RPT-08: exports reflect the filters applied on screen; CSV or PDF via {@code format} or Accept. */
    @GetMapping("/{report}")
    public ResponseEntity<?> run(@PathVariable String report,
                                 @RequestParam(required = false) UUID programmeId,
                                 @RequestParam(required = false) UUID cohortId,
                                 @RequestParam(required = false) UUID courseId,
                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                 @RequestParam(required = false) String format,
                                 @RequestHeader(value = HttpHeaders.ACCEPT, required = false) String accept) {
        Table t = reports.run(report, new Filters(programmeId, cohortId, from, to, courseId));
        Exports.Format f = Exports.resolve(format, accept);
        if (f == Exports.Format.JSON) return ResponseEntity.ok(t);
        return Exports.file(f, report + "-" + java.time.LocalDate.now(), t.title(), t.subtitle(), t.columns(), t.rows());
    }

    @GetMapping("/{report}/meta")
    public Map<String, String> meta(@PathVariable String report) {
        return Map.of("key", report, "title", ReportService.REPORTS.getOrDefault(report, report));
    }
}
