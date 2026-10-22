package edu.university.grps.student.domain;

import edu.university.grps.common.BaseEntity;
import edu.university.grps.programme.domain.Cohort;
import edu.university.grps.programme.domain.Programme;
import edu.university.grps.user.domain.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "student")
public class Student extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "programme_id")
    private Programme programme;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cohort_id")
    private Cohort cohort;

    @Column(name = "enrolled_on", nullable = false)
    private LocalDate enrolledOn;

    @Column(name = "checkpoint_due_on", nullable = false)
    private LocalDate checkpointDueOn;

    @Column(name = "programme_end_on", nullable = false)
    private LocalDate programmeEndOn;

    @Column(name = "current_semester", nullable = false)
    private int currentSemester = 1;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StudentStatus status = StudentStatus.ACTIVE;

    @Column(name = "status_reason")
    private String statusReason;

    @Column(name = "flagged_at")
    private Instant flaggedAt;

    @Column(name = "expelled_at")
    private Instant expelledAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    /** BR-20: expelled, completed and withdrawn students cannot be scheduled or submit work. */
    public boolean isClosed() {
        return status == StudentStatus.EXPELLED || status == StudentStatus.COMPLETED || status == StudentStatus.WITHDRAWN;
    }
}
