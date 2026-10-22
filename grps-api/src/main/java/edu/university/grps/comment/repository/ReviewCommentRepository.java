package edu.university.grps.comment.repository;

import edu.university.grps.comment.domain.ReviewComment;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewCommentRepository extends JpaRepository<ReviewComment, UUID> {

    List<ReviewComment> findByTargetTypeAndTargetIdOrderByCreatedAtAsc(ReviewComment.TargetType type, UUID targetId);

    List<ReviewComment> findByTargetTypeAndTargetIdInOrderByCreatedAtAsc(ReviewComment.TargetType type, Collection<UUID> targetIds);

    Page<ReviewComment> findByAuthorIdOrderByCreatedAtDesc(UUID authorId, Pageable pageable);
}
