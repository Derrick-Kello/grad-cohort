package edu.university.grps.user.service;

import edu.university.grps.user.domain.AppUser;
import edu.university.grps.user.domain.RoleCode;
import edu.university.grps.user.repository.AppUserRepository;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Name lookups and role-based recipient lists for other modules. */
@Service
@Transactional(readOnly = true)
public class UserDirectory {

    private final AppUserRepository users;

    public UserDirectory(AppUserRepository users) {
        this.users = users;
    }

    public Map<UUID, String> names(Collection<UUID> ids) {
        // A mutable map so callers can look up a null id without an exception.
        Map<UUID, String> out = new HashMap<>();
        List<UUID> wanted = ids.stream().filter(Objects::nonNull).distinct().toList();
        if (wanted.isEmpty()) return out;
        users.findAllById(wanted).forEach(u -> out.put(u.getId(), u.getFullName()));
        return out;
    }

    public String name(UUID id) {
        if (id == null) return null;
        return users.findById(id).map(AppUser::getFullName).orElse(null);
    }

    public List<UUID> activeIdsWithRole(RoleCode role) {
        return users.findActiveByRole(role).stream().map(AppUser::getId).toList();
    }

    public List<AppUser> activeWithRole(RoleCode role) {
        return users.findActiveByRole(role);
    }
}
