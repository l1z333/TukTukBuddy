package com.example.tuktukbuddy;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.tuktukbuddy.adapter.MyRequestsAdapter;
import com.example.tuktukbuddy.model.RideRequest;
import com.example.tuktukbuddy.repository.RideRequestRepository;
import com.example.tuktukbuddy.repository.UserRepository;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.firebase.auth.FirebaseUser;

import java.util.List;

public class MyRequestsActivity extends AppCompatActivity {

    private RecyclerView recyclerViewMyRequests;
    private LinearLayout layoutLoading;
    private LinearLayout layoutEmpty;
    private LinearLayout layoutError;
    private TextView tvErrorMessage;

    private MyRequestsAdapter myRequestsAdapter;
    private RideRequestRepository rideRequestRepository;
    private UserRepository userRepository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my_requests);

        MaterialToolbar toolbar = findViewById(R.id.toolbarMyRequests);
        toolbar.setNavigationOnClickListener(v -> finish());

        rideRequestRepository = new RideRequestRepository();
        userRepository = new UserRepository();

        recyclerViewMyRequests = findViewById(R.id.recyclerViewMyRequests);
        layoutLoading = findViewById(R.id.layoutLoadingMyRequests);
        layoutEmpty = findViewById(R.id.layoutEmptyMyRequests);
        layoutError = findViewById(R.id.layoutErrorMyRequests);
        tvErrorMessage = findViewById(R.id.tvErrorMessageMyRequests);

        Button btnRetry = findViewById(R.id.btnRetryMyRequests);
        Button btnRefreshEmpty = findViewById(R.id.btnRefreshMyRequests);

        myRequestsAdapter = new MyRequestsAdapter();
        recyclerViewMyRequests.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewMyRequests.setAdapter(myRequestsAdapter);

        btnRetry.setOnClickListener(v -> loadMyRequests());
        btnRefreshEmpty.setOnClickListener(v -> loadMyRequests());

        loadMyRequests();
    }

    private void loadMyRequests() {
        showLoadingState();

        FirebaseUser currentUser = userRepository.getCurrentFirebaseUser();
        String currentUid = currentUser != null ? currentUser.getUid() : null;

        rideRequestRepository.fetchMyRequests(currentUid, new RideRequestRepository.RequestsLoadedCallback() {
            @Override
            public void onSuccess(List<RideRequest> requests) {
                if (isFinishing() || isDestroyed()) return;

                if (requests == null || requests.isEmpty()) {
                    showEmptyState();
                } else {
                    myRequestsAdapter.setRequests(requests);
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

    private void showLoadingState() {
        layoutLoading.setVisibility(View.VISIBLE);
        recyclerViewMyRequests.setVisibility(View.GONE);
        layoutEmpty.setVisibility(View.GONE);
        layoutError.setVisibility(View.GONE);
    }

    private void showContentState() {
        layoutLoading.setVisibility(View.GONE);
        recyclerViewMyRequests.setVisibility(View.VISIBLE);
        layoutEmpty.setVisibility(View.GONE);
        layoutError.setVisibility(View.GONE);
    }

    private void showEmptyState() {
        layoutLoading.setVisibility(View.GONE);
        recyclerViewMyRequests.setVisibility(View.GONE);
        layoutEmpty.setVisibility(View.VISIBLE);
        layoutError.setVisibility(View.GONE);
    }

    private void showErrorState(String message) {
        layoutLoading.setVisibility(View.GONE);
        recyclerViewMyRequests.setVisibility(View.GONE);
        layoutEmpty.setVisibility(View.GONE);
        tvErrorMessage.setText(message);
        layoutError.setVisibility(View.VISIBLE);
    }
}