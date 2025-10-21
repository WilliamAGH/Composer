package com.composerai.api.util;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

/**
 * Utility class for temporal awareness and relative time calculations.
 * Provides helpers for formatting current time, calculating relative time expressions,
 * and enhancing AI prompts with temporal context.
 */
public final class TemporalUtils {

    private static final DateTimeFormatter HUMAN_READABLE_FORMAT =
        DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy 'at' h:mm a z", Locale.US);

    private TemporalUtils() {
        throw new AssertionError("Utility class should not be instantiated");
    }

    /**
     * Gets the current time in UTC with human-readable formatting.
     *
     * @return formatted UTC timestamp (e.g., "Monday, January 15, 2024 at 3:45 PM UTC")
     */
    public static String getCurrentUtcFormatted() {
        return ZonedDateTime.now(ZoneId.of("UTC")).format(HUMAN_READABLE_FORMAT);
    }

    /**
     * Gets the current time in Pacific Time with human-readable formatting.
     *
     * @return formatted Pacific timestamp (e.g., "Monday, January 15, 2024 at 7:45 AM PST")
     */
    public static String getCurrentPacificFormatted() {
        return ZonedDateTime.now(ZoneId.of("America/Los_Angeles")).format(HUMAN_READABLE_FORMAT);
    }

    /**
     * Calculates relative time expression from an ISO timestamp string.
     * Examples: "2 hours ago", "3 days ago", "just now", "in 2 hours"
     *
     * @param isoTimestamp ISO 8601 formatted timestamp (e.g., "2024-01-15T15:30:00Z")
     * @return relative time expression, or null if parsing fails
     */
    public static String getRelativeTime(String isoTimestamp) {
        if (isoTimestamp == null || isoTimestamp.isBlank()) {
            return null;
        }

        try {
            OffsetDateTime timestamp = OffsetDateTime.parse(isoTimestamp);
            return getRelativeTime(timestamp);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /**
     * Calculates relative time expression from an OffsetDateTime.
     * Examples: "2 hours ago", "3 days ago", "just now", "in 2 hours"
     *
     * @param timestamp the timestamp to compare against now
     * @return relative time expression
     */
    public static String getRelativeTime(OffsetDateTime timestamp) {
        if (timestamp == null) {
            return null;
        }

        OffsetDateTime now = OffsetDateTime.now(timestamp.getOffset());
        Duration duration = Duration.between(timestamp, now);
        long seconds = duration.getSeconds();
        boolean isPast = seconds >= 0;
        long absSeconds = Math.abs(seconds);

        // Just now (within 1 minute)
        if (absSeconds < 60) {
            return "just now";
        }

        // Minutes
        if (absSeconds < 3600) {
            long minutes = absSeconds / 60;
            return formatRelative(minutes, "minute", isPast);
        }

        // Hours
        if (absSeconds < 86400) {
            long hours = absSeconds / 3600;
            return formatRelative(hours, "hour", isPast);
        }

        // Days
        if (absSeconds < 604800) {
            long days = absSeconds / 86400;
            return formatRelative(days, "day", isPast);
        }

        // Weeks
        if (absSeconds < 2592000) {
            long weeks = absSeconds / 604800;
            return formatRelative(weeks, "week", isPast);
        }

        // Months (approximate)
        if (absSeconds < 31536000) {
            long months = absSeconds / 2592000;
            return formatRelative(months, "month", isPast);
        }

        // Years
        long years = absSeconds / 31536000;
        return formatRelative(years, "year", isPast);
    }

    /**
     * Calculates how many hours have passed since the given timestamp.
     *
     * @param isoTimestamp ISO 8601 formatted timestamp
     * @return hours elapsed (negative if timestamp is in the future), or null if parsing fails
     */
    public static Long getHoursSince(String isoTimestamp) {
        if (isoTimestamp == null || isoTimestamp.isBlank()) {
            return null;
        }

        try {
            OffsetDateTime timestamp = OffsetDateTime.parse(isoTimestamp);
            return getHoursSince(timestamp);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /**
     * Calculates how many hours have passed since the given timestamp.
     *
     * @param timestamp the timestamp to compare against now
     * @return hours elapsed (negative if timestamp is in the future)
     */
    public static long getHoursSince(OffsetDateTime timestamp) {
        if (timestamp == null) {
            return 0;
        }
        OffsetDateTime now = OffsetDateTime.now(timestamp.getOffset());
        return Duration.between(timestamp, now).toHours();
    }

    /**
     * Calculates how many days have passed since the given timestamp.
     *
     * @param isoTimestamp ISO 8601 formatted timestamp
     * @return days elapsed (negative if timestamp is in the future), or null if parsing fails
     */
    public static Long getDaysSince(String isoTimestamp) {
        if (isoTimestamp == null || isoTimestamp.isBlank()) {
            return null;
        }

        try {
            OffsetDateTime timestamp = OffsetDateTime.parse(isoTimestamp);
            return getDaysSince(timestamp);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /**
     * Calculates how many days have passed since the given timestamp.
     *
     * @param timestamp the timestamp to compare against now
     * @return days elapsed (negative if timestamp is in the future)
     */
    public static long getDaysSince(OffsetDateTime timestamp) {
        if (timestamp == null) {
            return 0;
        }
        OffsetDateTime now = OffsetDateTime.now(timestamp.getOffset());
        return Duration.between(timestamp, now).toDays();
    }

    /**
     * Creates a temporal context block for AI prompts with current time in UTC and Pacific.
     * This can be injected into system prompts to provide temporal awareness.
     *
     * @return formatted temporal context string with current UTC and Pacific times
     */
    public static String createTemporalContext() {
        return String.format(
            "Current UTC time: %s%nCurrent Pacific Time: %s",
            getCurrentUtcFormatted(),
            getCurrentPacificFormatted()
        );
    }

    /**
     * Enriches an email timestamp with relative time context for AI understanding.
     * Example: "Jan 15, 2024 at 3:45 PM PST (2 hours ago)"
     *
     * @param displayDate the formatted display date from email metadata
     * @param isoTimestamp the ISO timestamp from email metadata
     * @return enriched timestamp with relative time, or original if enrichment fails
     */
    public static String enrichWithRelativeTime(String displayDate, String isoTimestamp) {
        if (displayDate == null || displayDate.isBlank()) {
            return "";
        }

        String relativeTime = getRelativeTime(isoTimestamp);
        if (relativeTime == null) {
            return displayDate;
        }

        return String.format("%s (%s)", displayDate, relativeTime);
    }

    // Private helper methods

    private static String formatRelative(long value, String unit, boolean isPast) {
        String pluralSuffix = value == 1 ? "" : "s";
        String timePhrase = value + " " + unit + pluralSuffix;
        return isPast ? timePhrase + " ago" : "in " + timePhrase;
    }
}
