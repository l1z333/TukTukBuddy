package com.example.tuktukbuddy;

import com.example.tuktukbuddy.model.Trip;
import com.example.tuktukbuddy.model.RideRequest;
import com.example.tuktukbuddy.util.LiveTripPolicy;
import com.example.tuktukbuddy.util.TripLifecycleUtils;
import com.example.tuktukbuddy.util.TripTimeUtils;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertNotNull;

import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;
import java.util.LinkedHashMap;
import java.util.Map;

public class TripAvailabilityTest {

    @Test
    public void onlyOpenTripsWithSeatsAreAvailableForBrowsing() {
        Trip openWithSeats = trip("OPEN", 2);
        Trip closedWithSeats = trip("CLOSED", 2);
        Trip openWithNoSeats = trip("OPEN", 0);
        Trip openWithNegativeSeats = trip("OPEN", -1);

        assertTrue(openWithSeats.isAvailableForBrowsing());
        assertFalse(closedWithSeats.isAvailableForBrowsing());
        assertFalse(openWithNoSeats.isAvailableForBrowsing());
        assertFalse(openWithNegativeSeats.isAvailableForBrowsing());

        openWithSeats.setLifecycleStatus(TripLifecycleUtils.COMPLETED);
        closedWithSeats.setLifecycleStatus(TripLifecycleUtils.SCHEDULED);
        assertTrue("Lifecycle does not change seat availability", openWithSeats.isAvailableForBrowsing());
        assertFalse("Seat status remains independent of lifecycle", closedWithSeats.isAvailableForBrowsing());
    }

    @Test
    public void acceptingTheFinalSeatClosesTheTrip() {
        assertEquals("CLOSED", Trip.statusAfterAcceptingSeat(0));
    }

    @Test
    public void acceptingASeatWithSeatsRemainingKeepsTripOpen() {
        assertEquals("OPEN", Trip.statusAfterAcceptingSeat(1));
    }

    @Test
    public void acceptedTripsRemainLiveRegardlessOfSeatStateUntilTerminal() {
        Date departure = TripTimeUtils.parseDepartureAt("2026-10-25", "10:30 AM");
        long justBeforeDeparture = departure.getTime() - 1L;
        Trip openWithSeats = scheduledTrip("OPEN", 1, departure);
        Trip fullAndClosed = scheduledTrip("CLOSED", 0, departure);
        Trip noSeatsButOpen = scheduledTrip("OPEN", 0, departure);
        Trip alreadyDeparted = scheduledTrip("OPEN", 1, new Date(departure.getTime() - 1L));
        RideRequest accepted = request("trip-A", "Same Route");
        RideRequest pending = request("trip-A", "Same Route");
        pending.setStatus("PENDING");

        assertTrue(LiveTripPolicy.isLiveTrip(openWithSeats, justBeforeDeparture));
        assertTrue(LiveTripPolicy.isLiveCreatorTrip(openWithSeats, 1, justBeforeDeparture));
        assertTrue(LiveTripPolicy.isLiveRequesterRide(accepted, openWithSeats, justBeforeDeparture));
        assertTrue(LiveTripPolicy.isLiveTrip(fullAndClosed, justBeforeDeparture));
        assertTrue(LiveTripPolicy.isLiveCreatorTrip(fullAndClosed, 1, justBeforeDeparture));
        assertFalse(LiveTripPolicy.isUpcomingCreatorTrip(fullAndClosed, 1, justBeforeDeparture));
        assertTrue(LiveTripPolicy.isLiveRequesterRide(accepted, fullAndClosed, justBeforeDeparture));
        assertTrue(TripTimeUtils.isActive(fullAndClosed, justBeforeDeparture));
        assertEquals(TripLifecycleUtils.HomeSection.LIVE,
                TripLifecycleUtils.getHomeSection(fullAndClosed, accepted, justBeforeDeparture));
        assertTrue("Seat availability is independent from lifecycle", 
                LiveTripPolicy.isLiveTrip(noSeatsButOpen, justBeforeDeparture));
        assertTrue(LiveTripPolicy.isUpcomingCreatorTrip(openWithSeats, 0, justBeforeDeparture));
        assertFalse(LiveTripPolicy.isUpcomingCreatorTrip(openWithSeats, 1, justBeforeDeparture));
        assertTrue("After departure an accepted ride remains live until explicitly completed",
                LiveTripPolicy.isLiveRequesterRide(accepted, alreadyDeparted, departure.getTime()));
        assertFalse(LiveTripPolicy.isLiveRequesterRide(pending, openWithSeats, justBeforeDeparture));
    }

