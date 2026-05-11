package chit.tefca.common.util;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Date/time utilities for consistent formatting across services.
 */
public final class DateTimeUtils {

    private static final DateTimeFormatter ISO_FORMATTER =
            DateTimeFormatter.ISO_INSTANT.withZone(ZoneOffset.UTC);

    private DateTimeUtils() {
    }

    public static String formatIso(Instant instant) {
        return ISO_FORMATTER.format(instant);
    }

    public static Instant parseIso(String isoString) {
        return Instant.parse(isoString);
    }
}
