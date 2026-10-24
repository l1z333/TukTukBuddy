package com.example.tuktukbuddy;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.tuktukbuddy.model.Trip;
import com.example.tuktukbuddy.model.User;
import com.example.tuktukbuddy.repository.TripRepository;
import com.example.tuktukbuddy.repository.UserRepository;
import com.example.tuktukbuddy.util.UserPreferenceManager;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseUser;

import java.util.Calendar;
import java.util.Locale;

public class PostTripActivity extends AppCompatActivity {

    private TextInputLayout tilPickup;
    private TextInputEditText etPickup;
    private TextInputLayout tilDestination;
    private TextInputEditText etDestination;
    private TextInputLayout tilDate;
    private TextInputEditText etDate;
    private TextInputLayout tilTime;
    private TextInputEditText etTime;
    private TextInputLayout tilSeats;
    private TextInputEditText etSeats;
    private TextInputEditText etNotes;
    private Button btnSubmitTrip;

    private FrameLayout loadingOverlay;
    private TextView tvLoadingMessage;

    private UserPreferenceManager preferenceManager;
    private UserRepository userRepository;
    private TripRepository tripRepository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_post_trip);

        MaterialToolbar toolbar = findViewById(R.id.toolbarPostTrip);
        toolbar.setNavigationOnClickListener(v -> finish());

        preferenceManager = new UserPreferenceManager(this);
        userRepository = new UserRepository();
        tripRepository = new TripRepository();

        tilPickup = findViewById(R.id.tilPickup);
        etPickup = findViewById(R.id.etPickup);
        tilDestination = findViewById(R.id.tilDestination);
        etDestination = findViewById(R.id.etDestination);
        tilDate = findViewById(R.id.tilDate);
        etDate = findViewById(R.id.etDate);
        tilTime = findViewById(R.id.tilTime);
        etTime = findViewById(R.id.etTime);
        tilSeats = findViewById(R.id.tilSeats);
        etSeats = findViewById(R.id.etSeats);
        etNotes = findViewById(R.id.etNotes);
        btnSubmitTrip = findViewById(R.id.btnSubmitTrip);

        loadingOverlay = findViewById(R.id.loadingOverlayPostTrip);
        tvLoadingMessage = findViewById(R.id.tvLoadingMessagePostTrip);

        setupDateAndTimePickers();

        btnSubmitTrip.setOnClickListener(v -> handlePostTripSubmit());
    }

    private void setupDateAndTimePickers() {
        etDate.setOnClickListener(v -> {
            final Calendar c = Calendar.getInstance();
            int year = c.get(Calendar.YEAR);
            int month = c.get(Calendar.MONTH);
            int day = c.get(Calendar.DAY_OF_MONTH);

            DatePickerDialog datePickerDialog = new DatePickerDialog(this,
                    (view, selectedYear, selectedMonth, selectedDay) -> {
                        String formattedDate = String.format(Locale.getDefault(), "%04d-%02d-%02d",
                                selectedYear, selectedMonth + 1, selectedDay);
                        etDate.setText(formattedDate);
                        tilDate.setError(null);
                    }, year, month, day);
            datePickerDialog.getDatePicker().setMinDate(System.currentTimeMillis() - 1000);
            datePickerDialog.show();
        });

        etTime.setOnClickListener(v -> {
            final Calendar c = Calendar.getInstance();
            int hour = c.get(Calendar.HOUR_OF_DAY);
            int minute = c.get(Calendar.MINUTE);

            TimePickerDialog timePickerDialog = new TimePickerDialog(this,
                    (view, selectedHour, selectedMinute) -> {
                        int rawHour = selectedHour % 12;
                        int hour12 = (rawHour == 0) ? 12 : rawHour;
                        String amPm = selectedHour >= 12 ? "PM" : "AM";
                        String formattedTime = String.format(Locale.getDefault(), "%02d:%02d %s",
                                hour12, selectedMinute, amPm);
                        etTime.setText(formattedTime);
                        tilTime.setError(null);
                    }, hour, minute, false);
            timePickerDialog.show();
        });
    }

    private void handlePostTripSubmit() {
        String pickup = etPickup.getText() != null ? etPickup.getText().toString().trim() : "";
        String destination = etDestination.getText() != null ? etDestination.getText().toString().trim() : "";
        String date = etDate.getText() != null ? etDate.getText().toString().trim() : "";
        String time = etTime.getText() != null ? etTime.getText().toString().trim() : "";
        String seatsStr = etSeats.getText() != null ? etSeats.getText().toString().trim() : "";
        String notes = etNotes.getText() != null ? etNotes.getText().toString().trim() : "";

        tilPickup.setError(null);
        tilDestination.setError(null);
        tilDate.setError(null);
        tilTime.setError(null);
        tilSeats.setError(null);

        boolean isPickupValid = !pickup.isEmpty();
        boolean isDestValid = !destination.isEmpty();
        boolean isDateValid = !date.isEmpty();
        boolean isTimeValid = !time.isEmpty();

        int parsedSeats = 0;
        try {
            parsedSeats = Integer.parseInt(seatsStr);
        } catch (NumberFormatException ignored) {
        }
        boolean isSeatsValid = parsedSeats >= 1 && parsedSeats <= 10;

        if (!isPickupValid) {
            tilPickup.setError(getString(R.string.error_pickup_required));
            etPickup.requestFocus();
        }

        if (!isDestValid) {
            tilDestination.setError(getString(R.string.error_destination_required));
            if (isPickupValid) etDestination.requestFocus();
        }

        if (!isDateValid) {
            tilDate.setError(getString(R.string.error_date_required));
        }

        if (!isTimeValid) {
            tilTime.setError(getString(R.string.error_time_required));
        }

        if (!isSeatsValid) {
            tilSeats.setError(getString(R.string.error_seats_invalid));
            if (isPickupValid && isDestValid) etSeats.requestFocus();
        }

        if (!isPickupValid || !isDestValid || !isDateValid || !isTimeValid || !isSeatsValid) {
            return;
        }

        FirebaseUser firebaseUser = userRepository.getCurrentFirebaseUser();
        if (firebaseUser == null) {
            Toast.makeText(this, "Authentication error. Please re-open app.", Toast.LENGTH_SHORT).show();
            return;
        }

        String creatorUid = firebaseUser.getUid();
        User cachedUser = preferenceManager.getUser();
        String creatorName = (cachedUser != null && cachedUser.getName() != null) ? cachedUser.getName() : "Student";

        setFormInputsEnabled(false);
        showLoading(getString(R.string.posting_trip));

        Trip trip = new Trip(
                null,
                creatorUid,
                creatorName,
                pickup,
                destination,
                date,
                time,
                parsedSeats,
                notes,
                "OPEN",
                System.currentTimeMillis()
        );

        tripRepository.createTrip(trip, new TripRepository.ActionCallback() {
            @Override
            public void onSuccess() {
                if (isFinishing() || isDestroyed()) return;
                hideLoading();
                Toast.makeText(PostTripActivity.this, "Trip published successfully!", Toast.LENGTH_SHORT).show();
                finish();
            }

            @Override
            public void onFailure(Exception e) {
                if (isFinishing() || isDestroyed()) return;
                hideLoading();
                setFormInputsEnabled(true);
                Toast.makeText(PostTripActivity.this, "Failed to publish trip: " + e.getMessage(), Toast.LENGTH_LONG).show();
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
        btnSubmitTrip.setEnabled(enabled);
        etPickup.setEnabled(enabled);
        etDestination.setEnabled(enabled);
        etSeats.setEnabled(enabled);
        etNotes.setEnabled(enabled);
    }
}