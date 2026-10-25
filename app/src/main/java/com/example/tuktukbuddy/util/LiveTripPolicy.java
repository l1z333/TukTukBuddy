package com.example.tuktukbuddy.util;

import com.example.tuktukbuddy.model.RideRequest;
import com.example.tuktukbuddy.model.Trip;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Shared Live Trips eligibility and document-ID indexing rules. */
public final class LiveTripPolicy {
    private LiveTripPolicy() {
    }

    /** Live means the ride has not been completed or cancelled. Seat status is separate. */
    public static boolean isLiveTrip(Trip trip, long nowMillis) {
        return trip != null && !TripLifecycleUtils.isTerminal(trip);
    }

    public static boolean isAcceptedRequest(RideRequest request) {
        return request != null && "ACCEPTED".equalsIgnoreCase(request.getStatus());
    }

    public static boolean isLiveCreatorTrip(Trip trip, int acceptedRequestCount, long nowMillis) {
        return acceptedRequestCount > 0 && isLiveTrip(trip, nowMillis);
    }

    /** Upcoming poster management contains only scheduled trips with no accepted passenger. */
    public static boolean isUpcomingCreatorTrip(Trip trip, int acceptedRequestCount, long nowMillis) {
        return acceptedRequestCount == 0
                && trip != null
                && !TripLifecycleUtils.isTerminal(trip)
                && TripLifecycleUtils.SCHEDULED.equals(
                TripLifecycleUtils.getDisplayStatus(trip, nowMillis));
    }

    public static boolean isLiveRequesterRide(RideRequest request, Trip trip, long nowMillis) {
        return isAcceptedRequest(request)
                && TripLifecycleUtils.isLiveAcceptedRide(trip, request, nowMillis);
    }

    /** Render each accepted requester once beneath one creator trip card. */
    public static List<RideRequest> uniquePassengersByRequesterId(Collection<RideRequest> requests) {
        List<RideRequest> unique = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        if (requests == null) return unique;
        for (RideRequest request : requests) {
            if (!isAcceptedRequest(request)) continue;
            String key = request.getRequesterUid();
            if (key == null || key.isEmpty()) key = request.getRequestId();
            if (key != null && seen.add(key)) unique.add(request);
        }
        return unique;
    }

    /** Keep one requester card per trip even if legacy duplicate request documents exist. */
    public static void addRequesterRideByTripId(Map<String, RideRequest> rides, RideRequest request) {
        if (rides == null || request == null || !isAcceptedRequest(request)
                || request.getTripId() == null || request.getTripId().isEmpty()) return;
        rides.putIfAbsent(request.getTripId(), request);
    }

    /** Index creator trips once by their Firestore document ID. */
    public static void addTripByDocumentId(Map<String, Trip> trips, String documentId, Trip trip) {
        if (trips == null || documentId == null || documentId.isEmpty() || trip == null) return;
        trip.setTripId(documentId);
        trips.putIfAbsent(documentId, trip);
    }

    /** Index a request once by its Firestore document ID, regardless of snapshot repetition. */
    public static void addRequestByDocumentId(Map<String, RideRequest> requests,
                                              String documentId, RideRequest request) {
        if (requests == null || documentId == null || documentId.isEmpty() || request == null) return;
        request.setRequestId(documentId);
        requests.putIfAbsent(documentId, request);
    }
}
