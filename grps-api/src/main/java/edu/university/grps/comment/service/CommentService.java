package edu.university.grps.comment.service;

import edu.university.grps.audit.AuditService;
import edu.university.grps.comment.domain.ReviewComment;
import edu.university.grps.comment.repository.ReviewCommentRepository;
import edu.university.grps.common.ApiException;
import edu.university.grps.notification.domain.NotificationCode;
import edu.university.grps.notification.service.NotificationService;
import edu.university.grps.security.CurrentUser;
import edu.university.grps.student.domain.Student;
import edu.university.grps.student.service.StudentAccess;
import edu.university.grps.user.service.UserDirectory;
import java.time.Instant;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Supervisor and admin comments on evidence, milestones and thesis versions (FR-VER-04, FR-THS-13). */
@Service
@Transactional
public class CommentService {

    /** Finds the student and a readable label for a comment target; implemented by the owning module. */
    public interface TargetResolver {
        ReviewComment.TargetType type();

        Optional<Target> resolve(UUID targetId);
    }

    public record Target(Student student, String label, String link) {}

    public record CommentDto(UUID id, String targetType, UUID targetId, UUID authorId, String authorName, String body,
                             Instant createdAt, String targetLabel, String studentName, UUID studentId) {}

    private final ReviewCommentRepository repository;
    private final Map<ReviewComment.TargetType, TargetResolver> resolvers = new EnumMap<>(ReviewComment.TargetType.class);
    private final StudentAccess access;
    private final UserDirectory directory;
    private final NotificationService notifications;
    private final AuditService audit;

    public CommentService(ReviewCommentRepository repository, List<TargetResolver> resolvers, StudentAccess access,
                          UserDirectory directory, NotificationService notifications, AuditService audit) {
        this.repository = repository;
        resolvers.forEach(r -> this.resolvers.put(r.type(), r));
        this.access = access;
        this.directory = directory;
        this.notifications = notifications;
        this.audit = audit;
    }

    /** Admins and the student's supervisors can comment; the student is notified (N-20). */
    public CommentDto add(ReviewComment.TargetType type, UUID targetId, String body) {
        if (body == null || body.isBlank()) throw ApiException.validation("Write a comment first.");
        Target target = resolve(type, targetId);
        Student student = target.student();
        boolean supervisor = access.isSupervisorOf(student.getId());
        if (!CurrentUser.isAdmin() && !supervisor) throw ApiException.notFound("Comment target");
        ReviewComment c = new ReviewComment();
        c.setTargetType(type);
        c.setTargetId(targetId);
        c.setAuthorId(CurrentUser.id());
        c.setBody(body.trim());
        c = repository.save(c);
        audit.record("COMMENT_ADDED", "ReviewComment", c.getId(), student.getId(), null,
                Map.of("target", type.name() + ":" + targetId, "length", c.getBody().length()));
        String author = directory.name(c.getAuthorId());
        notifications.notify(List.of(student.getUser().getId()), NotificationCode.N20, "New comment from " + author,
                author + " commented on " + target.label() + ": \"" + abbreviate(c.getBody()) + "\"", target.link());
        return toDto(c, author, target);
    }

    /** Stores a comment written as part of another action (for example returning a milestone); no extra notification. */
    public void record(ReviewComment.TargetType type, UUID targetId, String body) {
        ReviewComment c = new ReviewComment();
        c.setTargetType(type);
        c.setTargetId(targetId);
        c.setAuthorId(CurrentUser.id());
        c.setBody(body);
        repository.save(c);
    }

    @Transactional(readOnly = true)
    public List<CommentDto> list(ReviewComment.TargetType type, UUID targetId) {
        Target target = resolve(type, targetId);
        if (!access.canView(target.student())) throw ApiException.notFound("Comment target");
        List<ReviewComment> comments = repository.findByTargetTypeAndTargetIdOrderByCreatedAtAsc(type, targetId);
        Map<UUID, String> names = directory.names(comments.stream().map(ReviewComment::getAuthorId).toList());
        return comments.stream().map(c -> toDto(c, names.get(c.getAuthorId()), target)).toList();
    }

    /** Comments on many targets at once, without access checks; callers must have checked the student. */
    @Transactional(readOnly = true)
    public List<CommentDto> listForTargets(ReviewComment.TargetType type, Collection<UUID> targetIds) {
        if (targetIds.isEmpty()) return List.of();
        List<ReviewComment> comments = repository.findByTargetTypeAndTargetIdInOrderByCreatedAtAsc(type, targetIds);
        Map<UUID, String> names = directory.names(comments.stream().map(ReviewComment::getAuthorId).toList());
        return comments.stream().map(c -> new CommentDto(c.getId(), c.getTargetType().name(), c.getTargetId(),
                c.getAuthorId(), names.get(c.getAuthorId()), c.getBody(), c.getCreatedAt(), null, null, null)).toList();
    }

    @Transactional(readOnly = true)
    public Page<CommentDto> mine(Pageable pageable) {
        UUID me = CurrentUser.id();
        String myName = directory.name(me);
        return repository.findByAuthorIdOrderByCreatedAtDesc(me, pageable).map(c -> {
            Target target = resolvers.get(c.getTargetType()).resolve(c.getTargetId()).orElse(null);
            return toDto(c, myName, target);
        });
    }

    private Target resolve(ReviewComment.TargetType type, UUID targetId) {
        TargetResolver resolver = resolvers.get(type);
        if (resolver == null) throw ApiException.validation("Unsupported comment target.");
        return resolver.resolve(targetId).orElseThrow(() -> ApiException.notFound("Comment target"));
    }

    private static CommentDto toDto(ReviewComment c, String authorName, Target target) {
        return new CommentDto(c.getId(), c.getTargetType().name(), c.getTargetId(), c.getAuthorId(), authorName, c.getBody(),
                c.getCreatedAt(), target == null ? null : target.label(),
                target == null ? null : target.student().getUser().getFullName(),
                target == null ? null : target.student().getId());
    }

    private static String abbreviate(String s) {
        return s.length() <= 140 ? s : s.substring(0, 137) + "...";
    }
}
