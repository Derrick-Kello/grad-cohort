package edu.university.grps.security;

import edu.university.grps.common.ApiException;
import edu.university.grps.common.ErrorCode;
import edu.university.grps.user.domain.RoleCode;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/** Reads the signed-in user from the security context. */
public final class CurrentUser {

    private CurrentUser() {}

    public static Optional<UUID> optionalId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            return Optional.of(UUID.fromString(jwt.getSubject()));
        }
        return Optional.empty();
    }

    public static UUID id() {
        return optionalId().orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED, "Sign in to continue."));
    }

    public static boolean hasRole(RoleCode role) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        String wanted = "ROLE_" + role.name();
        return auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals(wanted));
    }

    public static boolean isAdmin() {
        return hasRole(RoleCode.ADMIN);
    }
}
