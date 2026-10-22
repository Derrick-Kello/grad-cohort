package edu.university.grps.student.domain;

import edu.university.grps.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/** An approved suspension or extension; shifts both due dates (FR-PRG-06). */
@Getter
@Setter
@Entity
@Table(name = "extension")
public class Extension extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id")
    private Student student;

    @Column(nullable = false)
    private int months;

    @Column(nullable = false)
    private String reason;

    @Column(name = "approved_by", nullable = false)
    private String approvedBy;

    @Column(name = "approved_on", nullable = false)
    private LocalDate approvedOn;
}
