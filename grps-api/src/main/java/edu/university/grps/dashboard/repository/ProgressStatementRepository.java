package edu.university.grps.dashboard.repository;

import edu.university.grps.dashboard.domain.ProgressStatement;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProgressStatementRepository extends JpaRepository<ProgressStatement, UUID> {

    Optional<ProgressStatement> findByVerificationCode(String code);
}
