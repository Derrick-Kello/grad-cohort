package edu.university.grps.student.service;

import static org.assertj.core.api.Assertions.assertThat;

import edu.university.grps.programme.domain.Programme;
import edu.university.grps.support.Fixtures;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class DueDatesTest {

    private final Programme masters = Fixtures.masters();

    @Test
    void BR_12_both_programmes_run_24_months_over_4_semesters() {
        DueDates due = DueDates.compute(masters, LocalDate.of(2025, 9, 1), 0);
        assertThat(due.programmeEnd()).isEqualTo(LocalDate.of(2027, 9, 1));
        assertThat(DueDates.semesterOn(masters, LocalDate.of(2025, 9, 1), LocalDate.of(2025, 9, 2))).isEqualTo(1);
        assertThat(DueDates.semesterOn(masters, LocalDate.of(2025, 9, 1), LocalDate.of(2026, 3, 1))).isEqualTo(2);
        assertThat(DueDates.semesterOn(masters, LocalDate.of(2025, 9, 1), LocalDate.of(2027, 8, 31))).isEqualTo(4);
        assertThat(DueDates.semesterOn(masters, LocalDate.of(2025, 9, 1), LocalDate.of(2028, 1, 1))).isEqualTo(4);
    }

    @Test
    void BR_13_checkpoint_is_enrolment_plus_18_months() {
        DueDates due = DueDates.compute(masters, LocalDate.of(2025, 9, 1), 0);
        assertThat(due.checkpointDue()).isEqualTo(LocalDate.of(2027, 3, 1));
    }

    @Test
    void first_milestone_opens_at_the_start_of_semester_2() {
        DueDates due = DueDates.compute(masters, LocalDate.of(2025, 9, 1), 0);
        assertThat(due.firstMilestoneOpens()).isEqualTo(LocalDate.of(2026, 3, 1));
    }

    @Test
    void FR_PRG_06_extensions_shift_both_due_dates() {
        DueDates due = DueDates.compute(masters, LocalDate.of(2025, 9, 1), 3);
        assertThat(due.checkpointDue()).isEqualTo(LocalDate.of(2027, 6, 1));
        assertThat(due.programmeEnd()).isEqualTo(LocalDate.of(2027, 12, 1));
    }
}
