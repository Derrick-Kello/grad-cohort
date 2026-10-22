package edu.university.grps.programme.domain;

import edu.university.grps.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Masters or PhD programme; every value is editable by the admin (section 6). */
@Getter
@Setter
@Entity
@Table(name = "programme")
public class Programme extends BaseEntity {

    public enum Level { MASTERS, PHD }

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Level level;

    @Column(name = "duration_months", nullable = false)
    private int durationMonths;

    @Column(nullable = false)
    private int semesters;

    @Column(name = "required_milestones", nullable = false)
    private int requiredMilestones;

    @Column(name = "checkpoint_months", nullable = false)
    private int checkpointMonths;

    @Column(name = "checkpoint_milestones", nullable = false)
    private int checkpointMilestones;

    @Column(name = "evidence_required", nullable = false)
    private boolean evidenceRequired;

    @Column(name = "first_milestone_semester", nullable = false)
    private int firstMilestoneSemester = 2;

    @Column(name = "required_publications", nullable = false)
    private int requiredPublications;

    public int monthsPerSemester() {
        return Math.max(1, durationMonths / Math.max(1, semesters));
    }
}
