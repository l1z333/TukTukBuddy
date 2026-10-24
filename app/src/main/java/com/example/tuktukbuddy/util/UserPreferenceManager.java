package com.example.tuktukbuddy.util;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.tuktukbuddy.model.User;

public class UserPreferenceManager {
    private static final String PREF_NAME = "tuktukbuddy_prefs";
    private static final String KEY_UID = "user_uid";
    private static final String KEY_NAME = "user_name";
    private static final String KEY_ROLL_NO = "user_roll_no";
    private static final String KEY_AVG_RATING = "user_avg_rating";
    private static final String KEY_IS_ONBOARDED = "is_onboarded";
    private static final String KEY_PROFILE_RESET_PENDING = "profile_reset_pending";

    private final SharedPreferences preferences;

    public UserPreferenceManager(Context context) {
        this.preferences = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public void saveUser(User user) {
        if (user == null) return;
        preferences.edit()
                .putString(KEY_UID, user.getUid())
                .putString(KEY_NAME, user.getName())
                .putString(KEY_ROLL_NO, user.getRollNo())
                .putFloat(KEY_AVG_RATING, (float) user.getAvgRating())
                .putBoolean(KEY_IS_ONBOARDED, true)
                .putBoolean(KEY_PROFILE_RESET_PENDING, false)
                .apply();
    }

    public User getUser() {
        if (!isOnboarded()) {
            return null;
        }
        String uid = preferences.getString(KEY_UID, "");
        String name = preferences.getString(KEY_NAME, "");
        String rollNo = preferences.getString(KEY_ROLL_NO, "");
        float avgRating = preferences.getFloat(KEY_AVG_RATING, 0.0f);
        return new User(uid, name, rollNo, avgRating);
    }

    public String getUid() {
        return preferences.getString(KEY_UID, null);
    }

    public boolean isOnboarded() {
        return preferences.getBoolean(KEY_IS_ONBOARDED, false);
    }

    public boolean isProfileResetPending() {
        return preferences.getBoolean(KEY_PROFILE_RESET_PENDING, false);
    }

    public void clearProfileForReset() {
        preferences.edit()
                .clear()
                .putBoolean(KEY_PROFILE_RESET_PENDING, true)
                .apply();
    }

    public void clear() {
        preferences.edit().clear().apply();
    }
}
