package com.example.tuktukbuddy;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.tuktukbuddy.model.User;
import com.example.tuktukbuddy.repository.UserRepository;
import com.example.tuktukbuddy.util.UserPreferenceManager;

public class HomeActivity extends AppCompatActivity {

    private TextView tvHomeWelcome;
    private TextView tvHomeRollNo;
    private TextView tvHomeRating;

    private UserPreferenceManager preferenceManager;
    private UserRepository userRepository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        preferenceManager = new UserPreferenceManager(this);
        userRepository = new UserRepository();

        tvHomeWelcome = findViewById(R.id.tvHomeWelcome);
        tvHomeRollNo = findViewById(R.id.tvHomeRollNo);
        tvHomeRating = findViewById(R.id.tvHomeRating);

        Button btnPostTrip = findViewById(R.id.btnPostTrip);
        Button btnBrowseTrips = findViewById(R.id.btnBrowseTrips);
        Button btnMyRequests = findViewById(R.id.btnMyRequests);
        Button btnManageRequests = findViewById(R.id.btnManageRequests);
        Button btnResetProfile = findViewById(R.id.btnResetProfile);

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

        btnManageRequests.setOnClickListener(v -> {
            Intent intent = new Intent(HomeActivity.this, ManageRequestsActivity.class);
            startActivity(intent);
        });

        btnResetProfile.setOnClickListener(v -> resetProfileForOnboarding());
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

    private void redirectToOnboarding() {
        Intent intent = new Intent(HomeActivity.this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
