package com.example.tuktukbuddy.repository;

import com.example.tuktukbuddy.model.Trip;
import com.example.tuktukbuddy.util.TripLifecycleUtils;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.functions.FirebaseFunctions;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Collections;

public class TripRepository {
    private final FirebaseFirestore firestore;

    public interface ActionCallback {
        void onSuccess();
        void onFailure(Exception e);
    }

    public interface TripsLoadedCallback {
        void onSuccess(List<Trip> trips);
        void onFailure(Exception e);
    }

    public TripRepository() {
        this.firestore = FirebaseFirestore.getInstance();
    }

    public void createTrip(Trip trip, ActionCallback callback) {
        if (trip == null) {
            callback.onFailure(new Exception("Trip data cannot be null."));
            return;
        }

        DocumentReference docRef = firestore.collection("trips").document();
        trip.setTripId(docRef.getId());
        trip.setLifecycleStatus(TripLifecycleUtils.SCHEDULED);

        docRef.set(trip.toMap())
                .addOnSuccessListener(aVoid -> callback.onSuccess())
                .addOnFailureListener(callback::onFailure);
    }

    /** Mark a trip completed without changing its seats or ride requests. */
    public void markTripCompleted(String tripId, ActionCallback callback) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null || tripId == null || tripId.trim().isEmpty()) {
            callback.onFailure(new Exception("Authentication and a valid trip are required."));
            return;
        }

        DocumentReference tripRef = firestore.collection("trips").document(tripId);
        String currentUid = currentUser.getUid();
        firestore.runTransaction(transaction -> {
            DocumentSnapshot snapshot = transaction.get(tripRef);
            if (!snapshot.exists()) {
                throw new FirebaseFirestoreException("Trip does not exist.",
                        FirebaseFirestoreException.Code.NOT_FOUND);
            }
            if (!currentUid.equals(snapshot.getString("creatorUid"))) {
                throw new FirebaseFirestoreException("Only the trip creator can complete this trip.",
                        FirebaseFirestoreException.Code.PERMISSION_DENIED);
            }

            String lifecycle = snapshot.getString("lifecycleStatus");
            if (TripLifecycleUtils.COMPLETED.equalsIgnoreCase(lifecycle)) {
                return null; // Idempotent retry after a previously committed completion.
            }
            if (TripLifecycleUtils.CANCELLED.equalsIgnoreCase(lifecycle)) {
                throw new FirebaseFirestoreException("A cancelled trip cannot be completed.",
                        FirebaseFirestoreException.Code.FAILED_PRECONDITION);
            }

            Date departureAt = snapshot.getDate("departureAt");
            if (departureAt == null) {
                throw new FirebaseFirestoreException(
                        "This legacy trip has no canonical departure timestamp and cannot be completed safely.",
                        FirebaseFirestoreException.Code.FAILED_PRECONDITION);
            }
            if (System.currentTimeMillis() < departureAt.getTime()) {
                throw new FirebaseFirestoreException("A trip cannot be completed before departure.",
                        FirebaseFirestoreException.Code.FAILED_PRECONDITION);
            }

            transaction.update(tripRef, "lifecycleStatus", TripLifecycleUtils.COMPLETED);
            return null;
        }).addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(callback::onFailure);
    }

    /** Persist IN_PROGRESS once the creator's canonical departure time has arrived. */
    public void markTripInProgressIfDue(String tripId, ActionCallback callback) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null || tripId == null || tripId.trim().isEmpty()) {
            callback.onFailure(new Exception("Authentication and a valid trip are required."));
            return;
        }
        DocumentReference tripRef = firestore.collection("trips").document(tripId);
        String currentUid = currentUser.getUid();
        firestore.runTransaction(transaction -> {
            DocumentSnapshot snapshot = transaction.get(tripRef);
            if (!snapshot.exists()) {
                throw new FirebaseFirestoreException("Trip does not exist.",
                        FirebaseFirestoreException.Code.NOT_FOUND);
            }
            if (!currentUid.equals(snapshot.getString("creatorUid"))) {
                throw new FirebaseFirestoreException("Only the trip creator can update its lifecycle.",
                        FirebaseFirestoreException.Code.PERMISSION_DENIED);
            }
            String lifecycle = snapshot.getString("lifecycleStatus");
            if (TripLifecycleUtils.IN_PROGRESS.equalsIgnoreCase(lifecycle)
                    || TripLifecycleUtils.COMPLETED.equalsIgnoreCase(lifecycle)
                    || TripLifecycleUtils.CANCELLED.equalsIgnoreCase(lifecycle)) return null;
            Date departureAt = snapshot.getDate("departureAt");
            if (departureAt == null || System.currentTimeMillis() < departureAt.getTime()) {
                throw new FirebaseFirestoreException(
                        "The trip has no due canonical departure time.",
                        FirebaseFirestoreException.Code.FAILED_PRECONDITION);
            }
            transaction.update(tripRef, "lifecycleStatus", TripLifecycleUtils.IN_PROGRESS);
            return null;
        }).addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(callback::onFailure);
    }

    /** Whole-trip cancellation is server-owned so every active request and notice changes atomically. */
    public void cancelTrip(String tripId, ActionCallback callback) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null || tripId == null || tripId.trim().isEmpty()) {
            callback.onFailure(new Exception("Authentication and a valid trip are required."));
            return;
        }
        FirebaseFunctions.getInstance().getHttpsCallable("cancelTrip")
                .call(Collections.singletonMap("tripId", tripId))
                .addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(callback::onFailure);
    }

    /** Hide a terminal trip from this user's home history without deleting shared records. */
    public void hideTripFromHistory(String userUid, String tripId, ActionCallback callback) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null || userUid == null || !userUid.equals(currentUser.getUid())
                || tripId == null || tripId.trim().isEmpty()) {
            callback.onFailure(new Exception("Only the signed-in user can update their trip history."));
            return;
        }

        Map<String, Object> marker = new HashMap<>();
        marker.put("tripId", tripId);
        marker.put("hiddenAt", FieldValue.serverTimestamp());
        firestore.collection("users").document(userUid).collection("hiddenTrips")
                .document(tripId).set(marker)
                .addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(callback::onFailure);
    }

    public void fetchOpenTrips(String currentUid, TripsLoadedCallback callback) {
        firestore.collection("trips")
                .whereEqualTo("status", "OPEN")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<Trip> tripList = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        Trip trip = doc.toObject(Trip.class);
                        if (trip.getTripId() == null || trip.getTripId().isEmpty()) {
                            trip.setTripId(doc.getId());
                        }
                        // Exclude current user's own trips
                        if (trip.isAvailableForBrowsing()
                                && (currentUid == null || !currentUid.equals(trip.getCreatorUid()))) {
                            tripList.add(trip);
                        }
                    }
                    // Sort by createdAt descending (newest first)
                    tripList.sort((t1, t2) -> Long.compare(t2.getCreatedAt(), t1.getCreatedAt()));
                    callback.onSuccess(tripList);
                })
                .addOnFailureListener(callback::onFailure);
    }
}
