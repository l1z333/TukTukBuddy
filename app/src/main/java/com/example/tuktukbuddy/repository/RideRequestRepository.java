package com.example.tuktukbuddy.repository;

import com.example.tuktukbuddy.model.RideRequest;
import com.example.tuktukbuddy.model.Trip;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public class RideRequestRepository {
    private final FirebaseFirestore firestore;

    public interface ActionCallback {
        void onSuccess();
        void onFailure(Exception e);
    }

    public interface RequestsLoadedCallback {
        void onSuccess(List<RideRequest> requests);
        void onFailure(Exception e);
    }

    public RideRequestRepository() {
        this.firestore = FirebaseFirestore.getInstance();
    }

    public void createRideRequest(Trip trip, String requesterUid, String requesterName, ActionCallback callback) {
        if (trip == null || requesterUid == null || requesterUid.trim().isEmpty()) {
            callback.onFailure(new Exception("Invalid request parameters."));
            return;
        }

        if (requesterUid.equals(trip.getCreatorUid())) {
            callback.onFailure(new Exception("You cannot request to join your own trip."));
            return;
        }

        if (trip.getAvailableSeats() <= 0 || !"OPEN".equalsIgnoreCase(trip.getStatus())) {
            callback.onFailure(new Exception("This trip has no available seats or is no longer open."));
            return;
        }

        // Check for duplicate request from same student for same trip
        firestore.collection("rideRequests")
                .whereEqualTo("tripId", trip.getTripId())
                .whereEqualTo("requesterUid", requesterUid)
                .get()
                .addOnSuccessListener(snapshots -> {
                    if (!snapshots.isEmpty()) {
                        callback.onFailure(new Exception("You have already submitted a join request for this trip."));
                        return;
                    }

                    // Re-verify trip document from Firestore before creating request
                    firestore.collection("trips")
                            .document(trip.getTripId())
                            .get()
                            .addOnSuccessListener(tripSnapshot -> {
                                if (!tripSnapshot.exists()) {
                                    callback.onFailure(new Exception("Trip no longer exists."));
                                    return;
                                }

                                String status = tripSnapshot.getString("status");
                                Long seatsLong = tripSnapshot.getLong("availableSeats");
                                int seats = seatsLong != null ? seatsLong.intValue() : 0;

                                if (!"OPEN".equalsIgnoreCase(status) || seats <= 0) {
                                    callback.onFailure(new Exception("Trip is no longer open or has no remaining seats."));
                                    return;
                                }

                                DocumentReference docRef = firestore.collection("rideRequests").document();
                                String route = String.format("%s ➔ %s", trip.getPickup(), trip.getDestination());
                                String dateTime = String.format("%s %s", trip.getDate(), trip.getTime());

                                RideRequest request = new RideRequest(
                                        docRef.getId(),
                                        trip.getTripId(),
                                        route,
                                        dateTime,
                                        trip.getCreatorUid(),
                                        requesterUid,
                                        requesterName,
                                        "PENDING",
                                        System.currentTimeMillis()
                                );

                                docRef.set(request.toMap())
                                        .addOnSuccessListener(aVoid -> callback.onSuccess())
                                        .addOnFailureListener(callback::onFailure);
                            })
                            .addOnFailureListener(callback::onFailure);
                })
                .addOnFailureListener(callback::onFailure);
    }

    public void fetchMyRequests(String requesterUid, RequestsLoadedCallback callback) {
        if (requesterUid == null || requesterUid.trim().isEmpty()) {
            callback.onSuccess(new ArrayList<>());
            return;
        }

        firestore.collection("rideRequests")
                .whereEqualTo("requesterUid", requesterUid)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<RideRequest> requestList = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        RideRequest request = doc.toObject(RideRequest.class);
                        if (request.getRequestId() == null || request.getRequestId().isEmpty()) {
                            request.setRequestId(doc.getId());
                        }
                        requestList.add(request);
                    }
                    requestList.sort((r1, r2) -> Long.compare(r2.getCreatedAt(), r1.getCreatedAt()));
                    callback.onSuccess(requestList);
                })
                .addOnFailureListener(callback::onFailure);
    }

    public void fetchRequestsForTripCreator(String creatorUid, RequestsLoadedCallback callback) {
        if (creatorUid == null || creatorUid.trim().isEmpty()) {
            callback.onSuccess(new ArrayList<>());
            return;
        }

        firestore.collection("rideRequests")
                .whereEqualTo("creatorUid", creatorUid)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<RideRequest> requestList = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        RideRequest request = doc.toObject(RideRequest.class);
                        if (request.getRequestId() == null || request.getRequestId().isEmpty()) {
                            request.setRequestId(doc.getId());
                        }
                        requestList.add(request);
                    }
                    requestList.sort((r1, r2) -> Long.compare(r2.getCreatedAt(), r1.getCreatedAt()));
                    callback.onSuccess(requestList);
                })
                .addOnFailureListener(callback::onFailure);
    }

    public void acceptRideRequest(RideRequest request, ActionCallback callback) {
        if (request == null || request.getTripId() == null || request.getRequestId() == null) {
            callback.onFailure(new Exception("Invalid request details."));
            return;
        }

        DocumentReference tripRef = firestore.collection("trips").document(request.getTripId());
        DocumentReference requestRef = firestore.collection("rideRequests").document(request.getRequestId());

        firestore.runTransaction(transaction -> {
            DocumentSnapshot tripSnapshot = transaction.get(tripRef);
            DocumentSnapshot requestSnapshot = transaction.get(requestRef);

            if (!tripSnapshot.exists()) {
                throw new FirebaseFirestoreException("Trip does not exist.", FirebaseFirestoreException.Code.NOT_FOUND);
            }

            if (!requestSnapshot.exists()) {
                throw new FirebaseFirestoreException("Ride request does not exist.", FirebaseFirestoreException.Code.NOT_FOUND);
            }

            String currentRequestStatus = requestSnapshot.getString("status");
            if (!"PENDING".equalsIgnoreCase(currentRequestStatus)) {
                throw new FirebaseFirestoreException("This request has already been processed.", FirebaseFirestoreException.Code.FAILED_PRECONDITION);
            }

            String tripStatus = tripSnapshot.getString("status");
            Long seatsLong = tripSnapshot.getLong("availableSeats");
            int availableSeats = seatsLong != null ? seatsLong.intValue() : 0;

            if (!"OPEN".equalsIgnoreCase(tripStatus) || availableSeats <= 0) {
                throw new FirebaseFirestoreException("Cannot accept: Trip is closed or has no available seats remaining.", FirebaseFirestoreException.Code.FAILED_PRECONDITION);
            }

            int newSeats = availableSeats - 1;
            transaction.update(tripRef, "availableSeats", newSeats);
            if (newSeats == 0) {
                transaction.update(tripRef, "status", "CLOSED");
            }

            transaction.update(requestRef, "status", "ACCEPTED");
            return null;
        }).addOnSuccessListener(aVoid -> callback.onSuccess())
          .addOnFailureListener(callback::onFailure);
    }

    public void rejectRideRequest(RideRequest request, ActionCallback callback) {
        if (request == null || request.getRequestId() == null) {
            callback.onFailure(new Exception("Invalid request details."));
            return;
        }

        DocumentReference requestRef = firestore.collection("rideRequests").document(request.getRequestId());

        firestore.runTransaction(transaction -> {
            DocumentSnapshot requestSnapshot = transaction.get(requestRef);

            if (!requestSnapshot.exists()) {
                throw new FirebaseFirestoreException("Ride request does not exist.", FirebaseFirestoreException.Code.NOT_FOUND);
            }

            String currentRequestStatus = requestSnapshot.getString("status");
            if (!"PENDING".equalsIgnoreCase(currentRequestStatus)) {
                throw new FirebaseFirestoreException("This request has already been processed.", FirebaseFirestoreException.Code.FAILED_PRECONDITION);
            }

            transaction.update(requestRef, "status", "REJECTED");
            return null;
        }).addOnSuccessListener(aVoid -> callback.onSuccess())
          .addOnFailureListener(callback::onFailure);
    }
}