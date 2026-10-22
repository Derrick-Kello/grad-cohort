package edu.university.grps.programme.domain;

import edu.university.grps.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "academic_semester")
public class AcademicSemester extends BaseEntity {

    @Column(name = "academic_year", nullable = false)
    private String academicYear;

    @Column(nullable = false)
    private int number;

    @Column(name = "starts_on", nullable = false)
    private LocalDate startsOn;

    @Column(name = "ends_on", nullable = false)
    private LocalDate endsOn;

    public String label() {
        return academicYear + " Semester " + number;
    }

    public boolean contains(LocalDate date) {
        return !date.isBefore(startsOn) && !date.isAfter(endsOn);
    }
}
