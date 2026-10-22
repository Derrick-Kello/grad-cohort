package edu.university.grps.notification.service;

import edu.university.grps.common.ApiException;
import edu.university.grps.common.GrpsProperties;
import edu.university.grps.notification.domain.Notification;
import edu.university.grps.notification.domain.NotificationCode;
import edu.university.grps.notification.repository.NotificationRepository;
import edu.university.grps.user.domain.AppUser;
import edu.university.grps.user.repository.AppUserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Every event that asks someone to act produces an in-app notification and an email (section 13).
 * Rows are written in the caller's transaction; the live push and email go out after commit.
 */
@Service
public class NotificationService {

    public static final int MAX_EMAIL_ATTEMPTS = 3;
    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository repository;
    private final AppUserRepository users;
    private final SseHub sseHub;
    private final EmailSender emailSender;
    private final GrpsProperties props;
    private final Clock clock;
    private final TransactionTemplate tx;
    private final MailDispatcher dispatcher;

    public NotificationService(NotificationRepository repository, AppUserRepository users, SseHub sseHub,
                               EmailSender emailSender, GrpsProperties props, Clock clock, TransactionTemplate tx,
                               @org.springframework.context.annotation.Lazy MailDispatcher dispatcher) {
        this.repository = repository;
        this.users = users;
        this.sseHub = sseHub;
        this.emailSender = emailSender;
        this.props = props;
        this.clock = clock;
        this.tx = tx;
        this.dispatcher = dispatcher;
    }

    public record NotificationDto(UUID id, String code, String codeLabel, String title, String body, String link,
                                  Instant createdAt, Instant readAt) {
        static NotificationDto of(Notification n) {
            return new NotificationDto(n.getId(), n.getCode().display(), n.getCode().label(), n.getTitle(),
                    n.getBody(), n.getLink(), n.getCreatedAt(), n.getReadAt());
        }
    }

    @Transactional
    public void notify(Collection<UUID> recipientIds, NotificationCode code, String title, String body, String link) {
        notify(recipientIds, code, title, body, link, code.urgent());
    }

    @Transactional
    public void notify(Collection<UUID> recipientIds, NotificationCode code, String title, String body, String link,
                       boolean urgent) {
        LinkedHashSet<UUID> unique = recipientIds.stream().filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (unique.isEmpty()) return;
        Map<UUID, AppUser> recipients = users.findAllById(unique).stream()
                .collect(Collectors.toMap(AppUser::getId, Function.identity()));
        List<Notification> saved = new ArrayList<>();
        for (UUID id : unique) {
            AppUser user = recipients.get(id);
            if (user == null || !user.isActive()) continue;
            Notification n = new Notification();
            n.setRecipientId(id);
            n.setCode(code);
            n.setTitle(title);
            n.setBody(body);
            n.setLink(link);
            n.setUrgent(urgent);
            n.setEmailStatus(user.isEmailDigest() && !urgent ? Notification.EmailStatus.DIGEST
                    : Notification.EmailStatus.PENDING);
            saved.add(repository.save(n));
        }
        afterCommit(() -> {
            for (Notification n : saved) {
                sseHub.push(n.getRecipientId(), NotificationDto.of(n), repository.countByRecipientIdAndReadAtIsNull(n.getRecipientId()));
                if (n.getEmailStatus() == Notification.EmailStatus.PENDING) dispatcher.sendAsync(n.getId());
            }
        });
    }

    @Transactional(readOnly = true)
    public Page<NotificationDto> inbox(UUID userId, boolean unreadOnly, NotificationCode code, Pageable pageable) {
        return repository.inbox(userId, unreadOnly, code, pageable).map(NotificationDto::of);
    }

    @Transactional(readOnly = true)
    public long unreadCount(UUID userId) {
        return repository.countByRecipientIdAndReadAtIsNull(userId);
    }

    @Transactional
    public void markRead(UUID userId, UUID notificationId) {
        Notification n = repository.findById(notificationId)
                .filter(x -> x.getRecipientId().equals(userId))
                .orElseThrow(() -> ApiException.notFound("Notification"));
        if (n.getReadAt() == null) n.setReadAt(clock.instant());
    }

    @Transactional
    public int markAllRead(UUID userId) {
        return repository.markAllRead(userId, clock.instant());
    }

