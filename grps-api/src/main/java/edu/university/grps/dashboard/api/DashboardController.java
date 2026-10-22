package edu.university.grps.dashboard.api;

import edu.university.grps.dashboard.service.DashboardService;
import edu.university.grps.dashboard.service.DashboardService.AchievementDto;
import edu.university.grps.dashboard.service.DashboardService.AdminDashboardDto;
import edu.university.grps.dashboard.service.DashboardService.LecturerCourseSummary;
import edu.university.grps.dashboard.service.DashboardService.LibrarianDashboardDto;
import edu.university.grps.dashboard.service.DashboardService.SecretaryDashboardDto;
import edu.university.grps.dashboard.service.DashboardService.StudentDashboardDto;
import edu.university.grps.dashboard.service.DashboardService.SupervisorDashboardDto;
import edu.university.grps.dashboard.service.DashboardService.SupervisorStudentDto;
import edu.university.grps.dashboard.service.StatementService;
import edu.university.grps.dashboard.service.StatementService.VerificationDto;
import edu.university.grps.student.api.StudentDtos.StudentSummaryDto;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class DashboardController {

    private final DashboardService dashboards;
    private final StatementService statements;

    public DashboardController(DashboardService dashboards, StatementService statements) {
        this.dashboards = dashboards;
        this.statements = statements;
    }

    @GetMapping("/students/me/dashboard")
    @PreAuthorize("hasRole('STUDENT')")
    public StudentDashboardDto student() {
        return dashboards.studentDashboard();
    }

    @GetMapping("/students/me/achievements")
    @PreAuthorize("hasRole('STUDENT')")
    public List<AchievementDto> achievements() {
        return dashboards.achievements();
    }

    @GetMapping(path = "/students/me/statement.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<byte[]> statement() {
        StatementService.Statement st = statements.generate();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("progress-statement-" + st.code() + ".pdf").build().toString())
                .contentType(MediaType.APPLICATION_PDF).body(st.pdf());
    }

    @GetMapping("/public/statements/verify")
    public VerificationDto verify(@RequestParam String code) {
        return statements.verify(code);
    }

    @GetMapping("/supervisor/dashboard")
    @PreAuthorize("hasRole('SUPERVISOR')")
    public SupervisorDashboardDto supervisor() {
        return dashboards.supervisorDashboard();
    }

    @GetMapping("/supervisor/students")
    @PreAuthorize("hasRole('SUPERVISOR')")
    public List<StudentSummaryDto> supervisees() {
        return dashboards.supervisorDashboard().students();
    }

    @GetMapping("/supervisor/students/{id}")
    @PreAuthorize("hasAnyRole('SUPERVISOR','ADMIN','SECRETARY')")
    public SupervisorStudentDto supervisee(@PathVariable UUID id) {
        return dashboards.supervisorStudent(id);
    }

    @GetMapping("/admin/dashboard")
    @PreAuthorize("hasRole('ADMIN')")
    public AdminDashboardDto admin() {
        return dashboards.adminDashboard();
    }

    @GetMapping("/secretary/dashboard")
    @PreAuthorize("hasAnyRole('SECRETARY','ADMIN')")
    public SecretaryDashboardDto secretary() {
        return dashboards.secretaryDashboard();
    }

    @GetMapping("/librarian/dashboard")
    @PreAuthorize("hasAnyRole('LIBRARIAN','ADMIN')")
    public LibrarianDashboardDto librarian() {
        return dashboards.librarianDashboard();
    }

    @GetMapping("/lecturer/dashboard")
    @PreAuthorize("hasRole('LECTURER')")
    public List<LecturerCourseSummary> lecturer() {
        return dashboards.lecturerDashboard();
    }
}
