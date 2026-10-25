package com.example.tuktukbuddy;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.tuktukbuddy.model.RideRequest;
import com.example.tuktukbuddy.model.Trip;
import com.example.tuktukbuddy.model.User;
import com.example.tuktukbuddy.repository.RideRequestRepository;
import com.example.tuktukbuddy.repository.TripRepository;
import com.example.tuktukbuddy.repository.UserRepository;
import com.example.tuktukbuddy.util.LiveTripPolicy;
import com.example.tuktukbuddy.util.TripLifecycleUtils;
import com.example.tuktukbuddy.util.TripTimeUtils;
import com.example.tuktukbuddy.util.UserPreferenceManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.functions.FirebaseFunctionsException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class HomeActivity extends AppCompatActivity {
    private static final String TAG = "HomeActivity";

    private TextView tvHomeWelcome;
    private TextView tvHomeRollNo;
    private TextView tvHomeRating;

    private UserPreferenceManager preferenceManager;
    private UserRepository userRepository;
    private RideRequestRepository rideRequestRepository;

    private MaterialCardView cardLiveTrips;
    private MaterialCardView cardMyUpcomingTrips;
    private LinearLayout layoutLiveTrips;
    private LinearLayout layoutMyUpcomingTrips;
    private LinearLayout layoutTripNotifications;
    private TextView tvTripNotificationsTitle;
    private TextView tvLiveTripError;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Map<String, Trip> creatorTrips = new LinkedHashMap<>();
    private final Map<String, Trip> creatorAcceptedTrips = new LinkedHashMap<>();
    private final Map<String, Map<String, RideRequest>> creatorAcceptedRequests = new HashMap<>();
    private final Map<String, Map<String, RideRequest>> creatorPendingRequests = new HashMap<>();
    private final Map<String, RideRequest> requesterAcceptedRequests = new LinkedHashMap<>();
    private final Map<String, Trip> requesterTrips = new HashMap<>();
    private final Map<String, ListenerRegistration> requesterTripListeners = new HashMap<>();
    private final Map<String, ListenerRegistration> creatorAcceptedTripListeners = new HashMap<>();
    private final Set<String> cancellationInProgress = new LinkedHashSet<>();
    private final Set<String> tripCancellationInProgress = new LinkedHashSet<>();
    private final Set<String> completionInProgress = new LinkedHashSet<>();
    private final Set<String> lifecycleTransitionInProgress = new LinkedHashSet<>();
    private final Set<String> liveListenerErrors = new LinkedHashSet<>();
    private final Set<String> requesterTripErrors = new LinkedHashSet<>();
    private final Map<String, Integer> listenerAttempts = new HashMap<>();
    private final Map<String, Integer> listenerRetryCounts = new HashMap<>();
    private final Map<String, Runnable> listenerRetries = new HashMap<>();
    private List<DocumentSnapshot> tripNotifications = new ArrayList<>();

    private ListenerRegistration creatorTripsListener;
    private ListenerRegistration creatorRequestsListener;
    private ListenerRegistration requesterRequestsListener;
    private ListenerRegistration notificationsListener;
    private boolean liveListenersActive;
    private boolean activityStarted;
    private boolean creatorRequestsLoaded;
    private boolean creatorTripsLoaded;
    private int liveListenerGeneration;

    private final Runnable liveTripRefresh = new Runnable() {
        @Override
        public void run() {
            renderLiveTrips();
            mainHandler.postDelayed(this, nextLiveTripRefreshDelay());
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        preferenceManager = new UserPreferenceManager(this);
        userRepository = new UserRepository();
        rideRequestRepository = new RideRequestRepository();

        tvHomeWelcome = findViewById(R.id.tvHomeWelcome);
        tvHomeRollNo = findViewById(R.id.tvHomeRollNo);
        tvHomeRating = findViewById(R.id.tvHomeRating);

        Button btnPostTrip = findViewById(R.id.btnPostTrip);
        Button btnBrowseTrips = findViewById(R.id.btnBrowseTrips);
        Button btnMyRequests = findViewById(R.id.btnMyRequests);
        Button btnManageRequests = findViewById(R.id.btnManageRequests);
        Button btnResetProfile = findViewById(R.id.btnResetProfile);
        Button btnTripHistory = findViewById(R.id.btnTripHistory);

        cardLiveTrips = findViewById(R.id.cardLiveTrips);
        cardMyUpcomingTrips = findViewById(R.id.cardMyUpcomingTrips);
        layoutLiveTrips = findViewById(R.id.layoutLiveTrips);
        layoutMyUpcomingTrips = findViewById(R.id.layoutMyUpcomingTrips);
        layoutTripNotifications = findViewById(R.id.layoutTripNotifications);
        tvTripNotificationsTitle = findViewById(R.id.tvTripNotificationsTitle);
        tvLiveTripError = findViewById(R.id.tvLiveTripError);

        loadAndDisplayUser();

        btnPostTrip.setOnClickListener(v -> {
            Intent intent = new Intent(HomeActivity.this, PostTripActivity.class);
            startActivity(intent);
        });

        btnBrowseTrips.setOnClickListener(v -> {
            Intent intent = new Intent(HomeActivity.this, BrowseTripsActivity.class);
            startActivity(intent);
        });

        btnMyRequests.setOnClickListener(v -> {
            Intent intent = new Intent(HomeActivity.this, MyRequestsActivity.class);
            startActivity(intent);
        });

        btnTripHistory.setOnClickListener(v ->
                startActivity(new Intent(HomeActivity.this, TripHistoryActivity.class)));

        btnManageRequests.setOnClickListener(v -> {
            Intent intent = new Intent(HomeActivity.this, ManageRequestsActivity.class);
            startActivity(intent);
        });

        btnResetProfile.setOnClickListener(v -> resetProfileForOnboarding());
    }

    @Override
    protected void onStart() {
        super.onStart();
        activityStarted = true;
        startLiveTripListeners();
        mainHandler.post(liveTripRefresh);
    }

    @Override
    protected void onStop() {
        activityStarted = false;
        mainHandler.removeCallbacks(liveTripRefresh);
        stopLiveTripListeners();
        super.onStop();
    }

    private void loadAndDisplayUser() {
        User user = preferenceManager.getUser();
        if (user != null) {
            tvHomeWelcome.setText(getString(R.string.home_welcome_title, user.getName()));
            tvHomeRollNo.setText(getString(R.string.home_roll_no, user.getRollNo()));
            tvHomeRating.setText(getString(R.string.home_rating, user.getAvgRating()));
        } else {
            // Fallback if preferences are missing
            String uid = userRepository.getCurrentFirebaseUser() != null ? userRepository.getCurrentFirebaseUser().getUid() : null;
            if (uid != null) {
                userRepository.fetchUserProfile(uid, new UserRepository.ProfileCallback() {
                    @Override
                    public void onSuccess(User fetchedUser) {
                        if (isFinishing() || isDestroyed()) return;
                        if (fetchedUser != null) {
                            preferenceManager.saveUser(fetchedUser);
                            tvHomeWelcome.setText(getString(R.string.home_welcome_title, fetchedUser.getName()));
                            tvHomeRollNo.setText(getString(R.string.home_roll_no, fetchedUser.getRollNo()));
                            tvHomeRating.setText(getString(R.string.home_rating, fetchedUser.getAvgRating()));
                        } else {
                            redirectToOnboarding();
                        }
                    }

                    @Override
                    public void onFailure(Exception e) {
                        if (isFinishing() || isDestroyed()) return;
                        Toast.makeText(HomeActivity.this, "Error loading user profile: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
            } else {
                redirectToOnboarding();
            }
        }
    }

    private void resetProfileForOnboarding() {
        preferenceManager.clearProfileForReset();
        userRepository.signOut();
        Toast.makeText(this, "Profile reset and signed out.", Toast.LENGTH_SHORT).show();
        redirectToOnboarding();
    }

    private void startLiveTripListeners() {
        if (liveListenersActive) return;

        FirebaseUser currentUser = userRepository.getCurrentFirebaseUser();
        String uid = currentUser != null ? currentUser.getUid() : null;
        if (uid == null || uid.trim().isEmpty()) {
            Log.w(TAG, "Home Firestore listeners not started; authenticatedUidPresent=false");
            cardLiveTrips.setVisibility(View.GONE);
            return;
        }

        // Log only whether a Firebase UID is available; never log the UID itself.
        Log.d(TAG, "Home Firestore listeners starting; authenticatedUidPresent=true");
        liveListenersActive = true;
        creatorRequestsLoaded = false;
        creatorTripsLoaded = false;
        final int generation = ++liveListenerGeneration;
        FirebaseFirestore firestore = FirebaseFirestore.getInstance();
        attachCreatorTripsListener(firestore, uid, generation);
        attachCreatorRequestsListener(firestore, uid, generation);
        attachRequesterRequestsListener(firestore, uid, generation);
        attachNotificationsListener(firestore, uid, generation);
    }

    private void attachCreatorTripsListener(FirebaseFirestore firestore, String uid, int generation) {
        if (!isCurrentLiveListener(generation) || creatorTripsListener != null) return;
        final String key = "creatorTrips";
        final int attempt = nextListenerAttempt(key);
        creatorTripsListener = firestore.collection("trips")
                .whereEqualTo("creatorUid", uid)
                .whereIn("lifecycleStatus", Arrays.asList(
                        TripLifecycleUtils.SCHEDULED, TripLifecycleUtils.IN_PROGRESS))
                .addSnapshotListener((snapshots, error) -> {
                    if (!isCurrentListenerAttempt(generation, key, attempt)) return;
                    if (error != null || snapshots == null) {
                        invalidateListenerAttempt(key, attempt);
                        failLiveListener(key, creatorTripsListener, error, null, null);
                        creatorTripsListener = null;
                        creatorTrips.clear();
                        creatorTripsLoaded = false;
                        reconcileCreatorAcceptedTripListeners(firestore);
                        renderLiveTrips();
                        scheduleListenerRetry(key, () -> attachCreatorTripsListener(firestore, uid, generation));
                        return;
                    }
                    creatorTrips.clear();
                    for (QueryDocumentSnapshot document : snapshots) {
                        Trip trip = document.toObject(Trip.class);
                        LiveTripPolicy.addTripByDocumentId(creatorTrips, document.getId(), trip);
                        persistInProgressIfDue(document.getId(), trip);
                    }
                    creatorTripsLoaded = true;
                    reconcileCreatorAcceptedTripListeners(firestore);
                    recoverLiveListener(key);
                    renderLiveTrips();
                });
    }

    private void persistInProgressIfDue(String tripId, Trip trip) {
        if (trip == null || tripId == null
                || !TripLifecycleUtils.IN_PROGRESS.equals(
                TripLifecycleUtils.getDisplayStatus(trip, System.currentTimeMillis()))
                || TripLifecycleUtils.IN_PROGRESS.equalsIgnoreCase(trip.getLifecycleStatus())
                || TripLifecycleUtils.isTerminal(trip)
                || !lifecycleTransitionInProgress.add(tripId)) return;

        new TripRepository().markTripInProgressIfDue(tripId, new TripRepository.ActionCallback() {
            @Override
            public void onSuccess() {
                lifecycleTransitionInProgress.remove(tripId);
                if (isFinishing() || isDestroyed()) return;
                if (!TripLifecycleUtils.isTerminal(trip)) {
                    trip.setLifecycleStatus(TripLifecycleUtils.IN_PROGRESS);
                    creatorTrips.put(tripId, trip);
                    renderLiveTrips();
                }
            }

            @Override
            public void onFailure(Exception e) {
                lifecycleTransitionInProgress.remove(tripId);
                Log.w(TAG, "Could not persist IN_PROGRESS lifecycle. tripId=" + tripId, e);
            }
        });
    }

    /** Fetch accepted legacy trips individually without querying completed-trip history. */
    private void reconcileCreatorAcceptedTripListeners(FirebaseFirestore firestore) {
        if (!creatorTripsLoaded || !isActiveActivity()) return;
        Set<String> requiredTripIds = new LinkedHashSet<>();
        for (String tripId : creatorAcceptedRequests.keySet()) {
            if (!creatorTrips.containsKey(tripId)) requiredTripIds.add(tripId);
        }

        List<String> obsoleteIds = new ArrayList<>();
        for (String tripId : creatorAcceptedTripListeners.keySet()) {
            if (!requiredTripIds.contains(tripId)) obsoleteIds.add(tripId);
        }
        for (String tripId : obsoleteIds) {
            ListenerRegistration listener = creatorAcceptedTripListeners.remove(tripId);
            if (listener != null) listener.remove();
            creatorAcceptedTrips.remove(tripId);
            String key = "creatorAcceptedTrip:" + tripId;
            Runnable retry = listenerRetries.remove(key);
            if (retry != null) mainHandler.removeCallbacks(retry);
            listenerAttempts.remove(key);
            listenerRetryCounts.remove(key);
            liveListenerErrors.remove(key);
        }

        for (String tripId : requiredTripIds) {
            if (!creatorAcceptedTripListeners.containsKey(tripId)) {
                attachCreatorAcceptedTripListener(firestore, tripId, liveListenerGeneration);
            }
        }
    }

    private void attachCreatorAcceptedTripListener(FirebaseFirestore firestore, String tripId, int generation) {
        final String key = "creatorAcceptedTrip:" + tripId;
        if (!isCurrentLiveListener(generation) || creatorAcceptedTripListeners.containsKey(tripId)
                || !creatorAcceptedRequests.containsKey(tripId)) return;
        final int attempt = nextListenerAttempt(key);
        final ListenerRegistration[] registration = new ListenerRegistration[1];
        registration[0] = firestore.collection("trips").document(tripId)
                .addSnapshotListener((snapshot, error) -> {
                    if (!isCurrentListenerAttempt(generation, key, attempt)
                            || !creatorAcceptedTripListeners.containsKey(tripId)) return;
                    if (error != null || snapshot == null) {
                        invalidateListenerAttempt(key, attempt);
                        failLiveListener(key, registration[0], error, tripId,
                                firstCreatorAcceptedRequestId(tripId));
                        creatorAcceptedTripListeners.remove(tripId);
                        if (registration[0] != null) registration[0].remove();
                        creatorAcceptedTrips.remove(tripId);
                        renderLiveTrips();
                        scheduleListenerRetry(key, () -> attachCreatorAcceptedTripListener(
                                firestore, tripId, generation));
                        return;
                    }
                    if (!snapshot.exists()) {
                        creatorAcceptedTrips.remove(tripId);
                    } else {
                        Trip trip = snapshot.toObject(Trip.class);
                        if (trip != null && !TripLifecycleUtils.isTerminal(trip)) {
                            trip.setTripId(snapshot.getId());
                            creatorAcceptedTrips.put(tripId, trip);
                        } else {
                            creatorAcceptedTrips.remove(tripId);
                        }
                    }
                    recoverLiveListener(key);
                    renderLiveTrips();
                });
        creatorAcceptedTripListeners.put(tripId, registration[0]);
    }

    private String firstCreatorAcceptedRequestId(String tripId) {
        Map<String, RideRequest> requests = creatorAcceptedRequests.get(tripId);
        if (requests == null || requests.isEmpty()) return null;
        return requests.values().iterator().next().getRequestId();
    }

    private Trip creatorTripFor(String tripId) {
        Trip trip = creatorTrips.get(tripId);
        return trip != null ? trip : creatorAcceptedTrips.get(tripId);
    }

    private void attachCreatorRequestsListener(FirebaseFirestore firestore, String uid, int generation) {
        if (!isCurrentLiveListener(generation) || creatorRequestsListener != null) return;
        final String key = "creatorRequests";
        final int attempt = nextListenerAttempt(key);
        creatorRequestsListener = firestore.collection("rideRequests")
                .whereEqualTo("creatorUid", uid)
                .whereIn("status", Arrays.asList("PENDING", "ACCEPTED"))
                .addSnapshotListener((snapshots, error) -> {
                    if (!isCurrentListenerAttempt(generation, key, attempt)) return;
                    if (error != null || snapshots == null) {
                        invalidateListenerAttempt(key, attempt);
                        failLiveListener(key, creatorRequestsListener, error, null, null);
                        creatorRequestsListener = null;
                        creatorAcceptedRequests.clear();
                        creatorPendingRequests.clear();
                        creatorRequestsLoaded = false;
                        reconcileCreatorAcceptedTripListeners(firestore);
                        renderLiveTrips();
                        scheduleListenerRetry(key, () -> attachCreatorRequestsListener(firestore, uid, generation));
                        return;
                    }
                    creatorAcceptedRequests.clear();
                    creatorPendingRequests.clear();
                    for (QueryDocumentSnapshot document : snapshots) {
                        RideRequest request = toRideRequest(document);
                        if (request.getTripId() == null) continue;
                        if (LiveTripPolicy.isAcceptedRequest(request)) {
                            LiveTripPolicy.addRequestByDocumentId(
                                    creatorAcceptedRequests.computeIfAbsent(
                                            request.getTripId(), ignored -> new LinkedHashMap<>()),
                                    document.getId(), request);
                        } else if ("PENDING".equalsIgnoreCase(request.getStatus())) {
                            LiveTripPolicy.addRequestByDocumentId(
                                    creatorPendingRequests.computeIfAbsent(
                                            request.getTripId(), ignored -> new LinkedHashMap<>()),
                                    document.getId(), request);
                        }
                    }
                    creatorRequestsLoaded = true;
                    reconcileCreatorAcceptedTripListeners(firestore);
                    recoverLiveListener(key);
                    renderLiveTrips();
                });
    }

    private void attachRequesterRequestsListener(FirebaseFirestore firestore, String uid, int generation) {
        if (!isCurrentLiveListener(generation) || requesterRequestsListener != null) return;
        final String key = "requesterRequests";
        final int attempt = nextListenerAttempt(key);
        requesterRequestsListener = firestore.collection("rideRequests")
                .whereEqualTo("requesterUid", uid)
                .whereEqualTo("status", "ACCEPTED")
                .addSnapshotListener((snapshots, error) -> {
                    if (!isCurrentListenerAttempt(generation, key, attempt)) return;
                    if (error != null || snapshots == null) {
                        invalidateListenerAttempt(key, attempt);
                        failLiveListener(key, requesterRequestsListener, error, null, null);
                        requesterRequestsListener = null;
                        requesterAcceptedRequests.clear();
                        clearRequesterTripListeners();
                        requesterTrips.clear();
                        requesterTripErrors.clear();
                        renderLiveTrips();
                        scheduleListenerRetry(key, () -> attachRequesterRequestsListener(firestore, uid, generation));
                        return;
                    }
                    requesterAcceptedRequests.clear();
                    for (QueryDocumentSnapshot document : snapshots) {
                        RideRequest request = toRideRequest(document);
                        if (LiveTripPolicy.isAcceptedRequest(request)) {
                            LiveTripPolicy.addRequestByDocumentId(
                                    requesterAcceptedRequests, document.getId(), request);
                        }
                    }
                    reconcileRequesterTripListeners(firestore);
                    recoverLiveListener(key);
                    renderLiveTrips();
                });
    }

    private void attachNotificationsListener(FirebaseFirestore firestore, String uid, int generation) {
        if (!isCurrentLiveListener(generation) || notificationsListener != null) return;
        final String key = "notifications";
        final int attempt = nextListenerAttempt(key);
        notificationsListener = firestore.collection("tripNotifications")
                .whereEqualTo("recipientUid", uid)
                .addSnapshotListener((snapshots, error) -> {
                    if (!isCurrentListenerAttempt(generation, key, attempt)) return;
                    if (error != null || snapshots == null) {
                        invalidateListenerAttempt(key, attempt);
                        Log.e(TAG, "Home listener failed operation=loadTripNotifications"
                                + " collection=tripNotifications query=recipientUid"
                                + " exceptionClass=" + (error != null
                                ? error.getClass().getSimpleName() : "NO_SNAPSHOT")
                                + " code=" + firebaseErrorCode(error), error);
                        failLiveListener(key, notificationsListener, error, null, null);
                        notificationsListener = null;
                        tripNotifications.clear();
                        renderLiveTrips();
                        scheduleListenerRetry(key, () -> attachNotificationsListener(firestore, uid, generation));
                        return;
                    }
                    tripNotifications = new ArrayList<>(snapshots.getDocuments());
                    recoverLiveListener(key);
                    renderLiveTrips();
                });
    }

    private void stopLiveTripListeners() {
        liveListenersActive = false;
        liveListenerGeneration++;
        for (Runnable retry : listenerRetries.values()) {
            mainHandler.removeCallbacks(retry);
        }
        listenerRetries.clear();
        removeListener(creatorTripsListener);
        removeListener(creatorRequestsListener);
        removeListener(requesterRequestsListener);
        removeListener(notificationsListener);
        for (ListenerRegistration listener : creatorAcceptedTripListeners.values()) {
            if (listener != null) listener.remove();
        }
        creatorAcceptedTripListeners.clear();
        creatorTripsListener = null;
        creatorRequestsListener = null;
        requesterRequestsListener = null;
        notificationsListener = null;

        clearRequesterTripListeners();
        creatorTrips.clear();
        creatorAcceptedTrips.clear();
        creatorTripsLoaded = false;
        creatorAcceptedRequests.clear();
        creatorPendingRequests.clear();
        creatorRequestsLoaded = false;
        requesterAcceptedRequests.clear();
        requesterTrips.clear();
        tripNotifications.clear();
        cancellationInProgress.clear();
        tripCancellationInProgress.clear();
        completionInProgress.clear();
        lifecycleTransitionInProgress.clear();
        liveListenerErrors.clear();
        requesterTripErrors.clear();
        listenerAttempts.clear();
        listenerRetryCounts.clear();
        if (layoutLiveTrips != null) layoutLiveTrips.removeAllViews();
        if (layoutMyUpcomingTrips != null && layoutMyUpcomingTrips.getChildCount() > 1) {
            layoutMyUpcomingTrips.removeViews(1, layoutMyUpcomingTrips.getChildCount() - 1);
        }
        if (layoutTripNotifications != null) layoutTripNotifications.removeAllViews();
        if (tvTripNotificationsTitle != null) tvTripNotificationsTitle.setVisibility(View.GONE);
        if (cardLiveTrips != null) cardLiveTrips.setVisibility(View.GONE);
        if (cardMyUpcomingTrips != null) cardMyUpcomingTrips.setVisibility(View.GONE);
    }

    private void removeListener(ListenerRegistration listener) {
        if (listener != null) listener.remove();
    }

    private void clearRequesterTripListeners() {
        for (Map.Entry<String, ListenerRegistration> entry : requesterTripListeners.entrySet()) {
            entry.getValue().remove();
            String key = "requesterTrip:" + entry.getKey();
            Runnable retry = listenerRetries.remove(key);
            if (retry != null) mainHandler.removeCallbacks(retry);
            listenerAttempts.remove(key);
            listenerRetryCounts.remove(key);
            liveListenerErrors.remove(key);
        }
        requesterTripListeners.clear();
        requesterTripErrors.clear();
    }

    private boolean isActiveActivity() {
        return liveListenersActive && !isFinishing() && !isDestroyed();
    }

    private boolean isCurrentLiveListener(int generation) {
        return isActiveActivity() && generation == liveListenerGeneration;
    }

    private int nextListenerAttempt(String listenerKey) {
        int attempt = listenerAttempts.containsKey(listenerKey) ? listenerAttempts.get(listenerKey) + 1 : 1;
        listenerAttempts.put(listenerKey, attempt);
        return attempt;
    }

    private boolean isCurrentListenerAttempt(int generation, String listenerKey, int attempt) {
        Integer currentAttempt = listenerAttempts.get(listenerKey);
        return isCurrentLiveListener(generation) && currentAttempt != null && currentAttempt == attempt;
    }

    private void invalidateListenerAttempt(String listenerKey, int attempt) {
        Integer currentAttempt = listenerAttempts.get(listenerKey);
        if (currentAttempt != null && currentAttempt == attempt) {
            listenerAttempts.put(listenerKey, attempt + 1);
        }
    }

    private void failLiveListener(String listenerKey, ListenerRegistration registration,
                                  Exception error, String tripId, String requestId) {
        removeListener(registration);
        liveListenerErrors.add(listenerKey);
        if (tripId != null) requesterTripErrors.add(tripId);
        String code = error instanceof FirebaseFirestoreException
                ? ((FirebaseFirestoreException) error).getCode().name()
                : error != null ? error.getClass().getSimpleName() : "NO_SNAPSHOT";
        String message = error != null && error.getMessage() != null
                ? error.getMessage() : "Listener returned no snapshot.";
        Log.e(TAG, "Live Trips listener failed. type=" + listenerKey
                + " code=" + code
                + (tripId != null ? " tripId=" + tripId : "")
                + (requestId != null ? " requestId=" + requestId : "")
                + " message=" + message);
    }

    private void recoverLiveListener(String listenerKey) {
        liveListenerErrors.remove(listenerKey);
        listenerRetryCounts.remove(listenerKey);
        if (listenerKey.startsWith("requesterTrip:")) {
            requesterTripErrors.remove(listenerKey.substring("requesterTrip:".length()));
        }
    }

    private void scheduleListenerRetry(String listenerKey, Runnable retryAction) {
        if (listenerRetries.containsKey(listenerKey)) return;
        int retryCount = listenerRetryCounts.containsKey(listenerKey)
                ? listenerRetryCounts.get(listenerKey) + 1 : 1;
        listenerRetryCounts.put(listenerKey, retryCount);
        long retryDelayMillis = Math.min(5_000L << Math.min(retryCount - 1, 4), 60_000L);
        Runnable retry = () -> {
            listenerRetries.remove(listenerKey);
            if (isActiveActivity()) retryAction.run();
        };
        listenerRetries.put(listenerKey, retry);
        mainHandler.postDelayed(retry, retryDelayMillis);
    }

    private void updateListenerErrorView() {
        boolean hasErrors = false;
        for (String error : liveListenerErrors) {
            if (!"notifications".equals(error)) {
                hasErrors = true;
                break;
            }
        }
        tvLiveTripError.setVisibility(hasErrors ? View.VISIBLE : View.GONE);
        if (hasErrors) {
            tvLiveTripError.setText(R.string.home_live_trip_listener_error);
        }
    }

    private long nextLiveTripRefreshDelay() {
        long now = System.currentTimeMillis();
        long nextRefresh = now + 30_000L;
        for (Trip trip : creatorTrips.values()) {
            Date departure = TripTimeUtils.getVerifiedDepartureAt(trip);
            if (departure != null && departure.getTime() > now) {
                nextRefresh = Math.min(nextRefresh, departure.getTime());
            }
        }
        for (RideRequest request : requesterAcceptedRequests.values()) {
            Trip trip = requesterTrips.get(request.getTripId());
            Date departure = TripTimeUtils.getVerifiedDepartureAt(trip);
            if (departure != null) {
                long cutoff = departure.getTime() - TripTimeUtils.CANCELLATION_CUTOFF_MILLIS;
                if (cutoff > now) nextRefresh = Math.min(nextRefresh, cutoff);
                if (departure.getTime() > now) nextRefresh = Math.min(nextRefresh, departure.getTime());
            }
        }
        return Math.max(1L, nextRefresh - now);
    }

    private RideRequest toRideRequest(QueryDocumentSnapshot document) {
        RideRequest request = document.toObject(RideRequest.class);
        request.setRequestId(document.getId());
        return request;
    }

    private void reconcileRequesterTripListeners(FirebaseFirestore firestore) {
        Set<String> requiredTripIds = new LinkedHashSet<>();
        for (RideRequest request : requesterAcceptedRequests.values()) {
            if (request.getTripId() != null && !request.getTripId().isEmpty()) {
                requiredTripIds.add(request.getTripId());
            }
        }

        List<String> obsoleteIds = new ArrayList<>();
        for (String tripId : requesterTripListeners.keySet()) {
            if (!requiredTripIds.contains(tripId)) obsoleteIds.add(tripId);
        }
        for (String tripId : obsoleteIds) {
            requesterTripListeners.remove(tripId).remove();
            requesterTrips.remove(tripId);
            requesterTripErrors.remove(tripId);
            String retryKey = "requesterTrip:" + tripId;
            Runnable retry = listenerRetries.remove(retryKey);
            if (retry != null) mainHandler.removeCallbacks(retry);
            listenerAttempts.remove(retryKey);
            listenerRetryCounts.remove(retryKey);
            liveListenerErrors.remove(retryKey);
        }

        for (String tripId : requiredTripIds) {
            if (requesterTripListeners.containsKey(tripId)) continue;
            attachRequesterTripListener(firestore, tripId, liveListenerGeneration);
        }
    }

    private void attachRequesterTripListener(FirebaseFirestore firestore, String tripId, int generation) {
        String key = "requesterTrip:" + tripId;
        if (!isCurrentLiveListener(generation)
                || requesterTripListeners.containsKey(tripId)
                || !isRequesterTripRequired(tripId)) return;
        final int attempt = nextListenerAttempt(key);
        ListenerRegistration listener = firestore.collection("trips").document(tripId)
                .addSnapshotListener((snapshot, error) -> {
                    if (!isCurrentListenerAttempt(generation, key, attempt)
                            || !requesterTripListeners.containsKey(tripId)) return;
                    if (error != null || snapshot == null) {
                        invalidateListenerAttempt(key, attempt);
                        failLiveListener(key, requesterTripListeners.get(tripId), error,
                                tripId, firstRequesterIdForTrip(tripId));
                        requesterTripListeners.remove(tripId);
                        requesterTrips.remove(tripId);
                        requesterTripErrors.add(tripId);
                        renderLiveTrips();
                        scheduleListenerRetry(key, () -> attachRequesterTripListener(
                                firestore, tripId, generation));
                        return;
                    }
                    if (!snapshot.exists()) {
                        requesterTrips.remove(tripId);
                    } else {
                        Trip trip = snapshot.toObject(Trip.class);
                        if (trip != null) {
                            if (trip.getTripId() == null || trip.getTripId().isEmpty()) {
                                trip.setTripId(snapshot.getId());
                            }
                            trip.setTripId(snapshot.getId());
                            requesterTrips.put(tripId, trip);
                        }
                    }
                    recoverLiveListener(key);
                    requesterTripErrors.remove(tripId);
                    renderLiveTrips();
                });
        requesterTripListeners.put(tripId, listener);
    }

    private boolean isRequesterTripRequired(String tripId) {
        for (RideRequest request : requesterAcceptedRequests.values()) {
            if (tripId.equals(request.getTripId())) return true;
        }
        return false;
    }

    private String firstRequesterIdForTrip(String tripId) {
        for (RideRequest request : requesterAcceptedRequests.values()) {
            if (tripId.equals(request.getTripId())) return request.getRequestId();
        }
        return null;
    }

    private void renderLiveTrips() {
        if (layoutLiveTrips == null || !isActiveActivity()) return;
        layoutLiveTrips.removeAllViews();
        long now = System.currentTimeMillis();
        FirebaseUser activeUser = userRepository.getCurrentFirebaseUser();
        String currentUid = activeUser != null ? activeUser.getUid() : null;

        Map<String, Trip> creatorLive = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, RideRequest>> entry : creatorAcceptedRequests.entrySet()) {
            Trip trip = creatorTripFor(entry.getKey());
            if (LiveTripPolicy.isLiveCreatorTrip(trip, entry.getValue().size(), now)) {
                creatorLive.put(entry.getKey(), trip);
            }
        }

        Map<String, RideRequest> requesterLive = new LinkedHashMap<>();
        for (RideRequest request : requesterAcceptedRequests.values()) {
            Trip trip = requesterTrips.get(request.getTripId());
            if (LiveTripPolicy.isLiveRequesterRide(request, trip, now)) {
                LiveTripPolicy.addRequesterRideByTripId(requesterLive, request);
            }
        }

        boolean hasLive = !creatorLive.isEmpty() || !requesterLive.isEmpty();
        if (hasLive) {
            layoutLiveTrips.addView(createLiveText(getString(R.string.home_live_trips_title),
                    16, true), liveTextLayoutParams(4));
            for (Map.Entry<String, Trip> entry : creatorLive.entrySet()) {
                addCreatorTripCard(layoutLiveTrips, entry.getKey(), entry.getValue(), now, currentUid);
            }
            for (RideRequest request : requesterLive.values()) {
                addRequesterRideCard(layoutLiveTrips, request, requesterTrips.get(request.getTripId()), now);
            }
        }
        renderTripNotifications();
        updateListenerErrorView();
        cardLiveTrips.setVisibility(hasLive || !tripNotifications.isEmpty()
                || liveListenerErrors.contains("notifications") || hasLiveListenerError()
                ? View.VISIBLE : View.GONE);

        renderMyUpcomingTrips(now, currentUid);
    }

    /** Posted trips with no accepted passengers stay out of Live Trips. */
    private void renderMyUpcomingTrips(long now, String currentUid) {
        if (layoutMyUpcomingTrips == null) return;
        if (!creatorRequestsLoaded) {
            cardMyUpcomingTrips.setVisibility(View.GONE);
            return;
        }
        if (layoutMyUpcomingTrips.getChildCount() > 1) {
            layoutMyUpcomingTrips.removeViews(1, layoutMyUpcomingTrips.getChildCount() - 1);
        }
        int count = 0;
        for (Map.Entry<String, Trip> entry : creatorTrips.entrySet()) {
            Trip trip = entry.getValue();
            Map<String, RideRequest> accepted = creatorAcceptedRequests.get(entry.getKey());
            int acceptedCount = accepted == null ? 0 : accepted.size();
            if (!LiveTripPolicy.isUpcomingCreatorTrip(trip, acceptedCount, now)) continue;

            count++;
            addTripDetails(layoutMyUpcomingTrips, trip, now);
            Map<String, RideRequest> pending = creatorPendingRequests.get(entry.getKey());
            int pendingCount = pending == null ? 0 : pending.size();
            layoutMyUpcomingTrips.addView(createLiveText(
                    getResources().getQuantityString(R.plurals.home_pending_request_count,
                            pendingCount, pendingCount), 13, false), liveTextLayoutParams(4));
            addTripCancelAction(layoutMyUpcomingTrips, entry.getKey(), trip, now, currentUid);
            addDivider(layoutMyUpcomingTrips);
        }
        cardMyUpcomingTrips.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
    }

    private boolean hasLiveListenerError() {
        for (String key : liveListenerErrors) {
            if (!"notifications".equals(key)) return true;
        }
        return false;
    }

    private void addCreatorTripCard(LinearLayout target, String tripId, Trip trip,
                                    long now, String currentUid) {
        addTripDetails(target, trip, now);
        target.addView(createLiveText(getString(R.string.home_live_trip_creator_label),
                14, true), liveTextLayoutParams(8));
        if (trip != null && !trip.isAvailableForBrowsing()) {
            target.addView(createLiveText(getString(R.string.home_trip_full_label),
                    13, false), liveTextLayoutParams(4));
        }
        Map<String, RideRequest> passengers = creatorAcceptedRequests.get(tripId);
        if (passengers != null) {
            target.addView(createLiveText(getString(R.string.home_accepted_passengers_label),
                    14, true), liveTextLayoutParams(8));
            for (RideRequest passenger : LiveTripPolicy.uniquePassengersByRequesterId(passengers.values())) {
                String name = passenger.getRequesterName() != null
                        ? passenger.getRequesterName() : "Passenger";
                target.addView(createLiveText(name + " — " + passenger.getStatus(),
                        14, false), liveTextLayoutParams(3));
            }
        }
        if (TripLifecycleUtils.canComplete(trip, currentUid, now)) {
            MaterialButton completeButton = new MaterialButton(this);
            completeButton.setText(R.string.home_trip_mark_completed);
            completeButton.setEnabled(!completionInProgress.contains(tripId));
            target.addView(completeButton, liveTextLayoutParams(8));
            completeButton.setOnClickListener(v -> confirmCompleteTrip(trip));
        }
        addTripCancelAction(target, tripId, trip, now, currentUid);
        addDivider(target);
    }

    private void addTripCancelAction(LinearLayout target, String tripId, Trip trip,
                                    long now, String currentUid) {
        MaterialButton cancelTripButton = new MaterialButton(this);
        boolean cancelInProgress = tripCancellationInProgress.contains(tripId);
        cancelTripButton.setText(cancelInProgress
                ? R.string.home_trip_cancelling : R.string.home_trip_cancel);
        cancelTripButton.setEnabled(!cancelInProgress
                && TripLifecycleUtils.canCancel(trip, currentUid, now));
        target.addView(cancelTripButton, liveTextLayoutParams(8));
        if (trip != null) cancelTripButton.setOnClickListener(v -> confirmCancelTrip(trip));
        String cancelDisabledReason = tripCancellationDisabledReason(trip, currentUid, now);
        if (cancelDisabledReason != null) {
            target.addView(createLiveText(cancelDisabledReason, 13, false), liveTextLayoutParams(2));
        }
    }

    private String tripCancellationDisabledReason(Trip trip, String currentUid, long now) {
        if (trip == null) return getString(R.string.home_trip_cancel_details_unavailable);
        if (currentUid == null || !currentUid.equals(trip.getCreatorUid())) {
            return getString(R.string.home_trip_cancel_creator_only);
        }
        if (trip.getDepartureAt() == null) {
            return getString(R.string.home_trip_cancel_time_unavailable);
        }
        if (TripLifecycleUtils.isTerminal(trip)
                || TripLifecycleUtils.IN_PROGRESS.equalsIgnoreCase(trip.getLifecycleStatus())
                || now >= trip.getDepartureAt().getTime()) {
            return getString(R.string.home_trip_cancel_departed);
        }
        if (trip.getLifecycleStatus() != null
                && !TripLifecycleUtils.SCHEDULED.equalsIgnoreCase(trip.getLifecycleStatus())) {
            return getString(R.string.home_trip_cancel_lifecycle_unavailable);
        }
        return null;
    }

    private void addRequesterRideCard(LinearLayout target, RideRequest request, Trip trip, long now) {
        addTripDetails(target, trip, now);
        target.addView(createLiveText(getString(R.string.home_live_trip_requester_label),
                14, true), liveTextLayoutParams(8));
        if (trip != null && !trip.isAvailableForBrowsing()) {
            target.addView(createLiveText(getString(R.string.home_trip_full_label),
                    13, false), liveTextLayoutParams(4));
        }
        target.addView(createLiveText(getString(R.string.home_request_status_label, request.getStatus()),
                14, false), liveTextLayoutParams(8));
        String disabledReason = trip != null
                ? TripTimeUtils.cancellationDisabledReason(trip, now)
                : "Trip details could not be loaded. Cancellation is unavailable.";
        boolean inProgress = cancellationInProgress.contains(request.getRequestId());
        MaterialButton cancelButton = new MaterialButton(this);
        cancelButton.setText(inProgress ? "Cancelling…" : getString(R.string.home_cancel_request));
        cancelButton.setEnabled(trip != null && disabledReason == null && !inProgress);
        target.addView(cancelButton, liveTextLayoutParams(8));
        if (trip != null) cancelButton.setOnClickListener(v -> confirmCancelRequest(request, trip));
        if (disabledReason != null) {
            target.addView(createLiveText(disabledReason, 13, false), liveTextLayoutParams(2));
        }
        addDivider(target);
    }

    private void addTripDetails(LinearLayout target, Trip trip, long now) {
        if (trip == null) {
            target.addView(createLiveText("Trip details are temporarily unavailable. Retrying…",
                    14, false), liveTextLayoutParams(4));
            target.addView(createLiveText(getString(R.string.home_trip_lifecycle_unknown),
                    13, false), liveTextLayoutParams(4));
            return;
        }
        String route = trip.getPickup() != null ? trip.getPickup() : "?";
        route += "  →  " + (trip.getDestination() != null ? trip.getDestination() : "?");
        target.addView(createLiveText(route, 17, true), liveTextLayoutParams(4));
        String displayDate = TripTimeUtils.getDisplayDate(trip);
        String displayTime = TripTimeUtils.getDisplayTime(trip);
        String departure = displayDate != null && displayTime != null
                ? getString(R.string.trip_date_time_format, displayDate, displayTime)
                : "Departure date/time unavailable";
        target.addView(createLiveText(departure, 14, false), liveTextLayoutParams(4));
        target.addView(createLiveText(getString(R.string.home_trip_status_label,
                trip.getStatus() != null ? trip.getStatus() : "UNKNOWN"), 13, false), liveTextLayoutParams(4));
        String lifecycle = TripLifecycleUtils.getDisplayStatus(trip, now);
        target.addView(createLiveText(getString(R.string.home_trip_lifecycle_label, lifecycle),
                13, false), liveTextLayoutParams(4));
        if (TripLifecycleUtils.UNKNOWN.equals(lifecycle)) {
            target.addView(createLiveText(getString(R.string.home_trip_lifecycle_unknown),
                    13, false), liveTextLayoutParams(2));
        }
    }

    private void addDivider(LinearLayout target) {
        View divider = new View(this);
        divider.setBackgroundColor(getColor(R.color.surface_variant));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1));
        params.topMargin = dp(12);
        target.addView(divider, params);
    }

    private void renderTripNotifications() {
        layoutTripNotifications.removeAllViews();
        if (liveListenerErrors.contains("notifications")) {
            TextView warning = createLiveText(
                    getString(R.string.home_live_trip_listener_error), 13, false);
            warning.setTextColor(getColor(R.color.status_rejected));
            layoutTripNotifications.addView(warning, liveTextLayoutParams(4));
        }
        Collections.sort(tripNotifications, Comparator.comparingLong(this::notificationTime).reversed());
        for (DocumentSnapshot notification : tripNotifications) {
            String message = notification.getString("message");
            if (message != null && !message.trim().isEmpty()) {
                layoutTripNotifications.addView(createLiveText(message, 14, false), liveTextLayoutParams(6));
            }
        }
        tvTripNotificationsTitle.setVisibility(layoutTripNotifications.getChildCount() > 0
                ? View.VISIBLE : View.GONE);
    }

    private long notificationTime(DocumentSnapshot snapshot) {
        com.google.firebase.Timestamp timestamp = snapshot.getTimestamp("createdAt");
        return timestamp != null ? timestamp.toDate().getTime() : 0L;
    }

    private TextView createLiveText(String text, int textSize, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(textSize);
        view.setTextColor(getColor(bold ? R.color.on_surface : R.color.on_surface_variant));
        if (bold) view.setTypeface(view.getTypeface(), android.graphics.Typeface.BOLD);
        return view;
    }

    private LinearLayout.LayoutParams liveTextLayoutParams(int topMarginDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(topMarginDp);
        return params;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void confirmCancelRequest(RideRequest request, Trip trip) {
        String disabledReason = TripTimeUtils.cancellationDisabledReason(trip, System.currentTimeMillis());
        if (disabledReason != null) {
            Toast.makeText(this, disabledReason, Toast.LENGTH_LONG).show();
            renderLiveTrips();
            return;
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.home_cancel_request_title)
                .setMessage(R.string.home_cancel_request_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.home_cancel_request_confirm, (dialog, which) -> cancelRequest(request))
                .show();
    }

    private void cancelRequest(RideRequest request) {
        if (!cancellationInProgress.add(request.getRequestId())) return;
        renderLiveTrips();
        rideRequestRepository.cancelAcceptedRequest(request, new RideRequestRepository.ActionCallback() {
            @Override
            public void onSuccess() {
                cancellationInProgress.remove(request.getRequestId());
                Log.i(TAG, "Home operation succeeded operation=requesterCancel"
                        + " requestPath=/rideRequests/" + request.getRequestId());
                Toast.makeText(HomeActivity.this, "Your request was cancelled.", Toast.LENGTH_SHORT).show();
                renderLiveTrips();
            }

            @Override
            public void onFailure(Exception e) {
                cancellationInProgress.remove(request.getRequestId());
                logHomeOperationFailure("requesterCancel",
                        "requestPath=/rideRequests/" + request.getRequestId()
                                + " tripPath=/trips/" + request.getTripId()
                                + " notificationPath=/tripNotifications/" + request.getRequestId(), e);
                String message = e != null && e.getMessage() != null
                        ? e.getMessage() : "Could not cancel the request.";
                Toast.makeText(HomeActivity.this, message, Toast.LENGTH_LONG).show();
                renderLiveTrips();
            }
        });
    }

    private void confirmCompleteTrip(Trip trip) {
        FirebaseUser currentUser = userRepository.getCurrentFirebaseUser();
        String uid = currentUser != null ? currentUser.getUid() : null;
        if (!TripLifecycleUtils.canComplete(trip, uid, System.currentTimeMillis())) {
            Toast.makeText(this, "Only the trip creator can complete an in-progress trip.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.home_trip_complete_title)
                .setMessage(R.string.home_trip_complete_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.home_trip_complete_confirm,
                        (dialog, which) -> completeTrip(trip))
                .show();
    }

    private void confirmCancelTrip(Trip trip) {
        FirebaseUser currentUser = userRepository.getCurrentFirebaseUser();
        String uid = currentUser != null ? currentUser.getUid() : null;
        String disabledReason = tripCancellationDisabledReason(trip, uid, System.currentTimeMillis());
        if (disabledReason != null) {
            Toast.makeText(this, disabledReason, Toast.LENGTH_LONG).show();
            renderLiveTrips();
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.home_trip_cancel_title)
                .setMessage(R.string.home_trip_cancel_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.home_trip_cancel_confirm,
                        (dialog, which) -> cancelTrip(trip))
                .show();
    }

    private void cancelTrip(Trip trip) {
        String tripId = trip.getTripId();
        if (tripId == null || !tripCancellationInProgress.add(tripId)) return;
        renderLiveTrips();
        new TripRepository().cancelTrip(tripId, new TripRepository.ActionCallback() {
            @Override
            public void onSuccess() {
                tripCancellationInProgress.remove(tripId);
                Log.i(TAG, "Home operation succeeded operation=posterCancelFirestoreTransaction"
                        + " tripPath=/trips/" + tripId);
                trip.setStatus("CLOSED");
                trip.setLifecycleStatus(TripLifecycleUtils.CANCELLED);
                creatorTrips.put(tripId, trip);
                Toast.makeText(HomeActivity.this, "Trip cancelled. It is available in Trip History.",
                        Toast.LENGTH_LONG).show();
                renderLiveTrips();
            }

            @Override
            public void onFailure(Exception e) {
                tripCancellationInProgress.remove(tripId);
                logHomeOperationFailure("posterCancelFirestoreTransaction",
                        "tripPath=/trips/" + tripId, e);
                String message = e != null && e.getMessage() != null
                        ? e.getMessage() : "Could not cancel the trip.";
                Toast.makeText(HomeActivity.this, message, Toast.LENGTH_LONG).show();
                renderLiveTrips();
            }
        });
    }

    private void completeTrip(Trip trip) {
        String tripId = trip.getTripId();
        if (tripId == null || !completionInProgress.add(tripId)) return;
        renderLiveTrips();
        new TripRepository().markTripCompleted(tripId, new TripRepository.ActionCallback() {
            @Override
            public void onSuccess() {
                completionInProgress.remove(tripId);
                Log.i(TAG, "Home operation succeeded operation=markTripCompleted"
                        + " tripPath=/trips/" + tripId);
                trip.setLifecycleStatus(TripLifecycleUtils.COMPLETED);
                Toast.makeText(HomeActivity.this, "Trip marked completed.", Toast.LENGTH_SHORT).show();
                renderLiveTrips();
            }

            @Override
            public void onFailure(Exception e) {
                completionInProgress.remove(tripId);
                logHomeOperationFailure("markTripCompleted",
                        "tripPath=/trips/" + tripId, e);
                String message = e != null && e.getMessage() != null
                        ? e.getMessage() : "Could not complete the trip.";
                Toast.makeText(HomeActivity.this, message, Toast.LENGTH_LONG).show();
                renderLiveTrips();
            }
        });
    }

    private void redirectToOnboarding() {
        Intent intent = new Intent(HomeActivity.this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private String firebaseErrorCode(Exception error) {
        if (error instanceof FirebaseFirestoreException) {
            return ((FirebaseFirestoreException) error).getCode().name();
        }
        if (error instanceof FirebaseFunctionsException) {
            return ((FirebaseFunctionsException) error).getCode().name();
        }
        return error != null ? error.getClass().getSimpleName() : "NO_ERROR_OBJECT";
    }

    private void logHomeOperationFailure(String operation, String path, Exception error) {
        Log.e(TAG, "Home operation failed operation=" + operation
                + " exceptionClass=" + (error != null
                ? error.getClass().getSimpleName() : "NO_ERROR_OBJECT")
                + " code=" + firebaseErrorCode(error)
                + " " + path, error);
    }
}
