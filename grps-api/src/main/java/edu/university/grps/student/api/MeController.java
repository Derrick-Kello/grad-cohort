package edu.university.grps.student.api;

import edu.university.grps.security.CurrentUser;
import edu.university.grps.student.domain.Student;
import edu.university.grps.student.service.StudentAccess;
import edu.university.grps.user.domain.AppUser;
import edu.university.grps.user.domain.RoleCode;
import edu.university.grps.user.repository.AppUserRepository;
import edu.university.grps.user.service.AuthService;
import edu.university.grps.common.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me")
public class MeController {

    private final AppUserRepository users;
    private final StudentAccess access;
    private final AuthService auth;

    public MeController(AppUserRepository users, StudentAccess access, AuthService auth) {
        this.users = users;
        this.access = access;
        this.auth = auth;
    }

    public record MeDto(UUID id, String email, String number, String firstName, String lastName, String name,
                        String phone, List<String> roles, UUID studentId, String programmeCode, String programmeLevel,
                        boolean emailDigest) {}

    public record PreferencesRequest(boolean emailDigest, String phone) {}

    public record PasswordRequest(@NotBlank String currentPassword, @NotBlank String newPassword) {}

    @GetMapping
    @Transactional(readOnly = true)
    public MeDto me() {
        AppUser u = current();
        Student s = u.hasRole(RoleCode.STUDENT) ? access.currentStudentIfAny().orElse(null) : null;
        return new MeDto(u.getId(), u.getEmail(), u.getStaffOrStudentNo(), u.getFirstName(), u.getLastName(), u.getFullName(),
                u.getPhone(), u.roleCodes().stream().map(RoleCode::name).sorted().toList(), s == null ? null : s.getId(),
                s == null ? null : s.getProgramme().getCode(), s == null ? null : s.getProgramme().getLevel().name(),
                u.isEmailDigest());
    }

    @PutMapping("/preferences")
    @Transactional
    public MeDto preferences(@RequestBody PreferencesRequest request) {
        AppUser u = current();
        u.setEmailDigest(request.emailDigest());
        if (request.phone() != null) u.setPhone(request.phone().isBlank() ? null : request.phone().trim());
        return me();
    }

    @PutMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void changePassword(@RequestBody @Valid PasswordRequest request) {
        auth.changePassword(current(), request.currentPassword(), request.newPassword());
    }

    private AppUser current() {
        return users.findById(CurrentUser.id()).orElseThrow(() -> ApiException.notFound("User"));
    }
}
