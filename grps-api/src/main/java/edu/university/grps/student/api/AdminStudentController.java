package edu.university.grps.student.api;

import edu.university.grps.common.PageResponse;
import edu.university.grps.student.api.StudentDtos.StudentDetailDto;
import edu.university.grps.student.api.StudentDtos.StudentSummaryDto;
import edu.university.grps.student.domain.StudentStatus;
import edu.university.grps.student.service.StudentService;
import edu.university.grps.student.service.StudentService.EnrolInput;
import edu.university.grps.student.service.StudentService.ExtensionInput;
import edu.university.grps.student.service.StudentService.SupervisorInput;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
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
public class AdminStudentController {

    private final StudentService service;

    public AdminStudentController(StudentService service) {
        this.service = service;
    }

    public record ReasonRequest(String reason) {}

    @GetMapping("/admin/students")
    @PreAuthorize("hasAnyRole('ADMIN','SECRETARY')")
    public PageResponse<StudentSummaryDto> search(@RequestParam(required = false) String q,
                                                  @RequestParam(required = false) UUID programmeId,
                                                  @RequestParam(required = false) UUID cohortId,
                                                  @RequestParam(required = false) StudentStatus status,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "25") int size,
                                                  @RequestParam(defaultValue = "name,asc") String sort) {
        String[] parts = sort.split(",");
        Sort.Direction dir = parts.length > 1 && parts[1].equalsIgnoreCase("desc") ? Sort.Direction.DESC : Sort.Direction.ASC;
        Sort s = switch (parts[0]) {
            case "number" -> Sort.by(dir, "user.staffOrStudentNo");
            case "enrolledOn" -> Sort.by(dir, "enrolledOn");
            case "programmeEndOn" -> Sort.by(dir, "programmeEndOn");
            case "checkpointDueOn" -> Sort.by(dir, "checkpointDueOn");
            case "status" -> Sort.by(dir, "status");
            default -> Sort.by(dir, "user.lastName", "user.firstName");
        };
        return PageResponse.of(service.search(q, programmeId, cohortId, status, PageRequest.of(page, Math.min(size, 200), s)), x -> x);
    }

    @GetMapping("/admin/students/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SECRETARY','SUPERVISOR')")
    public StudentDetailDto get(@PathVariable UUID id) {
        return service.detail(id);
    }

    @PostMapping("/admin/students")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public StudentSummaryDto enrol(@RequestBody EnrolInput input) {
        return service.enrol(input);
    }

    @PutMapping("/admin/students/{id}/supervisor")
    @PreAuthorize("hasRole('ADMIN')")
    public StudentDetailDto assignSupervisor(@PathVariable UUID id, @RequestBody SupervisorInput input) {
        return service.assignSupervisor(id, input);
    }

    @DeleteMapping("/admin/students/{id}/supervisions/{supervisionId}")
    @PreAuthorize("hasRole('ADMIN')")
    public StudentDetailDto endCoSupervision(@PathVariable UUID id, @PathVariable UUID supervisionId) {
        return service.endCoSupervision(id, supervisionId);
    }

    @PostMapping("/admin/students/{id}/extensions")
    @PreAuthorize("hasRole('ADMIN')")
    public StudentDetailDto recordExtension(@PathVariable UUID id, @RequestBody ExtensionInput input) {
        return service.recordExtension(id, input);
    }

    @PostMapping("/admin/students/{id}/expulsion/confirm")
    @PreAuthorize("hasRole('ADMIN')")
    public StudentDetailDto confirmExpulsion(@PathVariable UUID id, @RequestBody ReasonRequest request) {
        return service.confirmExpulsion(id, request.reason());
    }

    @PostMapping("/admin/students/{id}/expulsion/clear")
    @PreAuthorize("hasRole('ADMIN')")
    public StudentDetailDto clearFlag(@PathVariable UUID id, @RequestBody ReasonRequest request) {
        return service.clearFlag(id, request.reason());
    }

    @PostMapping("/admin/students/{id}/withdraw")
    @PreAuthorize("hasRole('ADMIN')")
    public StudentDetailDto withdraw(@PathVariable UUID id, @RequestBody ReasonRequest request) {
        return service.withdraw(id, request.reason());
    }
}
