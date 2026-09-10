package com.example.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import androidx.core.app.NotificationCompat;

import com.example.R;
import com.example.db.DatabaseHelper;
import com.example.receiver.NotificationActionReceiver;
import com.example.ui.MainActivity;

public class ServiceManager {
    public static final String ACTION_STATE_CHANGED = "com.example.dns.ACTION_STATE_CHANGED";
    public static final String CHANNEL_ID = "dns_service_channel";
    public static final int NOTIFICATION_ID = 1001;

    public static final int STATE_STOPPED = 0;
    public static final int STATE_SERVER = 1;
    public static final int STATE_VPN = 2;

    private static volatile int currentState = STATE_STOPPED;

    public static int getCurrentState() {
        return currentState;
    }

    public static void setCurrentState(Context context, int state) {
        currentState = state;
        Intent intent = new Intent(ACTION_STATE_CHANGED);
        intent.putExtra("state", state);
        intent.setPackage(context.getPackageName());
        context.sendBroadcast(intent);
    }

    public static void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.notification_channel_name),
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription(context.getString(R.string.notification_channel_desc));
            channel.setShowBadge(false);
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    public static Notification buildForegroundNotification(Context context, String modeText) {
        createNotificationChannel(context);

        Intent openAppIntent = new Intent(context, MainActivity.class);
        openAppIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentPendingIntent = PendingIntent.getActivity(
                context, 0, openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0)
        );

        // Stop Action
        Intent stopIntent = new Intent(context, NotificationActionReceiver.class);
        stopIntent.setAction(NotificationActionReceiver.ACTION_STOP_SERVICE);
        PendingIntent stopPendingIntent = PendingIntent.getBroadcast(
                context, 1, stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0)
        );

        // Stats summary
        long[] stats = DatabaseHelper.getInstance(context).getStats();
        String statsText = stats[0] + " queries | " + stats[1] + " blocked";

        return new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_shield)
                .setContentTitle(modeText)
                .setContentText(statsText)
                .setContentIntent(contentPendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .addAction(R.drawable.ic_power, context.getString(R.string.action_stop), stopPendingIntent)
                .build();
    }
}