    /** Sends one notification's email; called on the mail executor and by the retry job. */
    void deliver(UUID notificationId) {
        tx.executeWithoutResult(status -> {
            Notification n = repository.findById(notificationId).orElse(null);
            if (n == null || n.getEmailStatus() == Notification.EmailStatus.SENT
                    || n.getEmailAttempts() >= MAX_EMAIL_ATTEMPTS) return;
            AppUser user = users.findById(n.getRecipientId()).orElse(null);
            if (user == null) {
                n.setEmailStatus(Notification.EmailStatus.SKIPPED);
                return;
            }
            String url = n.getLink() == null ? null : props.frontendUrl() + n.getLink();
            n.setEmailAttempts(n.getEmailAttempts() + 1);
            try {
                emailSender.send(user.getEmail(), n.getTitle(), EmailTemplates.html(n.getTitle(), n.getBody(), url, "Open in GRPS"),
                        EmailTemplates.text(n.getTitle(), n.getBody(), url));
                n.setEmailStatus(Notification.EmailStatus.SENT);
                n.setEmailLastError(null);
            } catch (RuntimeException e) {
                log.warn("Email for notification {} failed (attempt {}): {}", n.getId(), n.getEmailAttempts(), e.getMessage());
                n.setEmailStatus(Notification.EmailStatus.FAILED);
                n.setEmailLastError(e.getMessage());
            }
        });
    }

    /** EmailRetryJob: failed or never-sent emails, up to 3 attempts each (FR-NTF-02). */
    public int retryFailedEmails() {
        List<UUID> ids = repository.findEmailRetries(
                List.of(Notification.EmailStatus.FAILED, Notification.EmailStatus.PENDING), MAX_EMAIL_ATTEMPTS);
        Instant cutoff = clock.instant().minusSeconds(60);
        int sent = 0;
        for (UUID id : ids) {
            Notification n = repository.findById(id).orElse(null);
            if (n == null || n.getUpdatedAt().isAfter(cutoff)) continue;
            deliver(id);
            sent++;
        }
        return sent;
    }

    /** Daily digest for users who chose it (FR-NTF-04). */
    public int sendDigests() {
        List<Notification> pending = tx.execute(s ->
                repository.findByEmailStatusOrderByRecipientIdAscCreatedAtAsc(Notification.EmailStatus.DIGEST));
        if (pending == null || pending.isEmpty()) return 0;
        Map<UUID, List<Notification>> byUser = pending.stream()
                .collect(Collectors.groupingBy(Notification::getRecipientId));
        byUser.forEach((userId, items) -> tx.executeWithoutResult(s -> {
            AppUser user = users.findById(userId).orElse(null);
            if (user == null) return;
            String body = items.stream().map(n -> "- " + n.getTitle() + ": " + n.getBody())
                    .collect(Collectors.joining("\n"));
            String title = "Your daily GRPS summary (" + items.size() + " updates)";
            try {
                emailSender.send(user.getEmail(), title, EmailTemplates.html(title, body, props.frontendUrl() + "/notifications",
                        "Open notifications"), EmailTemplates.text(title, body, props.frontendUrl() + "/notifications"));
                for (Notification n : items) {
                    Notification managed = repository.findById(n.getId()).orElseThrow();
                    managed.setEmailStatus(Notification.EmailStatus.SENT);
                }
            } catch (RuntimeException e) {
                log.warn("Digest email to {} failed: {}", user.getEmail(), e.getMessage());
            }
        }));
        return byUser.size();
    }

    /** Direct email for account links (activation, password reset); not shown in the inbox. */
    public void sendAccountEmail(String to, String subject, String body, String actionUrl, String actionLabel) {
        Runnable send = () -> {
            try {
                emailSender.send(to, subject, EmailTemplates.html(subject, body, actionUrl, actionLabel),
                        EmailTemplates.text(subject, body, actionUrl));
            } catch (RuntimeException e) {
                log.warn("Account email to {} failed: {}", to, e.getMessage());
            }
        };
        afterCommit(() -> dispatcher.runAsync(send));
    }

    private static void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    /** Separate bean so {@code @Async} goes through the proxy. */
    @Service
    public static class MailDispatcher {

        private final NotificationService service;

        public MailDispatcher(NotificationService service) {
            this.service = service;
        }

        @Async("mailExecutor")
        public void sendAsync(UUID notificationId) {
            service.deliver(notificationId);
        }

        @Async("mailExecutor")
        public void runAsync(Runnable runnable) {
            runnable.run();
        }
    }
}
