package com.example.tuktukbuddy;

import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.tuktukbuddy.model.RideRequest;
import com.example.tuktukbuddy.model.Trip;
import com.example.tuktukbuddy.repository.TripRepository;
import com.example.tuktukbuddy.util.TripHistoryPolicy;
import com.example.tuktukbuddy.util.TripTimeUtils;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.card.MaterialCardView;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Loads personal trip history only while this screen is open. */
public class TripHistoryActivity extends AppCompatActivity {
    private MaterialButton btnCompleted;
    private MaterialButton btnCancelled;
    private MaterialButton btnRetry;
    private TextView tvLoading;
    private TextView tvError;
    private TextView tvEmpty;
    private LinearLayout layoutHistory;

    private final Map<String, HistoryEntry> historyByTripId = new LinkedHashMap<>();
    private final Set<String> hiddenTripIds = new LinkedHashSet<>();
    private final Set<String> hideInProgress = new LinkedHashSet<>();
    private TripHistoryPolicy.Category selectedCategory = TripHistoryPolicy.Category.COMPLETED;
    private String currentUid;
    private boolean historyLoaded;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_trip_history);

        MaterialToolbar toolbar = findViewById(R.id.toolbarTripHistory);
        toolbar.setNavigationOnClickListener(view -> finish());
        btnCompleted = findViewById(R.id.btnHistoryCompleted);
        btnCancelled = findViewById(R.id.btnHistoryCancelled);
        btnRetry = findViewById(R.id.btnHistoryRetry);
        tvLoading = findViewById(R.id.tvHistoryLoading);
        tvError = findViewById(R.id.tvHistoryError);
        tvEmpty = findViewById(R.id.tvHistoryEmpty);
        layoutHistory = findViewById(R.id.layoutTripHistory);

        btnCompleted.setOnClickListener(view -> selectCategory(TripHistoryPolicy.Category.COMPLETED));
        btnCancelled.setOnClickListener(view -> selectCategory(TripHistoryPolicy.Category.CANCELLED));
        btnRetry.setOnClickListener(view -> loadHistory());
        updateTabLabels();

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        currentUid = user != null ? user.getUid() : null;
        if (currentUid == null || currentUid.trim().isEmpty()) {
            showError();
            return;
        }
        loadHistory();
    }

    private void loadHistory() {
        if (currentUid == null || currentUid.trim().isEmpty()) {
            showError();
            return;
        }
        historyByTripId.clear();
        hiddenTripIds.clear();
        hideInProgress.clear();
        historyLoaded = false;
        tvError.setVisibility(View.GONE);
        btnRetry.setVisibility(View.GONE);
        tvEmpty.setVisibility(View.GONE);
        layoutHistory.removeAllViews();
        tvLoading.setVisibility(View.VISIBLE);

        FirebaseFirestore firestore = FirebaseFirestore.getInstance();
        Task<QuerySnapshot> creatorTrips = firestore.collection("trips")
                .whereEqualTo("creatorUid", currentUid)
                .whereIn("lifecycleStatus", Arrays.asList("COMPLETED", "CANCELLED"))
                .get();
        Task<QuerySnapshot> requesterRequests = firestore.collection("rideRequests")
                .whereEqualTo("requesterUid", currentUid)
                .get();
        Task<QuerySnapshot> hiddenTrips = firestore.collection("users").document(currentUid)
                .collection("hiddenTrips").get();

        Tasks.whenAllSuccess(creatorTrips, requesterRequests, hiddenTrips)
                .addOnSuccessListener(results -> loadRequesterTripDetails(
                        firestore,
                        (QuerySnapshot) results.get(0),
                        (QuerySnapshot) results.get(1),
                        (QuerySnapshot) results.get(2)))
                .addOnFailureListener(error -> showError());
    }

    private void loadRequesterTripDetails(FirebaseFirestore firestore,
                                         QuerySnapshot creatorSnapshots,
                                         QuerySnapshot requestSnapshots,
                                         QuerySnapshot hiddenSnapshots) {
        for (QueryDocumentSnapshot document : creatorSnapshots) {
            Trip trip = document.toObject(Trip.class);
            trip.setTripId(document.getId());
            if (trip != null
                    && TripHistoryPolicy.categoryForLifecycle(trip.getLifecycleStatus()) != null) {
                historyByTripId.put(document.getId(), new HistoryEntry(trip, true));
            }
        }
        for (QueryDocumentSnapshot hidden : hiddenSnapshots) hiddenTripIds.add(hidden.getId());

        Map<String, List<String>> requesterStatusesByTrip = new LinkedHashMap<>();
        for (QueryDocumentSnapshot document : requestSnapshots) {
            RideRequest request = document.toObject(RideRequest.class);
            if (request.getTripId() == null || request.getTripId().trim().isEmpty()) continue;
            requesterStatusesByTrip.computeIfAbsent(request.getTripId(), ignored -> new ArrayList<>())
                    .add(request.getStatus());
        }

        List<String> tripIds = new ArrayList<>(requesterStatusesByTrip.keySet());
        if (tripIds.isEmpty()) {
            historyLoaded = true;
            showHistory();
            return;
        }

        List<Task<DocumentSnapshot>> tripTasks = new ArrayList<>();
        for (String tripId : tripIds) {
            tripTasks.add(firestore.collection("trips").document(tripId).get());
        }
        Tasks.whenAllSuccess(tripTasks).addOnSuccessListener(results -> {
            for (int index = 0; index < results.size(); index++) {
                DocumentSnapshot snapshot = (DocumentSnapshot) results.get(index);
                if (!snapshot.exists()) continue;
                Trip trip = snapshot.toObject(Trip.class);
                if (trip == null || !TripHistoryPolicy.requesterWasAffected(
                        trip.getLifecycleStatus(), requesterStatusesByTrip.get(tripIds.get(index)))) continue;
                trip.setTripId(snapshot.getId());
                historyByTripId.putIfAbsent(snapshot.getId(), new HistoryEntry(trip, false));
            }
            historyLoaded = true;
            showHistory();
        }).addOnFailureListener(error -> showError());
    }

    private void selectCategory(TripHistoryPolicy.Category category) {
        selectedCategory = category;
        updateTabLabels();
        if (historyLoaded) showHistory();
    }

    private void updateTabLabels() {
        btnCompleted.setText(selectedCategory == TripHistoryPolicy.Category.COMPLETED
                ? "✓ " + getString(R.string.trip_history_completed_tab)
                : getString(R.string.trip_history_completed_tab));
        btnCancelled.setText(selectedCategory == TripHistoryPolicy.Category.CANCELLED
                ? "✓ " + getString(R.string.trip_history_cancelled_tab)
                : getString(R.string.trip_history_cancelled_tab));
    }

    private void showHistory() {
        if (!historyLoaded) {
            tvLoading.setVisibility(View.VISIBLE);
            tvError.setVisibility(View.GONE);
            tvEmpty.setVisibility(View.GONE);
            btnRetry.setVisibility(View.GONE);
            return;
        }
        tvLoading.setVisibility(View.GONE);
        tvError.setVisibility(View.GONE);
        btnRetry.setVisibility(View.GONE);
        layoutHistory.removeAllViews();

        List<HistoryEntry> visible = new ArrayList<>();
        for (Map.Entry<String, HistoryEntry> entry : historyByTripId.entrySet()) {
            if (hiddenTripIds.contains(entry.getKey())) continue;
            if (TripHistoryPolicy.categoryForLifecycle(entry.getValue().trip.getLifecycleStatus())
                    == selectedCategory) visible.add(entry.getValue());
        }
        visible.sort(Comparator.comparingLong((HistoryEntry entry) -> {
            return entry.trip.getDepartureAt() != null
                    ? entry.trip.getDepartureAt().getTime() : entry.trip.getCreatedAt();
        }).reversed());

        if (visible.isEmpty()) {
            tvEmpty.setText(selectedCategory == TripHistoryPolicy.Category.COMPLETED
                    ? R.string.trip_history_empty_completed : R.string.trip_history_empty_cancelled);
            tvEmpty.setVisibility(View.VISIBLE);
            return;
        }
        tvEmpty.setVisibility(View.GONE);
        for (HistoryEntry entry : visible) addHistoryCard(entry);
    }

    private void addHistoryCard(HistoryEntry entry) {
        Trip trip = entry.trip;
        MaterialCardView card = new MaterialCardView(this);
        card.setRadius(dp(14));
        card.setCardElevation(dp(2));
        card.setStrokeWidth(dp(1));
        card.setStrokeColor(getColor(R.color.surface_variant));
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(16), dp(16), dp(12));

        String pickup = trip.getPickup() != null ? trip.getPickup() : "?";
        String destination = trip.getDestination() != null ? trip.getDestination() : "?";
        content.addView(makeText(pickup + "  →  " + destination, 17, true));
        String date = TripTimeUtils.getDisplayDate(trip);
        String time = TripTimeUtils.getDisplayTime(trip);
        if (date != null && time != null) {
            content.addView(makeText(getString(R.string.trip_date_time_format, date, time), 14, false));
        }
        String state = selectedCategory == TripHistoryPolicy.Category.COMPLETED
                ? getString(R.string.trip_history_completed_tab)
                : getString(R.string.trip_history_cancelled_tab);
        content.addView(makeText(getString(R.string.home_trip_lifecycle_label, state), 13, false));
        content.addView(makeText(getString(entry.creator
                ? R.string.trip_history_role_creator : R.string.trip_history_role_requester), 13, false));

        MaterialButton hide = new MaterialButton(this);
        hide.setText(R.string.home_trip_hide_history);
        hide.setEnabled(!hideInProgress.contains(trip.getTripId()));
        hide.setOnClickListener(view -> confirmHide(trip.getTripId()));
        LinearLayout.LayoutParams hideParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        hideParams.topMargin = dp(8);
        content.addView(hide, hideParams);
        card.addView(content);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.bottomMargin = dp(12);
        layoutHistory.addView(card, cardParams);
    }

    private void confirmHide(String tripId) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.home_trip_hide_title)
                .setMessage(R.string.home_trip_hide_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.home_trip_hide_confirm, (dialog, which) -> hideFromHistory(tripId))
                .show();
    }

    private void hideFromHistory(String tripId) {
        if (currentUid == null || !hideInProgress.add(tripId)) return;
        showHistory();
        new TripRepository().hideTripFromHistory(currentUid, tripId, new TripRepository.ActionCallback() {
            @Override
            public void onSuccess() {
                hideInProgress.remove(tripId);
                hiddenTripIds.add(tripId);
                Toast.makeText(TripHistoryActivity.this, "Trip removed from your history.",
                        Toast.LENGTH_SHORT).show();
                showHistory();
            }

            @Override
            public void onFailure(Exception error) {
                hideInProgress.remove(tripId);
                String message = error != null && error.getMessage() != null
                        ? error.getMessage() : "Could not update your trip history.";
                Toast.makeText(TripHistoryActivity.this, message, Toast.LENGTH_LONG).show();
                showHistory();
            }
        });
    }

    private void showError() {
        tvLoading.setVisibility(View.GONE);
        tvEmpty.setVisibility(View.GONE);
        layoutHistory.removeAllViews();
        tvError.setText(R.string.trip_history_load_failed);
        tvError.setVisibility(View.VISIBLE);
        btnRetry.setVisibility(View.VISIBLE);
    }

    private TextView makeText(String text, int size, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(getColor(bold ? R.color.on_surface : R.color.on_surface_variant));
        if (bold) view.setTypeface(view.getTypeface(), android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(6);
        view.setLayoutParams(params);
        return view;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static final class HistoryEntry {
        final Trip trip;
        final boolean creator;

        HistoryEntry(Trip trip, boolean creator) {
            this.trip = trip;
            this.creator = creator;
        }
    }
}
