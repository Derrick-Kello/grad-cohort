package edu.university.grps.user.repository;

import edu.university.grps.user.domain.Role;
import edu.university.grps.user.domain.RoleCode;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<Role, UUID> {

    Optional<Role> findByCode(RoleCode code);
}