    @Test
    public void requestIndexDeduplicatesOnlySameDocumentId() {
        Map<String, RideRequest> indexed = new LinkedHashMap<>();
        RideRequest firstSnapshot = request("trip-A", "Same Route");
        RideRequest repeatedSnapshot = request("trip-A", "Same Route");
        RideRequest distinctRequest = request("trip-A", "Same Route");

        LiveTripPolicy.addRequestByDocumentId(indexed, "request-1", firstSnapshot);
        LiveTripPolicy.addRequestByDocumentId(indexed, "request-1", repeatedSnapshot);
        LiveTripPolicy.addRequestByDocumentId(indexed, "request-2", distinctRequest);

        assertEquals(2, indexed.size());
        assertEquals("request-1", indexed.get("request-1").getRequestId());
        assertEquals("request-2", indexed.get("request-2").getRequestId());
    }

    @Test
    public void rendersOneRequesterRidePerTripAndOnePassengerPerRequester() {
        Map<String, RideRequest> requesterCards = new LinkedHashMap<>();
        RideRequest first = request("trip-A", "Same Route");
        first.setRequestId("request-1");
        first.setRequesterUid("student-1");
        RideRequest duplicateRide = request("trip-A", "Same Route");
        duplicateRide.setRequestId("request-2");
        duplicateRide.setRequesterUid("student-1");
        RideRequest differentTrip = request("trip-B", "Same Route");
        differentTrip.setRequestId("request-3");
        differentTrip.setRequesterUid("student-1");
        LiveTripPolicy.addRequesterRideByTripId(requesterCards, first);
        LiveTripPolicy.addRequesterRideByTripId(requesterCards, duplicateRide);
        LiveTripPolicy.addRequesterRideByTripId(requesterCards, differentTrip);
        assertEquals(2, requesterCards.size());
        assertEquals("request-1", requesterCards.get("trip-A").getRequestId());
        assertTrue(requesterCards.containsKey("trip-B"));

        RideRequest secondPassenger = request("trip-A", "Same Route");
        secondPassenger.setRequestId("request-4");
        secondPassenger.setRequesterUid("student-2");
        assertEquals(2, LiveTripPolicy.uniquePassengersByRequesterId(
                java.util.Arrays.asList(first, duplicateRide, secondPassenger)).size());
    }

    @Test
    public void creatorTripIndexKeepsOneTripPerDocumentId() {
        Map<String, Trip> indexed = new LinkedHashMap<>();
        Trip firstSnapshot = scheduledTrip("OPEN", 1, new Date(1000L));
        Trip repeatedSnapshot = scheduledTrip("CLOSED", 0, new Date(1000L));
        Trip distinctTrip = scheduledTrip("OPEN", 1, new Date(1000L));

        LiveTripPolicy.addTripByDocumentId(indexed, "trip-1", firstSnapshot);
        LiveTripPolicy.addTripByDocumentId(indexed, "trip-1", repeatedSnapshot);
        LiveTripPolicy.addTripByDocumentId(indexed, "trip-2", distinctTrip);

        assertEquals(2, indexed.size());
        assertEquals("trip-1", indexed.get("trip-1").getTripId());
        assertEquals("trip-2", indexed.get("trip-2").getTripId());
        assertEquals("OPEN", indexed.get("trip-1").getStatus());
    }

    @Test
    public void completedTripsArePastAndRequestersCannotComplete() {
        Trip completed = scheduledTrip("CLOSED", 0, new Date(1L));
        completed.setCreatorUid("creator");
        completed.setLifecycleStatus(TripLifecycleUtils.COMPLETED);
        Trip scheduled = scheduledTrip("CLOSED", 0, new Date(1L));
        scheduled.setCreatorUid("creator");

        assertTrue(TripLifecycleUtils.isTerminal(completed));
        assertEquals(TripLifecycleUtils.HomeSection.PAST,
                TripLifecycleUtils.getHomeSection(completed,
                        request("trip-A", "Same Route"), 2L));
        assertFalse(LiveTripPolicy.isLiveTrip(completed, 2L));
        assertFalse(TripLifecycleUtils.canComplete(completed, "creator", 2L));
        assertTrue(TripLifecycleUtils.canComplete(scheduled, "creator", 2L));
        assertFalse(TripLifecycleUtils.canComplete(scheduled, "requester", 2L));
        assertFalse(TripLifecycleUtils.canComplete(scheduled, "creator", 0L));
    }

    @Test
    public void onlyCreatorCanCancelAUpcomingTripBeforeDeparture() {
        Trip scheduled = scheduledTrip("CLOSED", 0, new Date(10_000L));
        scheduled.setCreatorUid("creator");
        Trip inProgress = scheduledTrip("CLOSED", 0, new Date(1_000L));
        inProgress.setCreatorUid("creator");
        inProgress.setLifecycleStatus(TripLifecycleUtils.IN_PROGRESS);
        Trip legacy = trip("OPEN", 1);
        legacy.setCreatorUid("creator");

        assertTrue(TripLifecycleUtils.canCancel(scheduled, "creator", 9_999L));
        assertFalse(TripLifecycleUtils.canCancel(scheduled, "requester", 9_999L));
        assertFalse(TripLifecycleUtils.canCancel(scheduled, "creator", 10_000L));
        assertFalse(TripLifecycleUtils.canCancel(inProgress, "creator", 2_000L));
        assertFalse(TripLifecycleUtils.canCancel(legacy, "creator", 1L));
    }

