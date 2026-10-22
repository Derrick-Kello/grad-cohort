package edu.university.grps.notification.repository;

import edu.university.grps.notification.domain.Notification;
import edu.university.grps.notification.domain.NotificationCode;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    @Query("""
            select n from Notification n
            where n.recipientId = :recipient
              and (:unreadOnly = false or n.readAt is null)
              and (:code is null or n.code = :code)
            order by n.createdAt desc
            """)
    Page<Notification> inbox(@Param("recipient") UUID recipient, @Param("unreadOnly") boolean unreadOnly,
                             @Param("code") NotificationCode code, Pageable pageable);

    long countByRecipientIdAndReadAtIsNull(UUID recipientId);

    @Modifying
    @Query("update Notification n set n.readAt = :now where n.recipientId = :recipient and n.readAt is null")
    int markAllRead(@Param("recipient") UUID recipient, @Param("now") Instant now);

    @Query("select n.id from Notification n where n.emailStatus in :statuses and n.emailAttempts < :maxAttempts")
    List<UUID> findEmailRetries(@Param("statuses") Collection<Notification.EmailStatus> statuses,
                                @Param("maxAttempts") int maxAttempts);

    List<Notification> findByEmailStatusOrderByRecipientIdAscCreatedAtAsc(Notification.EmailStatus status);

    boolean existsByRecipientIdAndCodeAndLinkAndCreatedAtAfter(UUID recipientId, NotificationCode code, String link,
                                                               Instant after);
}
