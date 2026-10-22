package edu.university.grps.attendance.domain;

import edu.university.grps.common.BaseEntity;
import edu.university.grps.programme.domain.AcademicSemester;
import edu.university.grps.student.domain.Student;
import edu.university.grps.user.domain.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;

/** A taught course in one semester; it can have more than one lecturer (FR-ATT-01). */
@Getter
@Setter
@Entity
@Table(name = "course")
public class Course extends BaseEntity {

    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String title;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "semester_id")
    private AcademicSemester semester;

    @Column(name = "credit_hours", nullable = false)
    private int creditHours = 3;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "course_lecturer", joinColumns = @JoinColumn(name = "course_id"),
            inverseJoinColumns = @JoinColumn(name = "lecturer_user_id"))
    private Set<AppUser> lecturers = new HashSet<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "course_enrolment", joinColumns = @JoinColumn(name = "course_id"),
            inverseJoinColumns = @JoinColumn(name = "student_id"))
    private Set<Student> students = new HashSet<>();
}
