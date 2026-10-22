package edu.university.grps.attendance.service;

import edu.university.grps.attendance.domain.AttendanceRecord;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;

/**
 * FR-ATT-05 / BR-16: % = (PRESENT + LATE) / (sessions held - EXCUSED) x 100, rounded to one decimal.
 * At or above the threshold is ELIGIBLE; below is NOT_ELIGIBLE.
 */
public final class EligibilityCalculator {

    private EligibilityCalculator() {}

    public record Result(int held, int counted, int attended, BigDecimal pct, boolean eligible, int canMiss) {}

    public static Result compute(Collection<AttendanceRecord.Status> statuses, BigDecimal thresholdPct) {
        int held = statuses.size();
        int excused = 0;
        int attended = 0;
        for (AttendanceRecord.Status s : statuses) {
            if (s == AttendanceRecord.Status.EXCUSED) excused++;
            if (s == AttendanceRecord.Status.PRESENT || s == AttendanceRecord.Status.LATE) attended++;
        }
        int counted = held - excused;
        if (counted <= 0) return new Result(held, counted, attended, null, true, canMiss(attended, 0, thresholdPct));
        BigDecimal pct = BigDecimal.valueOf(attended * 100L).divide(BigDecimal.valueOf(counted), 1, RoundingMode.HALF_UP);
        boolean eligible = pct.compareTo(thresholdPct) >= 0;
        return new Result(held, counted, attended, pct, eligible, canMiss(attended, counted, thresholdPct));
    }

    /** How many further sessions the student can miss and still stay at or above the threshold. */
    static int canMiss(int attended, int counted, BigDecimal thresholdPct) {
        if (thresholdPct.signum() <= 0) return Integer.MAX_VALUE;
        int k = 0;
        while (k < 1000) {
            int total = counted + k + 1;
            BigDecimal pct = BigDecimal.valueOf(attended * 100L).divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_UP);
            if (pct.compareTo(thresholdPct) < 0) break;
            k++;
        }
        return k;
    }
}
