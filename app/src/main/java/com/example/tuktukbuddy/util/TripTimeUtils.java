package com.example.tuktukbuddy.util;

import com.example.tuktukbuddy.model.Trip;

import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public final class TripTimeUtils {
    public static final String TIME_ZONE_ID = "Asia/Kolkata";
    public static final long CANCELLATION_CUTOFF_MILLIS = 5 * 60 * 1000L;

    private TripTimeUtils() {
    }

    public static Date parseDepartureAt(String date, String time) {
        if (date == null || time == null) {
            return null;
        }

        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd hh:mm a", Locale.US);
        format.setLenient(false);
        format.setTimeZone(TimeZone.getTimeZone(TIME_ZONE_ID));

        String value = date.trim() + " " + time.trim();
        ParsePosition position = new ParsePosition(0);
        Date parsed = format.parse(value, position);
        return parsed != null && position.getIndex() == value.length() ? parsed : null;
    }

    public static String formatDate(Date departureAt) {
        return format(departureAt, "yyyy-MM-dd");
    }

    public static String formatTime(Date departureAt) {
        return format(departureAt, "hh:mm a");
    }

    public static String getDisplayDate(Trip trip) {
        if (trip == null) return null;
        return trip.getDepartureAt() != null
                ? formatDate(trip.getDepartureAt()) : trip.getDate();
    }

    public static String getDisplayTime(Trip trip) {
        if (trip == null) return null;
        return trip.getDepartureAt() != null
                ? formatTime(trip.getDepartureAt()) : trip.getTime();
    }

    private static String format(Date departureAt, String pattern) {
        if (departureAt == null) return null;
        SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.US);
        format.setTimeZone(TimeZone.getTimeZone(TIME_ZONE_ID));
        return format.format(departureAt);
    }

    public static Date getVerifiedDepartureAt(Trip trip) {
        if (trip == null) {
            return null;
        }

        Date stored = trip.getDepartureAt();
        // New trips use this timestamp as the canonical date/time. Legacy trips
        // without it retain their original strings and are parsed only where a
        // safe fallback is needed; no timestamp is written back for them.
        return stored != null ? stored : parseDepartureAt(trip.getDate(), trip.getTime());
    }

    public static String cancellationDisabledReason(Trip trip, long nowMillis) {
        if (trip == null) {
            return "The trip departure date or time could not be read. Cancellation is unavailable.";
        }
        if (trip.getDepartureAt() == null && parseDepartureAt(trip.getDate(), trip.getTime()) == null) {
            return "The trip departure date or time could not be read. Cancellation is unavailable.";
        }
        if (trip.getDepartureAt() == null) {
            return "This trip has no verified departure timestamp. Cancellation is unavailable.";
        }

        Date verified = getVerifiedDepartureAt(trip);
        if (verified == null) {
            return "The trip departure date or time could not be read. Cancellation is unavailable.";
        }
        if (nowMillis >= verified.getTime() - CANCELLATION_CUTOFF_MILLIS) {
            return "Cancellation is unavailable within five minutes of departure.";
        }
        return null;
    }

    public static boolean isActive(Trip trip, long nowMillis) {
        Date departureAt = getVerifiedDepartureAt(trip);
        return departureAt != null && departureAt.getTime() > nowMillis;
    }
}
