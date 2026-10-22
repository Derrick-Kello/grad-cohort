package edu.university.grps.attendance.domain;

import edu.university.grps.common.BaseEntity;
import edu.university.grps.student.domain.Student;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/** Exam eligibility per student per course, recalculated on every attendance save (FR-ATT-05/06). */
@Getter
@Setter
@Entity
@Table(name = "eligibility")
public class Eligibility extends BaseEntity {

    public enum Status { ELIGIBLE, NOT_ELIGIBLE, EXCEPTION }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id")
    private Course course;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id")
    private Student student;

    @Column(name = "sessions_held", nullable = false)
    private int sessionsHeld;

    @Column(name = "sessions_counted", nullable = false)
    private int sessionsCounted;

    @Column(name = "sessions_attended", nullable = false)
    private int sessionsAttended;

    @Column(name = "attendance_pct")
    private BigDecimal attendancePct;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.ELIGIBLE;

    @Column(name = "exception_reason")
    private String exceptionReason;

    @Column(name = "warned_below_warning", nullable = false)
    private boolean warnedBelowWarning;

    @Column(name = "warned_below_threshold", nullable = false)
    private boolean warnedBelowThreshold;
}
