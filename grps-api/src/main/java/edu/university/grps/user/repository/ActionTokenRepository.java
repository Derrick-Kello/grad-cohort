package edu.university.grps.user.repository;

import edu.university.grps.user.domain.ActionToken;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ActionTokenRepository extends JpaRepository<ActionToken, UUID> {

    Optional<ActionToken> findByTokenHashAndPurpose(String tokenHash, ActionToken.Purpose purpose);

    @Modifying
    @Query("delete from ActionToken t where t.expiresAt < :now or t.usedAt is not null")
    int deleteExpired(@Param("now") Instant now);
}
