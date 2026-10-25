package com.example.tuktukbuddy.repository;

import android.util.Log;

import com.example.tuktukbuddy.model.RideRequest;
import com.example.tuktukbuddy.model.Trip;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
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

    public void createRideRequest(
            Trip trip,
            String requesterUid,
            String requesterName,
            ActionCallback callback
    ) {
        if (trip == null || requesterUid == null || requesterUid.trim().isEmpty()) {
            callback.onFailure(new Exception("Invalid request parameters."));
            return;
        }

        if (requesterUid.equals(trip.getCreatorUid())) {
            callback.onFailure(new Exception("You cannot request to join your own trip."));
            return;
        }

        if (trip.getAvailableSeats() <= 0
                || !"OPEN".equalsIgnoreCase(trip.getStatus())) {
            callback.onFailure(new Exception(
                    "This trip has no available seats or is no longer open."
            ));
            return;
        }

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        String activeAuthUid = currentUser != null ? currentUser.getUid() : null;

        Log.d("RideRequestRepo", "createRideRequest - tripId: " + trip.getTripId()
                + ", requesterUid matches active authUid: " + requesterUid.equals(activeAuthUid));

        // Check for duplicate pending request from the same student for the same trip.
        firestore.collection("rideRequests")
                .whereEqualTo("tripId", trip.getTripId())
                .whereEqualTo("requesterUid", requesterUid)
                .whereEqualTo("status", "PENDING")
                .get()
                .addOnSuccessListener(snapshots -> {
                    if (!snapshots.isEmpty()) {
                        Log.w("RideRequestRepo", "createRideRequest blocked: duplicate PENDING request exists.");
                        callback.onFailure(new Exception(
                                "You already have a pending join request for this trip."
                        ));
                        return;
                    }

                    // Re-verify the trip before creating the request.
                    firestore.collection("trips")
                            .document(trip.getTripId())
                            .get()
                            .addOnSuccessListener(tripSnapshot -> {
                                if (!tripSnapshot.exists()) {
                                    callback.onFailure(new Exception(
                                            "Trip no longer exists."
                                    ));
                                    return;
                                }

                                String status = tripSnapshot.getString("status");
                                Long seatsLong = tripSnapshot.getLong("availableSeats");
                                int seats = seatsLong != null
                                        ? seatsLong.intValue() : 0;

                                if (!"OPEN".equalsIgnoreCase(status) || seats <= 0) {
                                    callback.onFailure(new Exception(
                                            "Trip is no longer open or has no remaining seats."
                                    ));
                                    return;
                                }

                                DocumentReference docRef =
                                        firestore.collection("rideRequests").document();

                                String route = String.format(
                                        "%s ➔ %s",
                                        trip.getPickup(),
                                        trip.getDestination()
                                );

                                String dateTime = String.format(
                                        "%s %s",
                                        trip.getDate(),
                                        trip.getTime()
                                );

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
                                        .addOnSuccessListener(aVoid -> {
                                            Log.d("RideRequestRepo", "createRideRequest succeeded! docId: " + docRef.getId());
                                            callback.onSuccess();
                                        })
                                        .addOnFailureListener(e -> {
                                            Log.e("RideRequestRepo", "createRideRequest set failed", e);
                                            callback.onFailure(e);
                                        });
                            })
                            .addOnFailureListener(callback::onFailure);
                })
                .addOnFailureListener(callback::onFailure);
    }

    public void fetchMyRequests(
            String requesterUid,
            RequestsLoadedCallback callback
    ) {
        if (requesterUid == null || requesterUid.trim().isEmpty()) {
            callback.onSuccess(new ArrayList<>());
            return;
        }

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        String activeAuthUid = currentUser != null ? currentUser.getUid() : null;

        Log.d("RideRequestRepo", "fetchMyRequests for requesterUid matches active authUid: "
                + requesterUid.equals(activeAuthUid));

        firestore.collection("rideRequests")
                .whereEqualTo("requesterUid", requesterUid)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<RideRequest> requestList = new ArrayList<>();

                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        RideRequest request = doc.toObject(RideRequest.class);

                        if (request.getRequestId() == null
                                || request.getRequestId().isEmpty()) {
                            request.setRequestId(doc.getId());
                        }

                        requestList.add(request);
                    }

                    requestList.sort((r1, r2) ->
                            Long.compare(r2.getCreatedAt(), r1.getCreatedAt()));

                    Log.d("RideRequestRepo", "fetchMyRequests returned " + requestList.size() + " requests.");
                    callback.onSuccess(requestList);
                })
                .addOnFailureListener(e -> {
                    Log.e("RideRequestRepo", "fetchMyRequests failed", e);
                    callback.onFailure(e);
                });
    }

    public void fetchRequestsForTripCreator(
            String creatorUid,
            RequestsLoadedCallback callback
    ) {
        if (creatorUid == null || creatorUid.trim().isEmpty()) {
            callback.onSuccess(new ArrayList<>());
            return;
        }

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        String activeAuthUid = currentUser != null ? currentUser.getUid() : null;

        Log.d("RideRequestRepo", "fetchRequestsForTripCreator for creatorUid matches active authUid: "
                + creatorUid.equals(activeAuthUid));

        firestore.collection("rideRequests")
                .whereEqualTo("creatorUid", creatorUid)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<RideRequest> requestList = new ArrayList<>();

                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        RideRequest request = doc.toObject(RideRequest.class);

                        if (request.getRequestId() == null
                                || request.getRequestId().isEmpty()) {
                            request.setRequestId(doc.getId());
                        }

                        requestList.add(request);
                    }

                    requestList.sort((r1, r2) ->
                            Long.compare(r2.getCreatedAt(), r1.getCreatedAt()));

                    Log.d("RideRequestRepo", "fetchRequestsForTripCreator returned " + requestList.size() + " requests.");
                    callback.onSuccess(requestList);
                })
                .addOnFailureListener(e -> {
                    Log.e("RideRequestRepo", "fetchRequestsForTripCreator failed", e);
                    callback.onFailure(e);
                });
    }

    public void acceptRideRequest(
            RideRequest request,
            ActionCallback callback
    ) {
        if (request == null
                || request.getTripId() == null
                || request.getRequestId() == null) {
            callback.onFailure(new Exception("Invalid request details."));
            return;
        }

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            callback.onFailure(new Exception("Authentication required to manage requests."));
            return;
        }

        Log.d("RideRequestRepo", "Initiating acceptRideRequest transaction for requestId: "
                + request.getRequestId() + ", tripId: " + request.getTripId());

        DocumentReference tripRef = firestore.collection("trips")
                .document(request.getTripId());

        DocumentReference requestRef = firestore.collection("rideRequests")
                .document(request.getRequestId());

        firestore.runTransaction(transaction -> {
                    DocumentSnapshot tripSnapshot = transaction.get(tripRef);
                    DocumentSnapshot requestSnapshot = transaction.get(requestRef);

                    if (!tripSnapshot.exists()) {
                        Log.w("RideRequestRepo", "Transaction failed: Trip document does not exist.");
                        throw new FirebaseFirestoreException(
                                "Trip does not exist.",
                                FirebaseFirestoreException.Code.NOT_FOUND
                        );
                    }

                    if (!requestSnapshot.exists()) {
                        Log.w("RideRequestRepo", "Transaction failed: Ride request document does not exist.");
                        throw new FirebaseFirestoreException(
                                "Ride request does not exist.",
                                FirebaseFirestoreException.Code.NOT_FOUND
                        );
                    }

                    String currentAuthUid = currentUser.getUid();
                    String tripCreatorUid = tripSnapshot.getString("creatorUid");
                    String requestCreatorUid = requestSnapshot.getString("creatorUid");
                    String storedTripId = requestSnapshot.getString("tripId");
                    String storedRequestId = requestSnapshot.getString("requestId");
                    String currentRequestStatus = requestSnapshot.getString("status");
                    String tripStatus = tripSnapshot.getString("status");
                    Long seatsLong = tripSnapshot.getLong("availableSeats");

                    Log.d("RideRequestRepo", "--- ACCEPT TRANSACTION DIAGNOSTICS ---");
                    Log.d("RideRequestRepo", "Trip exists: " + tripSnapshot.exists() + ", Request exists: " + requestSnapshot.exists());
                    Log.d("RideRequestRepo", "Trip creatorUid matches auth: " + currentAuthUid.equals(tripCreatorUid));
                    Log.d("RideRequestRepo", "Request creatorUid matches auth: " + currentAuthUid.equals(requestCreatorUid));
                    Log.d("RideRequestRepo", "Request tripId matches target tripId: " + (storedTripId != null && storedTripId.equals(request.getTripId())));
                    Log.d("RideRequestRepo", "Request has stored requestId field: " + (storedRequestId != null && !storedRequestId.isEmpty()));
                    Log.d("RideRequestRepo", "Request stored requestId matches doc ID: " + (storedRequestId != null && storedRequestId.equals(request.getRequestId())));
                    Log.d("RideRequestRepo", "Request status is PENDING: " + "PENDING".equalsIgnoreCase(currentRequestStatus) + " (found: '" + currentRequestStatus + "')");
                    Log.d("RideRequestRepo", "Trip status is OPEN: " + "OPEN".equalsIgnoreCase(tripStatus) + " (found: '" + tripStatus + "')");
                    Log.d("RideRequestRepo", "Trip seats available: " + (seatsLong != null ? seatsLong : "NULL"));
                    Log.d("RideRequestRepo", "--------------------------------------");

                    if (!currentAuthUid.equals(tripCreatorUid)
                            || !currentAuthUid.equals(requestCreatorUid)) {
                        Log.w("RideRequestRepo", "Transaction failed: Auth UID does not match trip/request creatorUid.");
                        throw new FirebaseFirestoreException(
                                "Only the trip creator can accept this request.",
                                FirebaseFirestoreException.Code.PERMISSION_DENIED
                        );
                    }

                    if (storedTripId == null || !storedTripId.equals(request.getTripId())) {
                        Log.w("RideRequestRepo", "Transaction failed: Request tripId mismatch.");
                        throw new FirebaseFirestoreException(
                                "Request does not match the specified trip.",
                                FirebaseFirestoreException.Code.FAILED_PRECONDITION
                        );
                    }

                    if (!"PENDING".equalsIgnoreCase(currentRequestStatus)) {
                        Log.w("RideRequestRepo", "Transaction failed: Request status is not PENDING (found: " + currentRequestStatus + ")");
                        throw new FirebaseFirestoreException(
                                "This request has already been processed.",
                                FirebaseFirestoreException.Code.FAILED_PRECONDITION
                        );
                    }

                    int availableSeats = seatsLong != null
                            ? seatsLong.intValue() : 0;

                    if (!"OPEN".equalsIgnoreCase(tripStatus) || availableSeats <= 0) {
                        Log.w("RideRequestRepo", "Transaction failed: Trip status/seats invalid (status: " + tripStatus + ", seats: " + availableSeats + ")");
                        throw new FirebaseFirestoreException(
                                "Cannot accept: Trip is closed or has no available seats remaining.",
                                FirebaseFirestoreException.Code.FAILED_PRECONDITION
                        );
                    }

                    int newSeats = availableSeats - 1;

                    Log.d("RideRequestRepo", "Applying transaction updates - newSeats: " + newSeats);

                    transaction.update(tripRef, "availableSeats", newSeats);
                    transaction.update(tripRef, "lastAcceptedRequestId", request.getRequestId());

                    if (newSeats == 0) {
                        transaction.update(tripRef, "status", "CLOSED");
                    }

                    transaction.update(requestRef, "status", "ACCEPTED");

                    return null;
                }).addOnSuccessListener(aVoid -> {
                    Log.d("RideRequestRepo", "acceptRideRequest transaction succeeded!");
                    callback.onSuccess();
                })
                .addOnFailureListener(e -> {
                    if (e instanceof FirebaseFirestoreException) {
                        FirebaseFirestoreException fe = (FirebaseFirestoreException) e;
                        Log.e("RideRequestRepo", "acceptRideRequest failed with Firestore code: " + fe.getCode(), fe);
                    } else {
                        Log.e("RideRequestRepo", "acceptRideRequest failed with exception: " + e.getMessage(), e);
                    }
                    callback.onFailure(e);
                });
    }

    public void rejectRideRequest(
            RideRequest request,
            ActionCallback callback
    ) {
        if (request == null
                || request.getRequestId() == null
                || request.getTripId() == null) {
            callback.onFailure(new Exception("Invalid request details."));
            return;
        }

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            callback.onFailure(new Exception("Authentication required to manage requests."));
            return;
        }

        DocumentReference requestRef = firestore.collection("rideRequests")
                .document(request.getRequestId());

        firestore.runTransaction(transaction -> {
                    DocumentSnapshot requestSnapshot = transaction.get(requestRef);

                    if (!requestSnapshot.exists()) {
                        throw new FirebaseFirestoreException(
                                "Ride request does not exist.",
                                FirebaseFirestoreException.Code.NOT_FOUND
                        );
                    }

                    String requestCreatorUid = requestSnapshot.getString("creatorUid");
                    String storedTripId = requestSnapshot.getString("tripId");

                    if (!currentUser.getUid().equals(requestCreatorUid)) {
                        throw new FirebaseFirestoreException(
                                "Only the trip creator can reject this request.",
                                FirebaseFirestoreException.Code.PERMISSION_DENIED
                        );
                    }

                    if (storedTripId == null || !storedTripId.equals(request.getTripId())) {
                        throw new FirebaseFirestoreException(
                                "Request does not match the specified trip.",
                                FirebaseFirestoreException.Code.FAILED_PRECONDITION
                        );
                    }

                    String currentRequestStatus = requestSnapshot.getString("status");

                    if (!"PENDING".equalsIgnoreCase(currentRequestStatus)) {
                        throw new FirebaseFirestoreException(
                                "This request has already been processed.",
                                FirebaseFirestoreException.Code.FAILED_PRECONDITION
                        );
                    }

                    transaction.update(requestRef, "status", "REJECTED");

                    return null;
                }).addOnSuccessListener(aVoid -> callback.onSuccess())
                .addOnFailureListener(callback::onFailure);
    }
}
