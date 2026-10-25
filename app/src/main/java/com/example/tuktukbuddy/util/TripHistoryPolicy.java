package com.example.tuktukbuddy.util;

import java.util.Collection;

/** Keeps completed and cancelled history mutually exclusive and user-relevant. */
public final class TripHistoryPolicy {
    public enum Category { COMPLETED, CANCELLED }

    private TripHistoryPolicy() {
    }

    public static Category categoryForLifecycle(String lifecycleStatus) {
        if (TripLifecycleUtils.COMPLETED.equalsIgnoreCase(lifecycleStatus)) return Category.COMPLETED;
        if (TripLifecycleUtils.CANCELLED.equalsIgnoreCase(lifecycleStatus)) return Category.CANCELLED;
        return null;
    }

    /** Requesters see completed accepted rides and any requested trip cancelled by its poster. */
    public static boolean requesterWasAffected(String lifecycleStatus, Collection<String> requestStatuses) {
        if (requestStatuses == null || requestStatuses.isEmpty()) return false;
        Category category = categoryForLifecycle(lifecycleStatus);
        if (category == Category.COMPLETED) {
            for (String status : requestStatuses) {
                if ("ACCEPTED".equalsIgnoreCase(status)) return true;
            }
        } else if (category == Category.CANCELLED) return true;
        return false;
    }
}
