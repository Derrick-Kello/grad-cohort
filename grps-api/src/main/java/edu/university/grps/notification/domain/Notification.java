package edu.university.grps.notification.domain;

import edu.university.grps.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/** In-app notification; it is the system of record, and email is a delivery attempt on top of it. */
@Getter
@Setter
@Entity
@Table(name = "notification")
public class Notification extends BaseEntity {

    public enum EmailStatus { PENDING, SENT, FAILED, DIGEST, SKIPPED }

    @Column(name = "recipient_id", nullable = false)
    private UUID recipientId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationCode code;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String body;

    private String link;

    @Column(nullable = false)
    private boolean urgent;

    @Column(name = "read_at")
    private Instant readAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "email_status", nullable = false)
    private EmailStatus emailStatus = EmailStatus.PENDING;

    @Column(name = "email_attempts", nullable = false)
    private int emailAttempts;

    @Column(name = "email_last_error")
    private String emailLastError;
}
