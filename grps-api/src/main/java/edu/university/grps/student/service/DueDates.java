package edu.university.grps.student.service;

import edu.university.grps.programme.domain.Programme;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Key dates per student, computed from the enrolment date and shifted by recorded extensions (section 6).
 * Checkpoint = enrolment + checkpoint months; programme end = enrolment + duration (end of semester 4).
 */
public record DueDates(LocalDate checkpointDue, LocalDate programmeEnd, LocalDate firstMilestoneOpens) {

    public static DueDates compute(Programme programme, LocalDate enrolledOn, int extensionMonths) {
        LocalDate checkpoint = enrolledOn.plusMonths(programme.getCheckpointMonths() + (long) extensionMonths);
        LocalDate end = enrolledOn.plusMonths(programme.getDurationMonths() + (long) extensionMonths);
        LocalDate opens = enrolledOn.plusMonths((long) programme.monthsPerSemester() * (programme.getFirstMilestoneSemester() - 1));
        return new DueDates(checkpoint, end, opens);
    }

    /** Programme semester (1..n) on a date; stays at the last semester while an extension runs. */
    public static int semesterOn(Programme programme, LocalDate enrolledOn, LocalDate date) {
        if (date.isBefore(enrolledOn)) return 1;
        long months = ChronoUnit.MONTHS.between(enrolledOn, date);
        int semester = (int) (months / programme.monthsPerSemester()) + 1;
        return Math.max(1, Math.min(programme.getSemesters(), semester));
    }
}
