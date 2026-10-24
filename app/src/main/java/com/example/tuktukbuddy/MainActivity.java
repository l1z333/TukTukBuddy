package com.example.tuktukbuddy;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.tuktukbuddy.model.User;
import com.example.tuktukbuddy.repository.UserRepository;
import com.example.tuktukbuddy.util.UserPreferenceManager;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseUser;

public class MainActivity extends AppCompatActivity {

    private TextInputLayout tilStudentName;
    private TextInputEditText etStudentName;
    private TextInputLayout tilRollNumber;
    private TextInputEditText etRollNumber;
    private Button btnContinue;

    private FrameLayout loadingOverlay;
    private TextView tvLoadingMessage;

    private UserPreferenceManager preferenceManager;
    private UserRepository userRepository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        preferenceManager = new UserPreferenceManager(this);
        userRepository = new UserRepository();

        tilStudentName = findViewById(R.id.tilStudentName);
        etStudentName = findViewById(R.id.etStudentName);
        tilRollNumber = findViewById(R.id.tilRollNumber);
        etRollNumber = findViewById(R.id.etRollNumber);
        btnContinue = findViewById(R.id.btnContinue);

        loadingOverlay = findViewById(R.id.loadingOverlay);
        tvLoadingMessage = findViewById(R.id.tvLoadingMessage);

        btnContinue.setOnClickListener(v -> handleOnboardingSubmit());

        checkExistingUserProfile();
    }

    private void checkExistingUserProfile() {
        if (preferenceManager.isProfileResetPending()) {
            return;
        }

        FirebaseUser currentFirebaseUser = userRepository.getCurrentFirebaseUser();
        if (currentFirebaseUser != null) {
            showLoading(getString(R.string.loading_profile));
            userRepository.fetchUserProfile(currentFirebaseUser.getUid(), new UserRepository.ProfileCallback() {
                @Override
                public void onSuccess(User user) {
                    if (isFinishing() || isDestroyed()) return;
                    if (user != null) {
                        preferenceManager.saveUser(user);
                        navigateToHome();
                    } else {
                        // User is authenticated but profile doc does not exist yet
                        hideLoading();
                    }
                }

                @Override
                public void onFailure(Exception e) {
                    if (isFinishing() || isDestroyed()) return;
                    if (preferenceManager.isOnboarded()) {
                        navigateToHome();
                    } else {
                        hideLoading();
                        Toast.makeText(MainActivity.this, "Network warning: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }
    }

    private void handleOnboardingSubmit() {
        String name = etStudentName.getText() != null ? etStudentName.getText().toString().trim() : "";
        String rollNo = etRollNumber.getText() != null ? etRollNumber.getText().toString().trim() : "";

        tilStudentName.setError(null);
        tilRollNumber.setError(null);

        boolean isNameValid = name.length() >= 2;
        boolean isRollValid = rollNo.length() >= 2;

        if (!isNameValid) {
            tilStudentName.setError(getString(R.string.error_name_required));
            etStudentName.requestFocus();
        }

        if (!isRollValid) {
            tilRollNumber.setError(getString(R.string.error_roll_no_required));
            if (isNameValid) {
                etRollNumber.requestFocus();
            }
        }

        if (!isNameValid || !isRollValid) {
            return;
        }

        setFormInputsEnabled(false);
        showLoading(getString(R.string.saving_profile));

        userRepository.ensureAuthenticated(new UserRepository.AuthCallback() {
            @Override
            public void onSuccess(FirebaseUser firebaseUser) {
                if (isFinishing() || isDestroyed()) return;
                String uid = firebaseUser.getUid();
                User newUser = new User(uid, name, rollNo, 0.0);

                userRepository.saveUserProfile(newUser, new UserRepository.ActionCallback() {
                    @Override
                    public void onSuccess() {
                        if (isFinishing() || isDestroyed()) return;
                        preferenceManager.saveUser(newUser);
                        Toast.makeText(MainActivity.this, "Profile created successfully!", Toast.LENGTH_SHORT).show();
                        navigateToHome();
                    }

                    @Override
                    public void onFailure(Exception e) {
                        if (isFinishing() || isDestroyed()) return;
                        hideLoading();
                        setFormInputsEnabled(true);
                        Toast.makeText(MainActivity.this, "Failed to save profile: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
            }

            @Override
            public void onFailure(Exception e) {
                if (isFinishing() || isDestroyed()) return;
                hideLoading();
                setFormInputsEnabled(true);
                Toast.makeText(MainActivity.this, "Authentication failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void showLoading(String message) {
        tvLoadingMessage.setText(message);
        loadingOverlay.setVisibility(View.VISIBLE);
    }

    private void hideLoading() {
        loadingOverlay.setVisibility(View.GONE);
    }

    private void setFormInputsEnabled(boolean enabled) {
        btnContinue.setEnabled(enabled);
        etStudentName.setEnabled(enabled);
        etRollNumber.setEnabled(enabled);
    }

    private void navigateToHome() {
        Intent intent = new Intent(MainActivity.this, HomeActivity.class);
        startActivity(intent);
        finish();
    }
}
