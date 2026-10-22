package edu.university.grps.common;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/**
 * The injected clock (PRD section 25). It reads system UTC time plus an offset, so local demos and
 * end-to-end tests can move the date forward to the 18- or 24-month mark without waiting.
 */
public class AdjustableClock extends Clock {

    private final Clock base;
    private volatile Duration offset = Duration.ZERO;

    public AdjustableClock(Clock base) {
        this.base = base;
    }

    public void setOffset(Duration offset) {
        this.offset = offset;
    }

    public Duration getOffset() {
        return offset;
    }

    @Override
    public ZoneId getZone() {
        return base.getZone();
    }

    @Override
    public Clock withZone(ZoneId zone) {
        AdjustableClock copy = new AdjustableClock(base.withZone(zone));
        copy.offset = offset;
        return copy;
    }

    @Override
    public Instant instant() {
        return base.instant().plus(offset);
    }
}
