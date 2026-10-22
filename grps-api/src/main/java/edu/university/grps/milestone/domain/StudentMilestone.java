package edu.university.grps.milestone.domain;

import edu.university.grps.common.BaseEntity;
import edu.university.grps.programme.domain.MilestoneTemplate;
import edu.university.grps.student.domain.Student;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * One required presentation for a student. Title and flags are copied from the template at enrolment,
 * so later template edits do not change milestones already generated (FR-PRG-01).
 */
@Getter
@Setter
@Entity
@Table(name = "student_milestone")
public class StudentMilestone extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id")
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "template_id")
    private MilestoneTemplate template;

    @Column(name = "sequence_no", nullable = false)
    private int sequenceNo;

    @Column(nullable = false)
    private String title;

    @Column(name = "is_final_pre_defense", nullable = false)
    private boolean finalPreDefense;

    @Column(name = "evidence_required", nullable = false)
    private boolean evidenceRequired;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MilestoneStatus status = MilestoneStatus.LOCKED;

    @Column(name = "available_at")
    private Instant availableAt;

    @Column(name = "approved_by")
    private UUID approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    public String label() {
        return "Presentation " + sequenceNo;
    }
}
