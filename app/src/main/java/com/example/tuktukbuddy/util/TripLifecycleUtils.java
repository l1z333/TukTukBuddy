package com.example.tuktukbuddy.util;

import com.example.tuktukbuddy.model.RideRequest;
import com.example.tuktukbuddy.model.Trip;

import java.util.Date;

/** Lifecycle interpretation kept separate from OPEN/CLOSED seat availability. */
public final class TripLifecycleUtils {
    public static final String SCHEDULED = "SCHEDULED";
    public static final String IN_PROGRESS = "IN_PROGRESS";
    public static final String COMPLETED = "COMPLETED";
    public static final String CANCELLED = "CANCELLED";
    public static final String UNKNOWN = "UNKNOWN";

    public enum HomeSection { LIVE, PAST, NONE }

    private TripLifecycleUtils() {
    }

    /**
     * Terminal state is persisted. Otherwise canonical departureAt determines
     * whether a trip is scheduled or in progress. Legacy date/time strings are
     * deliberately not converted into lifecycle timestamps.
     */
    public static String getDisplayStatus(Trip trip, long nowMillis) {
        if (trip == null) return UNKNOWN;
        String stored = trip.getLifecycleStatus();
        if (COMPLETED.equalsIgnoreCase(stored)) return COMPLETED;
        if (CANCELLED.equalsIgnoreCase(stored)) return CANCELLED;
        Date departureAt = trip.getDepartureAt();
        if (departureAt == null) return UNKNOWN;
        return nowMillis < departureAt.getTime() ? SCHEDULED : IN_PROGRESS;
    }

    public static boolean isTerminal(Trip trip) {
        if (trip == null || trip.getLifecycleStatus() == null) return false;
        return COMPLETED.equalsIgnoreCase(trip.getLifecycleStatus())
                || CANCELLED.equalsIgnoreCase(trip.getLifecycleStatus());
    }

    public static boolean canComplete(Trip trip, String currentUid, long nowMillis) {
        return trip != null
                && currentUid != null
                && currentUid.equals(trip.getCreatorUid())
                && trip.getDepartureAt() != null
                && nowMillis >= trip.getDepartureAt().getTime()
                && !isTerminal(trip);
    }

    /** Only the creator can cancel a scheduled ride before its canonical departure time. */
    public static boolean canCancel(Trip trip, String currentUid, long nowMillis) {
        return trip != null
                && currentUid != null
                && currentUid.equals(trip.getCreatorUid())
                && trip.getDepartureAt() != null
                && nowMillis < trip.getDepartureAt().getTime()
                && !isTerminal(trip)
                && !IN_PROGRESS.equalsIgnoreCase(trip.getLifecycleStatus())
                && (trip.getLifecycleStatus() == null
                || SCHEDULED.equalsIgnoreCase(trip.getLifecycleStatus()));
    }

    /** Pending requests for a cancelled trip must not expose creator actions. */
    public static boolean shouldOfferManageActions(RideRequest request, Trip trip) {
        return request != null
                && "PENDING".equalsIgnoreCase(request.getStatus())
                && trip != null
                && !CANCELLED.equalsIgnoreCase(trip.getLifecycleStatus());
    }

    public static boolean isLiveAcceptedRide(Trip trip, RideRequest request, long nowMillis) {
        return request != null
                && "ACCEPTED".equalsIgnoreCase(request.getStatus())
                && (trip == null || !isTerminal(trip));
    }

    public static HomeSection getHomeSection(Trip trip, RideRequest request, long nowMillis) {
        if (request == null) return HomeSection.NONE;
        String requestStatus = request.getStatus();
        boolean accepted = "ACCEPTED".equalsIgnoreCase(requestStatus);
        boolean historical = accepted
                || "CANCELLED".equalsIgnoreCase(requestStatus)
                || "TRIP_CANCELLED".equalsIgnoreCase(requestStatus);
        if (isTerminal(trip) && historical) return HomeSection.PAST;
        if (!accepted) return HomeSection.NONE;
        if (isLiveAcceptedRide(trip, request, nowMillis)) return HomeSection.LIVE;
        return HomeSection.NONE;
    }
}
