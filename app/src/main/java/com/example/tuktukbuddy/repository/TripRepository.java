package com.example.tuktukbuddy.repository;

import com.example.tuktukbuddy.model.Trip;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

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

        docRef.set(trip.toMap())
                .addOnSuccessListener(aVoid -> callback.onSuccess())
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
                        if (currentUid == null || !currentUid.equals(trip.getCreatorUid())) {
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