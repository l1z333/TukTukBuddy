package com.example.tuktukbuddy;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.tuktukbuddy.adapter.TripAdapter;
import com.example.tuktukbuddy.model.Trip;
import com.example.tuktukbuddy.model.User;
import com.example.tuktukbuddy.repository.RideRequestRepository;
import com.example.tuktukbuddy.repository.TripRepository;
import com.example.tuktukbuddy.repository.UserRepository;
import com.example.tuktukbuddy.util.UserPreferenceManager;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.firebase.auth.FirebaseUser;

import java.util.List;

public class BrowseTripsActivity extends AppCompatActivity {

    private RecyclerView recyclerViewTrips;
    private LinearLayout layoutLoading;
    private LinearLayout layoutEmpty;
    private LinearLayout layoutError;
    private TextView tvErrorMessage;

    private TripAdapter tripAdapter;
    private TripRepository tripRepository;
    private UserRepository userRepository;
    private RideRequestRepository rideRequestRepository;
    private UserPreferenceManager preferenceManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_browse_trips);

        MaterialToolbar toolbar = findViewById(R.id.toolbarBrowseTrips);
        toolbar.setNavigationOnClickListener(v -> finish());

        tripRepository = new TripRepository();
        userRepository = new UserRepository();
        rideRequestRepository = new RideRequestRepository();
        preferenceManager = new UserPreferenceManager(this);

        recyclerViewTrips = findViewById(R.id.recyclerViewTrips);
        layoutLoading = findViewById(R.id.layoutLoading);
        layoutEmpty = findViewById(R.id.layoutEmpty);
        layoutError = findViewById(R.id.layoutError);
        tvErrorMessage = findViewById(R.id.tvErrorMessage);

        Button btnRetry = findViewById(R.id.btnRetry);
        Button btnRefreshEmpty = findViewById(R.id.btnRefreshEmpty);

        tripAdapter = new TripAdapter();
        recyclerViewTrips.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewTrips.setAdapter(tripAdapter);

        tripAdapter.setOnTripRequestListener(this::confirmAndSendJoinRequest);

        btnRetry.setOnClickListener(v -> loadTrips());
        btnRefreshEmpty.setOnClickListener(v -> loadTrips());

        loadTrips();
    }

    private void loadTrips() {
        showLoadingState();

        FirebaseUser currentUser = userRepository.getCurrentFirebaseUser();
        String currentUid = currentUser != null ? currentUser.getUid() : null;

        tripRepository.fetchOpenTrips(currentUid, new TripRepository.TripsLoadedCallback() {
            @Override
            public void onSuccess(List<Trip> trips) {
                if (isFinishing() || isDestroyed()) return;

                if (trips == null || trips.isEmpty()) {
                    showEmptyState();
                } else {
                    tripAdapter.setTrips(trips);
                    showContentState();
                }
            }

            @Override
            public void onFailure(Exception e) {
                if (isFinishing() || isDestroyed()) return;
                showErrorState(e != null ? e.getMessage() : "Unknown error occurred.");
            }
        });
    }

    private void confirmAndSendJoinRequest(Trip trip) {
        if (trip == null) return;

        FirebaseUser currentUser = userRepository.getCurrentFirebaseUser();
        if (currentUser == null) {
            Toast.makeText(this, "Authentication required to request a seat.", Toast.LENGTH_SHORT).show();
            return;
        }

        String route = String.format("%s ➔ %s", trip.getPickup(), trip.getDestination());
        String message = getString(R.string.dialog_confirm_request_message, route);

        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_confirm_request_title)
                .setMessage(message)
                .setPositiveButton("Send Request", (dialog, which) -> sendJoinRequest(trip, currentUser.getUid()))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void sendJoinRequest(Trip trip, String requesterUid) {
        User user = preferenceManager.getUser();
        String requesterName = (user != null && user.getName() != null) ? user.getName() : "Student";

        Toast.makeText(this, R.string.submitting_request, Toast.LENGTH_SHORT).show();

        rideRequestRepository.createRideRequest(trip, requesterUid, requesterName, new RideRequestRepository.ActionCallback() {
            @Override
            public void onSuccess() {
                if (isFinishing() || isDestroyed()) return;
                Toast.makeText(BrowseTripsActivity.this, "Join request submitted successfully!", Toast.LENGTH_LONG).show();
            }

            @Override
            public void onFailure(Exception e) {
                if (isFinishing() || isDestroyed()) return;
                Toast.makeText(BrowseTripsActivity.this, "Request failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void showLoadingState() {
        layoutLoading.setVisibility(View.VISIBLE);
        recyclerViewTrips.setVisibility(View.GONE);
        layoutEmpty.setVisibility(View.GONE);
        layoutError.setVisibility(View.GONE);
    }

    private void showContentState() {
        layoutLoading.setVisibility(View.GONE);
        recyclerViewTrips.setVisibility(View.VISIBLE);
        layoutEmpty.setVisibility(View.GONE);
        layoutError.setVisibility(View.GONE);
    }

    private void showEmptyState() {
        layoutLoading.setVisibility(View.GONE);
        recyclerViewTrips.setVisibility(View.GONE);
        layoutEmpty.setVisibility(View.VISIBLE);
        layoutError.setVisibility(View.GONE);
    }

    private void showErrorState(String message) {
        layoutLoading.setVisibility(View.GONE);
        recyclerViewTrips.setVisibility(View.GONE);
        layoutEmpty.setVisibility(View.GONE);
        tvErrorMessage.setText(message);
        layoutError.setVisibility(View.VISIBLE);
    }
}