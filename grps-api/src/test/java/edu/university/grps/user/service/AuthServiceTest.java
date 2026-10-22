package edu.university.grps.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import edu.university.grps.audit.AuditService;
import edu.university.grps.common.ApiException;
import edu.university.grps.common.ErrorCode;
import edu.university.grps.common.GrpsProperties;
import edu.university.grps.notification.service.NotificationService;
import edu.university.grps.security.TokenService;
import edu.university.grps.support.Fixtures;
import edu.university.grps.user.domain.AppUser;
import edu.university.grps.user.domain.RoleCode;
import edu.university.grps.user.repository.ActionTokenRepository;
import edu.university.grps.user.repository.AppUserRepository;
import edu.university.grps.user.repository.RefreshTokenRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock AppUserRepository users;
    @Mock RefreshTokenRepository refreshTokens;
    @Mock ActionTokenRepository actionTokens;
    @Mock TokenService tokens;
    @Mock NotificationService notifications;
    @Mock AuditService audit;
    @Mock GrpsProperties props;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private AuthService service;
    private AppUser user;

    @BeforeEach
    void setUp() {
        service = new AuthService(users, refreshTokens, actionTokens, tokens, encoder, notifications, audit, props,
                Clock.fixed(Instant.parse("2026-10-19T08:00:00Z"), ZoneOffset.UTC));
        user = Fixtures.user("Kofi", RoleCode.LECTURER);
        user.setPasswordHash(encoder.encode("CorrectHorse42"));
        when(users.findByLogin(anyString())).thenReturn(Optional.of(user));
    }

    @Test
    void FR_AUTH_02_five_failures_lock_the_account_for_15_minutes() {
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> service.login("kofi@test.local", "wrong-password"))
                    .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.INVALID_CREDENTIALS));
        }
        assertThat(user.getLockedUntil()).isEqualTo(Instant.parse("2026-10-19T08:15:00Z"));
        // The right password is refused while locked, with the same message.
        assertThatThrownBy(() -> service.login("kofi@test.local", "CorrectHorse42"))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.INVALID_CREDENTIALS));
    }

    @Test
    void FR_AUTH_01_invalid_sign_in_does_not_say_which_field_was_wrong() {
        assertThatThrownBy(() -> service.login("kofi@test.local", "nope"))
                .hasMessage("The sign-in details are incorrect, or the account is temporarily locked.");
    }

    @Test
    void deactivated_users_cannot_sign_in() {
        user.setActive(false);
        assertThatThrownBy(() -> service.login("kofi@test.local", "CorrectHorse42")).isInstanceOf(ApiException.class);
    }
}
