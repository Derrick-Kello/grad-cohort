package edu.university.grps.user.service;

import edu.university.grps.audit.AuditService;
import edu.university.grps.common.ApiException;
import edu.university.grps.common.ErrorCode;
import edu.university.grps.common.GrpsProperties;
import edu.university.grps.notification.service.NotificationService;
import edu.university.grps.security.PasswordPolicy;
import edu.university.grps.security.TokenService;
import edu.university.grps.user.domain.ActionToken;
import edu.university.grps.user.domain.AppUser;
import edu.university.grps.user.domain.RefreshToken;
import edu.university.grps.user.domain.RoleCode;
import edu.university.grps.user.repository.ActionTokenRepository;
import edu.university.grps.user.repository.AppUserRepository;
import edu.university.grps.user.repository.RefreshTokenRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    static final int MAX_FAILURES = 5;
    static final Duration FAILURE_WINDOW = Duration.ofMinutes(15);
    static final Duration LOCK_DURATION = Duration.ofMinutes(15);
    static final Duration RESET_TTL = Duration.ofMinutes(30);
    private static final String GENERIC_FAILURE = "The sign-in details are incorrect, or the account is temporarily locked.";

    private final AppUserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final ActionTokenRepository actionTokens;
    private final TokenService tokens;
    private final PasswordEncoder encoder;
    private final NotificationService notifications;
    private final AuditService audit;
    private final GrpsProperties props;
    private final Clock clock;
    // Hash of a random value, checked when the account does not exist so timing stays similar.
    private final String dummyHash;

    public AuthService(AppUserRepository users, RefreshTokenRepository refreshTokens, ActionTokenRepository actionTokens,
                       TokenService tokens, PasswordEncoder encoder, NotificationService notifications, AuditService audit,
                       GrpsProperties props, Clock clock) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.actionTokens = actionTokens;
        this.tokens = tokens;
        this.encoder = encoder;
        this.notifications = notifications;
        this.audit = audit;
        this.props = props;
        this.clock = clock;
        this.dummyHash = encoder.encode(java.util.UUID.randomUUID().toString());
    }

    public record TokenPair(String accessToken, String refreshToken, long expiresIn, List<String> roles) {}

    /**
     * FR-AUTH-01 / FR-AUTH-02: invalid sign-ins return 401 without saying which field was wrong;
     * 5 failures in 15 minutes lock the account for 15 minutes.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public TokenPair login(String login, String password) {
        Instant now = clock.instant();
        AppUser user = login == null ? null : users.findByLogin(login.trim()).orElse(null);
        if (user == null || user.getPasswordHash() == null) {
            encoder.matches(password == null ? "" : password, dummyHash);
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS, GENERIC_FAILURE);
        }
        if (!user.isActive() || user.isLocked(now)) {
            encoder.matches(password == null ? "" : password, dummyHash);
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS, GENERIC_FAILURE);
        }
        if (password == null || !encoder.matches(password, user.getPasswordHash())) {
            registerFailure(user, now);
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS, GENERIC_FAILURE);
        }
        user.setFailedLogins(0);
        user.setFirstFailedAt(null);
        user.setLockedUntil(null);
        user.setLastLoginAt(now);
        return issue(user);
    }

    private void registerFailure(AppUser user, Instant now) {
        if (user.getFirstFailedAt() == null || user.getFirstFailedAt().plus(FAILURE_WINDOW).isBefore(now)) {
            user.setFirstFailedAt(now);
            user.setFailedLogins(1);
        } else {
            user.setFailedLogins(user.getFailedLogins() + 1);
        }
        if (user.getFailedLogins() >= MAX_FAILURES) {
            user.setLockedUntil(now.plus(LOCK_DURATION));
            user.setFailedLogins(0);
            user.setFirstFailedAt(null);
            audit.record("ACCOUNT_LOCKED", "AppUser", user.getId(), Map.of("until", user.getLockedUntil()));
        }
    }

    /** SEC-03: refresh tokens rotate on use; reusing a revoked token revokes every session of that user. */
    @Transactional(noRollbackFor = ApiException.class)
    public TokenPair refresh(String refreshToken) {
        Instant now = clock.instant();
        RefreshToken stored = refreshToken == null ? null
                : refreshTokens.findByTokenHash(TokenService.sha256(refreshToken)).orElse(null);
        if (stored == null) throw new ApiException(ErrorCode.UNAUTHENTICATED, "Your session has ended. Sign in again.");
        AppUser user = stored.getUser();
        if (stored.getRevokedAt() != null) {
            refreshTokens.revokeAllForUser(user.getId(), now);
            throw new ApiException(ErrorCode.UNAUTHENTICATED, "Your session has ended. Sign in again.");
        }
        if (stored.getExpiresAt().isBefore(now) || !user.isActive()) {
            stored.setRevokedAt(now);
            throw new ApiException(ErrorCode.UNAUTHENTICATED, "Your session has ended. Sign in again.");
        }
        stored.setRevokedAt(now);
        return issue(user);
    }

    @Transactional
    public void logout(String refreshToken) {
        if (refreshToken == null) return;
        refreshTokens.findByTokenHash(TokenService.sha256(refreshToken)).ifPresent(t -> {
            if (t.getRevokedAt() == null) t.setRevokedAt(clock.instant());
        });
    }

    /** FR-AUTH-03: always answers the same way, whether or not the email exists. */
    @Transactional
    public void forgotPassword(String email) {
        if (email == null || email.isBlank()) return;
        users.findByEmailIgnoreCase(email.trim()).filter(AppUser::isActive).ifPresent(user -> {
            String token = TokenService.newOpaqueToken();
            ActionToken t = new ActionToken();
            t.setUser(user);
            t.setPurpose(ActionToken.Purpose.RESET);
            t.setTokenHash(TokenService.sha256(token));
            t.setExpiresAt(clock.instant().plus(RESET_TTL));
            actionTokens.save(t);
            notifications.sendAccountEmail(user.getEmail(), "Reset your GRPS password",
                    "We received a request to reset your password. The link works once and expires in 30 minutes. "
                            + "If you did not ask for this, ignore this email.",
                    props.frontendUrl() + "/reset-password?token=" + token, "Choose a new password");
        });
    }

    @Transactional
    public void resetPassword(String token, String newPassword) {
        setPasswordWithToken(token, newPassword, ActionToken.Purpose.RESET);
    }

    @Transactional
    public void activate(String token, String newPassword) {
        setPasswordWithToken(token, newPassword, ActionToken.Purpose.ACTIVATION);
    }

    @Transactional
    public void changePassword(AppUser user, String currentPassword, String newPassword) {
        if (user.getPasswordHash() == null || currentPassword == null || !encoder.matches(currentPassword, user.getPasswordHash())) {
            throw ApiException.validation("Your current password is incorrect.");
        }
        PasswordPolicy.check(newPassword);
        user.setPasswordHash(encoder.encode(newPassword));
        refreshTokens.revokeAllForUser(user.getId(), clock.instant());
        audit.record("PASSWORD_CHANGED", "AppUser", user.getId(), Map.of());
    }

    private void setPasswordWithToken(String token, String newPassword, ActionToken.Purpose purpose) {
        Instant now = clock.instant();
        ActionToken t = token == null ? null
                : actionTokens.findByTokenHashAndPurpose(TokenService.sha256(token), purpose).orElse(null);
        if (t == null || t.getUsedAt() != null || t.getExpiresAt().isBefore(now)) {
            throw ApiException.validation("This link is invalid or has expired. Request a new one.");
        }
        PasswordPolicy.check(newPassword);
        AppUser user = t.getUser();
        user.setPasswordHash(encoder.encode(newPassword));
        user.setLockedUntil(null);
        user.setFailedLogins(0);
        user.setFirstFailedAt(null);
        t.setUsedAt(now);
        refreshTokens.revokeAllForUser(user.getId(), now);
        audit.record(purpose == ActionToken.Purpose.RESET ? "PASSWORD_RESET" : "ACCOUNT_ACTIVATED", "AppUser", user.getId(), Map.of());
    }

    private TokenPair issue(AppUser user) {
        String refresh = TokenService.newOpaqueToken();
        RefreshToken stored = new RefreshToken();
        stored.setUser(user);
        stored.setTokenHash(TokenService.sha256(refresh));
        stored.setExpiresAt(clock.instant().plus(props.jwt().refreshTtl()));
        refreshTokens.save(stored);
        List<String> roles = user.roleCodes().stream().map(RoleCode::name).sorted().toList();
        return new TokenPair(tokens.issueAccessToken(user), refresh, tokens.accessTtlSeconds(), roles);
    }

    @Transactional
    public int cleanupTokens() {
        Instant now = clock.instant();
        return refreshTokens.deleteExpired(now) + actionTokens.deleteExpired(now);
    }
}
