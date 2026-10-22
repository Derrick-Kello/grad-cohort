package edu.university.grps.thesis.service;

import edu.university.grps.comment.domain.ReviewComment;
import edu.university.grps.comment.service.CommentService;
import edu.university.grps.thesis.repository.ThesisRepositories.ThesisSubmissionRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Supervisors comment on each thesis version (FR-THS-13). */
@Component
public class ThesisCommentTarget implements CommentService.TargetResolver {

    private final ThesisSubmissionRepository theses;

    public ThesisCommentTarget(ThesisSubmissionRepository theses) {
        this.theses = theses;
    }

    @Override
    public ReviewComment.TargetType type() {
        return ReviewComment.TargetType.THESIS;
    }

    @Override
    public Optional<CommentService.Target> resolve(UUID targetId) {
        return theses.findWithStudentById(targetId)
                .map(t -> new CommentService.Target(t.getStudent(), "thesis version " + t.getVersionNo(), "/student/thesis"));
    }
}
