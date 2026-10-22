package edu.university.grps.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatNoException;

import edu.university.grps.audit.AuditService;
import edu.university.grps.common.ApiException;
import edu.university.grps.common.ErrorCode;
import edu.university.grps.common.GrpsProperties;
import edu.university.grps.notification.service.NotificationService;
import edu.university.grps.support.Fixtures;
import edu.university.grps.user.domain.RoleCode;
import edu.university.grps.user.repository.ActionTokenRepository;
import edu.university.grps.user.repository.AppUserRepository;
import edu.university.grps.user.repository.RefreshTokenRepository;
import edu.university.grps.user.repository.RoleRepository;
import java.time.Clock;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock AppUserRepository users;
    @Mock RoleRepository roles;
    @Mock ActionTokenRepository actionTokens;
    @Mock RefreshTokenRepository refreshTokens;
    @Mock NotificationService notifications;
    @Mock AuditService audit;
    @Mock GrpsProperties props;

    private UserService service;

    @BeforeEach
    void setUp() {
        service = new UserService(users, roles, actionTokens, refreshTokens, notifications, audit, props, Clock.systemUTC());
        Fixtures.signIn(UUID.randomUUID(), RoleCode.ADMIN);
    }

    @AfterEach
    void tearDown() {
        Fixtures.signOut();
    }

    @Test
    void BR_18_supervisor_role_needs_the_lecturer_role() {
        assertThatThrownBy(() -> service.checkRoleRules(EnumSet.of(RoleCode.SUPERVISOR), Set.of()))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.BUSINESS_RULE));
        assertThatNoException().isThrownBy(() -> service.checkRoleRules(EnumSet.of(RoleCode.LECTURER, RoleCode.SUPERVISOR), Set.of()));
    }

    @Test
    void only_a_super_admin_can_grant_admin_roles() {
        assertThatThrownBy(() -> service.checkRoleRules(EnumSet.of(RoleCode.ADMIN), Set.of(RoleCode.LECTURER)))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.FORBIDDEN));
        Fixtures.signIn(UUID.randomUUID(), RoleCode.SUPER_ADMIN, RoleCode.ADMIN);
        assertThatNoException().isThrownBy(() -> service.checkRoleRules(EnumSet.of(RoleCode.ADMIN), Set.of(RoleCode.LECTURER)));
    }
}