    @Test
    public void legacyTripWithoutDepartureOrLifecycleRemainsUnknownAndCannotBeCompleted() {
        Trip legacy = trip("CLOSED", 0);
        legacy.setCreatorUid("creator");
        legacy.setDate("2020-01-01");
        legacy.setTime("10:00 AM");
        RideRequest accepted = request("legacy-trip", "Old Route");

        assertEquals("2020-01-01", TripTimeUtils.getDisplayDate(legacy));
        assertEquals("10:00 AM", TripTimeUtils.getDisplayTime(legacy));
        assertEquals(TripLifecycleUtils.UNKNOWN, TripLifecycleUtils.getDisplayStatus(legacy, 10L));
        assertTrue(LiveTripPolicy.isLiveRequesterRide(accepted, legacy, 10L));
        assertEquals(TripLifecycleUtils.HomeSection.LIVE,
                TripLifecycleUtils.getHomeSection(legacy, accepted, 10L));
        assertFalse(TripLifecycleUtils.canComplete(legacy, "creator", 10L));
        assertNull(legacy.getDepartureAt());
        assertNull(legacy.getLifecycleStatus());
    }

    @Test
    public void parsesAppDateAndTimeInKolkataTimezone() {
        Date departure = TripTimeUtils.parseDepartureAt("2026-10-25", "10:30 AM");
        assertNotNull(departure);
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone(TripTimeUtils.TIME_ZONE_ID));
        calendar.setTime(departure);
        assertEquals(10, calendar.get(Calendar.HOUR_OF_DAY));
        assertEquals(30, calendar.get(Calendar.MINUTE));
    }

    @Test
    public void departureTimestampOverridesStaleDisplayStrings() {
        Trip trip = trip("OPEN", 1);
        trip.setDate("2026-10-25");
        trip.setTime("08:00 AM");
        Date canonical = TripTimeUtils.parseDepartureAt("2026-10-25", "10:30 AM");
        trip.setDepartureAt(canonical);

        assertEquals("2026-10-25", TripTimeUtils.getDisplayDate(trip));
        assertEquals("10:30 AM", TripTimeUtils.getDisplayTime(trip));
        assertEquals(canonical, TripTimeUtils.getVerifiedDepartureAt(trip));
        assertNull(TripTimeUtils.cancellationDisabledReason(
                trip, canonical.getTime() - 10 * 60 * 1000L));
    }

    @Test
    public void legacyTripKeepsStoredDisplayStringsWithoutAddingTimestamp() {
        Trip trip = trip("OPEN", 1);
        trip.setDate("2026-10-25");
        trip.setTime("08:00 AM");

        assertEquals("2026-10-25", TripTimeUtils.getDisplayDate(trip));
        assertEquals("08:00 AM", TripTimeUtils.getDisplayTime(trip));
        assertNotNull(TripTimeUtils.getVerifiedDepartureAt(trip));
        assertNull(trip.getDepartureAt());
        assertTrue(TripTimeUtils.cancellationDisabledReason(trip, 0)
                .contains("verified departure timestamp"));
    }

    @Test
    public void invalidOrUnverifiedDepartureTimeDisablesCancellation() {
        Trip malformed = trip("OPEN", 1);
        malformed.setDate("2026-02-30");
        malformed.setTime("10:30 AM");
        assertNotNull(TripTimeUtils.cancellationDisabledReason(malformed, 0));

        Trip legacy = trip("OPEN", 1);
        legacy.setDate("2026-10-25");
        legacy.setTime("10:30 AM");
        assertTrue(TripTimeUtils.cancellationDisabledReason(legacy, 0).contains("verified departure"));
    }

    @Test
    public void cancellationCutoffIsStrictlyBeforeFiveMinutes() {
        Trip trip = trip("OPEN", 1);
        trip.setDate("2026-10-25");
        trip.setTime("10:30 AM");
        Date departure = TripTimeUtils.parseDepartureAt(trip.getDate(), trip.getTime());
        trip.setDepartureAt(departure);

        assertNull(TripTimeUtils.cancellationDisabledReason(trip, departure.getTime() - 300_001L));
        assertNotNull(TripTimeUtils.cancellationDisabledReason(trip, departure.getTime() - 300_000L));
        assertNotNull(TripTimeUtils.cancellationDisabledReason(trip, departure.getTime()));
    }

    private Trip trip(String status, int availableSeats) {
        Trip trip = new Trip();
        trip.setStatus(status);
        trip.setAvailableSeats(availableSeats);
        return trip;
    }

    private Trip scheduledTrip(String status, int availableSeats, Date departureAt) {
        Trip trip = trip(status, availableSeats);
        trip.setDepartureAt(departureAt);
        return trip;
    }

    private RideRequest request(String tripId, String route) {
        RideRequest request = new RideRequest();
        request.setTripId(tripId);
        request.setTripRoute(route);
        request.setStatus("ACCEPTED");
        return request;
    }
}
