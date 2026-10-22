package edu.university.grps.user.domain;

import edu.university.grps.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;

/** A person who can sign in. Accounts are soft-deactivated and never deleted (FR-USR-01). */
@Getter
@Setter
@Entity
@Table(name = "app_user")
public class AppUser extends BaseEntity {

    @Column(nullable = false)
    private String email;

    @Column(name = "staff_or_student_no", nullable = false)
    private String staffOrStudentNo;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    private String phone;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "failed_logins", nullable = false)
    private int failedLogins;

    @Column(name = "first_failed_at")
    private Instant firstFailedAt;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "email_digest", nullable = false)
    private boolean emailDigest;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "user_role",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<Role> roles = new HashSet<>();

    public String getFullName() {
        return firstName + " " + lastName;
    }

    public Set<RoleCode> roleCodes() {
        Set<RoleCode> codes = EnumSet.noneOf(RoleCode.class);
        roles.forEach(r -> codes.add(r.getCode()));
        return codes;
    }

    public boolean hasRole(RoleCode code) {
        return roles.stream().anyMatch(r -> r.getCode() == code);
    }

    public boolean isLocked(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }
}
