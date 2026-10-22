package edu.university.grps.milestone.service;

import edu.university.grps.comment.domain.ReviewComment;
import edu.university.grps.comment.service.CommentService;
import edu.university.grps.evidence.repository.EvidenceFileRepository;
import edu.university.grps.milestone.repository.MilestoneRepositories.StudentMilestoneRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Comment targets owned by the milestone and evidence modules. */
public final class CommentTargets {

    private CommentTargets() {}

    @Component
    public static class MilestoneTarget implements CommentService.TargetResolver {
        private final StudentMilestoneRepository milestones;

        public MilestoneTarget(StudentMilestoneRepository milestones) {
            this.milestones = milestones;
        }

        @Override
        public ReviewComment.TargetType type() {
            return ReviewComment.TargetType.MILESTONE;
        }

        @Override
        public Optional<CommentService.Target> resolve(UUID targetId) {
            return milestones.findWithStudentById(targetId).map(m -> new CommentService.Target(m.getStudent(),
                    m.label() + " (" + m.getTitle() + ")", "/student/presentations"));
        }
    }

    @Component
    public static class EvidenceTarget implements CommentService.TargetResolver {
        private final EvidenceFileRepository evidence;

        public EvidenceTarget(EvidenceFileRepository evidence) {
            this.evidence = evidence;
        }

        @Override
        public ReviewComment.TargetType type() {
            return ReviewComment.TargetType.EVIDENCE;
        }

        @Override
        public Optional<CommentService.Target> resolve(UUID targetId) {
            return evidence.findWithMilestoneById(targetId).map(e -> new CommentService.Target(e.getMilestone().getStudent(),
                    e.getFileName() + " (" + e.getMilestone().label() + ")", "/student/evidence"));
        }
    }
}
