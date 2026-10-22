package edu.university.grps.student.api;

import edu.university.grps.common.PageResponse;
import edu.university.grps.student.repository.StudentRepositories.StudentRepository;
import edu.university.grps.student.service.ImportService;
import edu.university.grps.student.service.ImportService.ImportResult;
import edu.university.grps.student.service.StudentAccess;
import edu.university.grps.user.api.UserDtos.UserDto;
import edu.university.grps.user.domain.RoleCode;
import edu.university.grps.user.service.UserService;
import edu.university.grps.user.service.UserService.UserInput;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
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

/** User management (FR-USR-01..03). Lives here because role changes check enrolment and supervisions. */
@RestController
@RequestMapping("/api/v1/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final UserService users;
    private final ImportService importer;
    private final StudentRepository students;
    private final StudentAccess access;

    public AdminUserController(UserService users, ImportService importer, StudentRepository students, StudentAccess access) {
        this.users = users;
        this.importer = importer;
        this.students = students;
        this.access = access;
    }

    public record RolesRequest(Set<RoleCode> roles) {}

    public record ActiveRequest(boolean active) {}

    @GetMapping
    public PageResponse<UserDto> search(@RequestParam(required = false) String q,
                                        @RequestParam(required = false) RoleCode role,
                                        @RequestParam(defaultValue = "false") boolean staffOnly,
                                        @RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "25") int size,
                                        @RequestParam(defaultValue = "lastName,asc") String sort) {
        String[] parts = sort.split(",");
        Sort s = Sort.by(parts.length > 1 && parts[1].equalsIgnoreCase("desc") ? Sort.Direction.DESC : Sort.Direction.ASC,
                switch (parts[0]) {
                    case "email", "firstName", "lastName", "createdAt", "lastLoginAt" -> parts[0];
                    case "number" -> "staffOrStudentNo";
                    default -> "lastName";
                });
        return PageResponse.of(users.search(q, role, staffOnly, PageRequest.of(page, Math.min(size, 200), s)), UserDto::of);
    }

    @GetMapping("/{id}")
    public UserDto get(@PathVariable UUID id) {
        return UserDto.of(users.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserDto create(@RequestBody UserInput input) {
        if (input.roles() != null && input.roles().contains(RoleCode.STUDENT)) {
            throw edu.university.grps.common.ApiException.validation("Enrol students from the Students page so they get a programme and supervisor.");
        }
        return UserDto.of(users.create(input));
    }

    @PutMapping("/{id}")
    public UserDto update(@PathVariable UUID id, @RequestBody UserInput input) {
        return UserDto.of(users.update(id, input));
    }

    @PutMapping("/{id}/roles")
    @Transactional
    public UserDto setRoles(@PathVariable UUID id, @RequestBody RolesRequest request) {
        boolean supervising = !access.supervisedStudentIds(id).isEmpty();
        boolean enrolled = students.findByUserId(id).isPresent();
        return UserDto.of(users.setRoles(id, request.roles(), supervising, enrolled));
    }

    @PutMapping("/{id}/active")
    public UserDto setActive(@PathVariable UUID id, @RequestBody ActiveRequest request) {
        return UserDto.of(users.setActive(id, request.active()));
    }

    @PostMapping("/{id}/unlock")
    public UserDto unlock(@PathVariable UUID id) {
        return UserDto.of(users.unlock(id));
    }

    @PostMapping("/{id}/resend-activation")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void resendActivation(@PathVariable UUID id) {
        users.resendActivation(id);
    }

    @PostMapping(path = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportResult importCsv(@RequestPart("file") MultipartFile file,
                                  @RequestParam(defaultValue = "false") boolean validateOnly) {
        return importer.importCsv(file, validateOnly);
    }
}
