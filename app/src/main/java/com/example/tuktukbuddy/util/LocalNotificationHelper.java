package com.example.tuktukbuddy.util;

import android.Manifest;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;

import com.example.tuktukbuddy.R;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** Posts local notifications only; it does not send data to a push service. */
public final class LocalNotificationHelper {
    private static final String CHANNEL_ID = "trip_updates";
    private static final String PREFS_NAME = "local_notification_dedup";
    private static final String PREF_DELIVERED_EVENTS = "delivered_event_ids";
    private static final Object LOCK = new Object();
    private static final int MAX_REMEMBERED_EVENTS = 500;

    private LocalNotificationHelper() {
    }

    public static void createChannel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager manager = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null || manager.getNotificationChannel(CHANNEL_ID) != null) return;

        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription(context.getString(R.string.notification_channel_description));
        manager.createNotificationChannel(channel);
    }

    public static boolean hasPermission(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return false;
        }

        NotificationManager manager = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null || !manager.areNotificationsEnabled()) return false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = manager.getNotificationChannel(CHANNEL_ID);
            return channel != null && channel.getImportance() != NotificationManager.IMPORTANCE_NONE;
        }
        return true;
    }

    public static boolean post(Context context,
                               String eventId,
                               String title,
                               String message,
                               Class<? extends Activity> destination) {
        if (eventId == null || eventId.trim().isEmpty() || !hasPermission(context)) return false;

        synchronized (LOCK) {
            SharedPreferences preferences = context.getSharedPreferences(
                    PREFS_NAME, Context.MODE_PRIVATE);
            Set<String> delivered = new LinkedHashSet<>(preferences.getStringSet(
                    PREF_DELIVERED_EVENTS, Collections.emptySet()));
            if (delivered.contains(eventId)) return false;

            NotificationManager manager = (NotificationManager)
                    context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager == null) return false;

            int notificationId = eventId.hashCode() & 0x7fffffff;
            Intent intent = new Intent(context, destination)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            PendingIntent contentIntent = PendingIntent.getActivity(
                    context,
                    notificationId,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    ? new Notification.Builder(context, CHANNEL_ID)
                    : new Notification.Builder(context).setPriority(Notification.PRIORITY_DEFAULT);
            Notification notification = builder
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(title)
                    .setContentText(message)
                    .setStyle(new Notification.BigTextStyle().bigText(message))
                    .setContentIntent(contentIntent)
                    .setAutoCancel(true)
                    .build();
            try {
                manager.notify(notificationId, notification);
            } catch (SecurityException permissionChanged) {
                return false;
            }

            delivered.add(eventId);
            while (delivered.size() > MAX_REMEMBERED_EVENTS) {
                delivered.remove(delivered.iterator().next());
            }
            preferences.edit().putStringSet(PREF_DELIVERED_EVENTS, delivered).apply();
            return true;
        }
    }
}
