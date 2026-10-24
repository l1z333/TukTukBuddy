package com.example.tuktukbuddy.repository;

import com.example.tuktukbuddy.model.User;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class UserRepository {
    private final FirebaseAuth auth;
    private final FirebaseFirestore firestore;

    public interface AuthCallback {
        void onSuccess(FirebaseUser firebaseUser);
        void onFailure(Exception e);
    }

    public interface ProfileCallback {
        void onSuccess(User user);
        void onFailure(Exception e);
    }

    public interface ActionCallback {
        void onSuccess();
        void onFailure(Exception e);
    }

    public UserRepository() {
        this.auth = FirebaseAuth.getInstance();
        this.firestore = FirebaseFirestore.getInstance();
    }

    public FirebaseUser getCurrentFirebaseUser() {
        return auth.getCurrentUser();
    }

    public void ensureAuthenticated(AuthCallback callback) {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser != null) {
            callback.onSuccess(currentUser);
        } else {
            auth.signInAnonymously()
                    .addOnSuccessListener(authResult -> {
                        if (authResult.getUser() != null) {
                            callback.onSuccess(authResult.getUser());
                        } else {
                            callback.onFailure(new Exception("Anonymous authentication returned a null user."));
                        }
                    })
                    .addOnFailureListener(callback::onFailure);
        }
    }

    public void fetchUserProfile(String uid, ProfileCallback callback) {
        if (uid == null || uid.trim().isEmpty()) {
            callback.onSuccess(null);
            return;
        }

        firestore.collection("users")
                .document(uid)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot.exists()) {
                        User user = snapshot.toObject(User.class);
                        if (user != null) {
                            callback.onSuccess(user);
                        } else {
                            callback.onFailure(new Exception("Failed to parse user profile document."));
                        }
                    } else {
                        callback.onSuccess(null);
                    }
                })
                .addOnFailureListener(callback::onFailure);
    }

    public void saveUserProfile(User user, ActionCallback callback) {
        if (user == null || user.getUid() == null) {
            callback.onFailure(new Exception("Invalid user profile parameters."));
            return;
        }

        firestore.collection("users")
                .document(user.getUid())
                .set(user.toMap())
                .addOnSuccessListener(aVoid -> callback.onSuccess())
                .addOnFailureListener(callback::onFailure);
    }

    public void signOut() {
        auth.signOut();
    }
}