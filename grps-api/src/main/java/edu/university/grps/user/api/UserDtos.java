package edu.university.grps.user.api;

import edu.university.grps.user.domain.AppUser;
import edu.university.grps.user.domain.RoleCode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class UserDtos {

    private UserDtos() {}

    public record UserDto(UUID id, String email, String number, String firstName, String lastName, String name,
                          String phone, List<String> roles, boolean active, boolean activated, Instant lockedUntil,
                          Instant lastLoginAt, Instant createdAt, Long version) {
        public static UserDto of(AppUser u) {
            return new UserDto(u.getId(), u.getEmail(), u.getStaffOrStudentNo(), u.getFirstName(), u.getLastName(),
                    u.getFullName(), u.getPhone(), u.roleCodes().stream().map(RoleCode::name).sorted().toList(), u.isActive(),
                    u.getPasswordHash() != null, u.getLockedUntil(), u.getLastLoginAt(), u.getCreatedAt(), u.getVersion());
        }
    }
}
