package edu.university.grps.common;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Timestamps are stored in UTC and shown in Africa/Accra (NFR-14), formatted as "19 Oct 2026, 09:30". */
public final class Times {

    public static final ZoneId ZONE = ZoneId.of("Africa/Accra");
    public static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.UK);
    public static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.UK);

    private Times() {}

    public static LocalDate today(Clock clock) {
        return LocalDate.now(clock.withZone(ZONE));
    }

    public static ZonedDateTime local(Instant instant) {
        return instant.atZone(ZONE);
    }

    public static String formatDate(LocalDate date) {
        return date == null ? "" : DATE.format(date);
    }

    public static String formatDateTime(Instant instant) {
        return instant == null ? "" : DATE_TIME.format(local(instant));
    }
}
