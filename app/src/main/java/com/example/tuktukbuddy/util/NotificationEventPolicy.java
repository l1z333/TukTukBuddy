package com.example.tuktukbuddy.util;

/** Event predicates used to avoid notifying for an initial Firestore snapshot. */
public final class NotificationEventPolicy {
    private NotificationEventPolicy() {
    }

    public static boolean isNewCreatorRequest(boolean initialSnapshot, boolean added, String status) {
        return !initialSnapshot && added && "PENDING".equalsIgnoreCase(status);
    }

    public static boolean isRequesterDecision(boolean initialSnapshot,
                                              String previousStatus,
                                              String currentStatus) {
        return !initialSnapshot
                && "PENDING".equalsIgnoreCase(previousStatus)
                && ("ACCEPTED".equalsIgnoreCase(currentStatus)
                || "REJECTED".equalsIgnoreCase(currentStatus));
    }

    public static boolean isNewRequesterCancellationNotice(boolean initialSnapshot,
                                                           boolean added,
                                                           String type) {
        return !initialSnapshot && added && "REQUEST_CANCELLED".equalsIgnoreCase(type);
    }

    public static boolean isTripCancellationTransition(boolean initialSnapshot,
                                                       String previousLifecycle,
                                                       String currentLifecycle) {
        return !initialSnapshot
                && !TripLifecycleUtils.CANCELLED.equalsIgnoreCase(previousLifecycle)
                && !TripLifecycleUtils.COMPLETED.equalsIgnoreCase(previousLifecycle)
                && TripLifecycleUtils.CANCELLED.equalsIgnoreCase(currentLifecycle);
    }
}
