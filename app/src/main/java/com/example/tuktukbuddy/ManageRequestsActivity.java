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

import com.example.tuktukbuddy.adapter.ManageRequestsAdapter;
import com.example.tuktukbuddy.model.RideRequest;
import com.example.tuktukbuddy.repository.RideRequestRepository;
import com.example.tuktukbuddy.repository.UserRepository;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.firebase.auth.FirebaseUser;

import java.util.List;

public class ManageRequestsActivity extends AppCompatActivity {

    private RecyclerView recyclerViewManageRequests;
    private LinearLayout layoutLoading;
    private LinearLayout layoutEmpty;
    private LinearLayout layoutError;
    private TextView tvErrorMessage;

    private ManageRequestsAdapter manageRequestsAdapter;
    private RideRequestRepository rideRequestRepository;
    private UserRepository userRepository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manage_requests);

        MaterialToolbar toolbar = findViewById(R.id.toolbarManageRequests);
        toolbar.setNavigationOnClickListener(v -> finish());

        rideRequestRepository = new RideRequestRepository();
        userRepository = new UserRepository();

        recyclerViewManageRequests = findViewById(R.id.recyclerViewManageRequests);
        layoutLoading = findViewById(R.id.layoutLoadingManageRequests);
        layoutEmpty = findViewById(R.id.layoutEmptyManageRequests);
        layoutError = findViewById(R.id.layoutErrorManageRequests);
        tvErrorMessage = findViewById(R.id.tvErrorMessageManageRequests);

        Button btnRetry = findViewById(R.id.btnRetryManageRequests);
        Button btnRefreshEmpty = findViewById(R.id.btnRefreshManageRequests);

        manageRequestsAdapter = new ManageRequestsAdapter();
        recyclerViewManageRequests.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewManageRequests.setAdapter(manageRequestsAdapter);

        manageRequestsAdapter.setOnManageRequestListener(new ManageRequestsAdapter.OnManageRequestListener() {
            @Override
            public void onAccept(RideRequest request) {
                confirmAndAcceptRequest(request);
            }

            @Override
            public void onReject(RideRequest request) {
                confirmAndRejectRequest(request);
            }
        });

        btnRetry.setOnClickListener(v -> loadManageRequests());
        btnRefreshEmpty.setOnClickListener(v -> loadManageRequests());

        loadManageRequests();
    }

    private void loadManageRequests() {
        showLoadingState();

        FirebaseUser currentUser = userRepository.getCurrentFirebaseUser();
        String currentUid = currentUser != null ? currentUser.getUid() : null;

        rideRequestRepository.fetchRequestsForTripCreator(currentUid, new RideRequestRepository.RequestsLoadedCallback() {
            @Override
            public void onSuccess(List<RideRequest> requests) {
                if (isFinishing() || isDestroyed()) return;

                if (requests == null || requests.isEmpty()) {
                    showEmptyState();
                } else {
                    manageRequestsAdapter.setRequests(requests);
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

    private void confirmAndAcceptRequest(RideRequest request) {
        if (request == null) return;

        String message = getString(R.string.dialog_confirm_accept_message, request.getRequesterName());

        new AlertDialog.Builder(this)
                .setTitle(R.string.btn_accept)
                .setMessage(message)
                .setPositiveButton("Accept", (dialog, which) -> executeAcceptRequest(request))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void executeAcceptRequest(RideRequest request) {
        Toast.makeText(this, R.string.processing_request, Toast.LENGTH_SHORT).show();

        rideRequestRepository.acceptRideRequest(request, new RideRequestRepository.ActionCallback() {
            @Override
            public void onSuccess() {
                if (isFinishing() || isDestroyed()) return;
                Toast.makeText(ManageRequestsActivity.this, "Ride request accepted! Seat updated.", Toast.LENGTH_LONG).show();
                loadManageRequests();
            }

            @Override
            public void onFailure(Exception e) {
                if (isFinishing() || isDestroyed()) return;
                Toast.makeText(ManageRequestsActivity.this, "Acceptance failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void confirmAndRejectRequest(RideRequest request) {
        if (request == null) return;

        String message = getString(R.string.dialog_confirm_reject_message, request.getRequesterName());

        new AlertDialog.Builder(this)
                .setTitle(R.string.btn_reject)
                .setMessage(message)
                .setPositiveButton("Reject", (dialog, which) -> executeRejectRequest(request))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void executeRejectRequest(RideRequest request) {
        Toast.makeText(this, R.string.processing_request, Toast.LENGTH_SHORT).show();

        rideRequestRepository.rejectRideRequest(request, new RideRequestRepository.ActionCallback() {
            @Override
            public void onSuccess() {
                if (isFinishing() || isDestroyed()) return;
                Toast.makeText(ManageRequestsActivity.this, "Ride request rejected.", Toast.LENGTH_SHORT).show();
                loadManageRequests();
            }

            @Override
            public void onFailure(Exception e) {
                if (isFinishing() || isDestroyed()) return;
                Toast.makeText(ManageRequestsActivity.this, "Rejection failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void showLoadingState() {
        layoutLoading.setVisibility(View.VISIBLE);
        recyclerViewManageRequests.setVisibility(View.GONE);
        layoutEmpty.setVisibility(View.GONE);
        layoutError.setVisibility(View.GONE);
    }

    private void showContentState() {
        layoutLoading.setVisibility(View.GONE);
        recyclerViewManageRequests.setVisibility(View.VISIBLE);
        layoutEmpty.setVisibility(View.GONE);
        layoutError.setVisibility(View.GONE);
    }

    private void showEmptyState() {
        layoutLoading.setVisibility(View.GONE);
        recyclerViewManageRequests.setVisibility(View.GONE);
        layoutEmpty.setVisibility(View.VISIBLE);
        layoutError.setVisibility(View.GONE);
    }

    private void showErrorState(String message) {
        layoutLoading.setVisibility(View.GONE);
        recyclerViewManageRequests.setVisibility(View.GONE);
        layoutEmpty.setVisibility(View.GONE);
        tvErrorMessage.setText(message);
        layoutError.setVisibility(View.VISIBLE);
    }
}