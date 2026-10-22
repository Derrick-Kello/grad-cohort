package edu.university.grps.attendance.service;

import static edu.university.grps.attendance.domain.AttendanceRecord.Status.ABSENT;
import static edu.university.grps.attendance.domain.AttendanceRecord.Status.EXCUSED;
import static edu.university.grps.attendance.domain.AttendanceRecord.Status.LATE;
import static edu.university.grps.attendance.domain.AttendanceRecord.Status.PRESENT;
import static org.assertj.core.api.Assertions.assertThat;

import edu.university.grps.attendance.domain.AttendanceRecord;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class EligibilityCalculatorTest {

    private static final BigDecimal SEVENTY = BigDecimal.valueOf(70);

    private static List<AttendanceRecord.Status> sessions(int present, int late, int absent, int excused) {
        List<AttendanceRecord.Status> out = new ArrayList<>();
        out.addAll(Collections.nCopies(present, PRESENT));
        out.addAll(Collections.nCopies(late, LATE));
        out.addAll(Collections.nCopies(absent, ABSENT));
        out.addAll(Collections.nCopies(excused, EXCUSED));
        return out;
    }

    @Test
    void BR_16_six_of_ten_is_60_percent_and_not_eligible() {
        var r = EligibilityCalculator.compute(sessions(6, 0, 4, 0), SEVENTY);
        assertThat(r.pct()).isEqualByComparingTo("60.0");
        assertThat(r.eligible()).isFalse();
    }

    @Test
    void BR_16_exactly_70_percent_is_eligible() {
        var r = EligibilityCalculator.compute(sessions(7, 0, 3, 0), SEVENTY);
        assertThat(r.pct()).isEqualByComparingTo("70.0");
        assertThat(r.eligible()).isTrue();
    }

    @Test
    void late_counts_as_attended_and_excused_is_excluded() {
        var r = EligibilityCalculator.compute(sessions(5, 2, 2, 1), SEVENTY);
        assertThat(r.counted()).isEqualTo(9);
        assertThat(r.attended()).isEqualTo(7);
        assertThat(r.pct()).isEqualByComparingTo("77.8");
    }

    @Test
    void reports_how_many_more_sessions_can_be_missed() {
        var r = EligibilityCalculator.compute(sessions(9, 0, 1, 0), SEVENTY);
        // 9/11 = 81.8%, 9/12 = 75%, 9/13 = 69.2% -> two more absences allowed.
        assertThat(r.canMiss()).isEqualTo(2);
    }

    @Test
    void no_sessions_yet_is_eligible_with_no_percentage() {
        var r = EligibilityCalculator.compute(List.of(), SEVENTY);
        assertThat(r.pct()).isNull();
        assertThat(r.eligible()).isTrue();
    }
}
