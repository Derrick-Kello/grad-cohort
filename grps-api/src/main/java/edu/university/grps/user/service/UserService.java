package edu.university.grps.user.service;

import edu.university.grps.audit.AuditService;
import edu.university.grps.common.ApiException;
import edu.university.grps.common.ErrorCode;
import edu.university.grps.common.GrpsProperties;
import edu.university.grps.notification.service.NotificationService;
import edu.university.grps.security.CurrentUser;
import edu.university.grps.security.TokenService;
import edu.university.grps.user.domain.ActionToken;
import edu.university.grps.user.domain.AppUser;
import edu.university.grps.user.domain.Role;
import edu.university.grps.user.domain.RoleCode;
import edu.university.grps.user.repository.ActionTokenRepository;
import edu.university.grps.user.repository.AppUserRepository;
import edu.university.grps.user.repository.RefreshTokenRepository;
import edu.university.grps.user.repository.RoleRepository;
import java.time.Clock;
import java.time.Duration;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class UserService {

    public static final Duration ACTIVATION_TTL = Duration.ofHours(72);
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Set<RoleCode> ADMIN_ROLES = EnumSet.of(RoleCode.ADMIN, RoleCode.SUPER_ADMIN);

    private final AppUserRepository users;
    private final RoleRepository roles;
    private final ActionTokenRepository actionTokens;
    private final RefreshTokenRepository refreshTokens;
    private final NotificationService notifications;
    private final AuditService audit;
    private final GrpsProperties props;
    private final Clock clock;

    public UserService(AppUserRepository users, RoleRepository roles, ActionTokenRepository actionTokens,
                       RefreshTokenRepository refreshTokens, NotificationService notifications, AuditService audit,
                       GrpsProperties props, Clock clock) {
        this.users = users;
        this.roles = roles;
        this.actionTokens = actionTokens;
        this.refreshTokens = refreshTokens;
        this.notifications = notifications;
        this.audit = audit;
        this.props = props;
        this.clock = clock;
    }

    public record UserInput(String email, String number, String firstName, String lastName, String phone,
                            Set<RoleCode> roles, Long version) {}

    @Transactional(readOnly = true)
    public AppUser get(UUID id) {
        return users.findById(id).orElseThrow(() -> ApiException.notFound("User"));
    }

    @Transactional(readOnly = true)
    public Page<AppUser> search(String q, RoleCode role, boolean staffOnly, Pageable pageable) {
        String pattern = q == null || q.isBlank() ? "" : "%" + q.trim().toLowerCase() + "%";
        return users.search(pattern, role, staffOnly, pageable);
    }

    /** FR-AUTH-04: new accounts get an activation link; temporary passwords are never emailed. */
    public AppUser create(UserInput in) {
        validateIdentity(in, null);
        Set<RoleCode> wanted = in.roles() == null ? Set.of() : in.roles();
        if (wanted.isEmpty()) throw ApiException.validation("Choose at least one role.");
        checkRoleRules(wanted, Set.of());
        AppUser user = new AppUser();
        user.setEmail(in.email().trim().toLowerCase());
        user.setStaffOrStudentNo(in.number().trim());
        user.setFirstName(in.firstName().trim());
        user.setLastName(in.lastName().trim());
        user.setPhone(blankToNull(in.phone()));
        user.setActive(true);
        user.setRoles(resolveRoles(wanted));
        user = users.save(user);
        audit.record("USER_CREATED", "AppUser", user.getId(), null, null,
                Map.of("email", user.getEmail(), "number", user.getStaffOrStudentNo(), "roles", wanted.toString()));
        sendActivation(user);
        return user;
    }

    public AppUser update(UUID id, UserInput in) {
        AppUser user = get(id);
        if (in.version() != null && !Objects.equals(in.version(), user.getVersion())) {
            throw new ApiException(ErrorCode.CONCURRENT_UPDATE, "Someone else changed this user. Reload and try again.");
        }
        validateIdentity(in, user);
        Map<String, Object> before = describe(user);
        user.setEmail(in.email().trim().toLowerCase());
        user.setStaffOrStudentNo(in.number().trim());
        user.setFirstName(in.firstName().trim());
        user.setLastName(in.lastName().trim());
        user.setPhone(blankToNull(in.phone()));
        audit.record("USER_UPDATED", "AppUser", user.getId(), null, before, describe(user));
        return user;
    }

    /** FR-USR-01: deactivated users cannot sign in; their history is kept. */
    public AppUser setActive(UUID id, boolean active) {
        AppUser user = get(id);
        if (user.getId().equals(CurrentUser.optionalId().orElse(null)) && !active) {
            throw ApiException.rule("You cannot deactivate your own account.");
        }
        if (user.isActive() == active) return user;
        user.setActive(active);
        if (!active) refreshTokens.revokeAllForUser(user.getId(), clock.instant());
        audit.record(active ? "USER_ACTIVATED" : "USER_DEACTIVATED", "AppUser", user.getId(), Map.of("active", active));
        return user;
    }

    /** FR-USR-03 / BR-18: the Supervisor role needs the Lecturer role. */
    public AppUser setRoles(UUID id, Set<RoleCode> wanted, boolean hasOpenSupervisions, boolean isEnrolledStudent) {
        AppUser user = get(id);
        Set<RoleCode> current = user.roleCodes();
        if (wanted == null || wanted.isEmpty()) throw ApiException.validation("An account needs at least one role.");
        checkRoleRules(wanted, current);
        if (isEnrolledStudent && !wanted.contains(RoleCode.STUDENT)) {
            throw ApiException.rule("This account is an enrolled student; the Student role cannot be removed.");
        }
        if (hasOpenSupervisions && !(wanted.contains(RoleCode.SUPERVISOR) && wanted.contains(RoleCode.LECTURER))) {
            throw ApiException.rule("Reassign this supervisor's students before removing the Lecturer or Supervisor role.");
        }
        user.setRoles(resolveRoles(wanted));
        audit.record("ROLES_CHANGED", "AppUser", user.getId(), null, Map.of("roles", current.toString()),
                Map.of("roles", wanted.toString()));
        refreshTokens.revokeAllForUser(user.getId(), clock.instant());
        return user;
    }

    public AppUser unlock(UUID id) {
        AppUser user = get(id);
        user.setLockedUntil(null);
        user.setFailedLogins(0);
        user.setFirstFailedAt(null);
        audit.record("USER_UNLOCKED", "AppUser", user.getId(), Map.of());
        return user;
    }

    public void resendActivation(UUID id) {
        AppUser user = get(id);
        if (user.getPasswordHash() != null) throw ApiException.invalidState("This account is already activated.");
        sendActivation(user);
    }

    private void sendActivation(AppUser user) {
        String token = TokenService.newOpaqueToken();
        ActionToken t = new ActionToken();
        t.setUser(user);
        t.setPurpose(ActionToken.Purpose.ACTIVATION);
        t.setTokenHash(TokenService.sha256(token));
        t.setExpiresAt(clock.instant().plus(ACTIVATION_TTL));
        actionTokens.save(t);
        String link = props.frontendUrl() + "/activate?token=" + token;
        notifications.sendAccountEmail(user.getEmail(), "Activate your GRPS account",
                "Hello " + user.getFirstName() + ",\nAn account has been created for you in the Graduate Research "
                        + "Progress System. Set your password to activate it. The link expires in 72 hours.",
                link, "Set my password");
    }

    void checkRoleRules(Set<RoleCode> wanted, Set<RoleCode> current) {
        if (wanted.contains(RoleCode.SUPERVISOR) && !wanted.contains(RoleCode.LECTURER)) {
            throw ApiException.rule("The Supervisor role can only be granted to an account with the Lecturer role.");
        }
        boolean touchesAdmin = ADMIN_ROLES.stream().anyMatch(r -> wanted.contains(r) != current.contains(r));
        if (touchesAdmin && !CurrentUser.hasRole(RoleCode.SUPER_ADMIN)) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Only a Super Admin can grant or remove admin roles.");
        }
    }

    private Set<Role> resolveRoles(Set<RoleCode> codes) {
        Set<Role> resolved = new HashSet<>();
        for (RoleCode code : codes) {
            resolved.add(roles.findByCode(code).orElseThrow(() -> new IllegalStateException("Role not seeded: " + code)));
        }
        return resolved;
    }

    private void validateIdentity(UserInput in, AppUser existing) {
        if (in.email() == null || !EMAIL.matcher(in.email().trim()).matches()) throw ApiException.validation("Enter a valid email address.");
        if (in.number() == null || in.number().isBlank()) throw ApiException.validation("Staff or student number is required.");
        if (in.firstName() == null || in.firstName().isBlank() || in.lastName() == null || in.lastName().isBlank()) {
            throw ApiException.validation("First and last name are required.");
        }
        boolean emailChanged = existing == null || !existing.getEmail().equalsIgnoreCase(in.email().trim());
        if (emailChanged && users.existsByEmailIgnoreCase(in.email().trim())) {
            throw new ApiException(ErrorCode.DUPLICATE, "Another account already uses this email.");
        }
        boolean numberChanged = existing == null || !existing.getStaffOrStudentNo().equalsIgnoreCase(in.number().trim());
        if (numberChanged && users.existsByStaffOrStudentNoIgnoreCase(in.number().trim())) {
            throw new ApiException(ErrorCode.DUPLICATE, "Another account already uses this staff or student number.");
        }
    }

    private static Map<String, Object> describe(AppUser u) {
        return Map.of("email", u.getEmail(), "number", u.getStaffOrStudentNo(), "name", u.getFullName(),
                "roles", u.roleCodes().stream().map(Enum::name).sorted().collect(Collectors.joining(",")));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
