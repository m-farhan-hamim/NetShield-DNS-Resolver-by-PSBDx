package com.example.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import androidx.core.app.NotificationCompat;

import com.example.R;
import com.example.db.DatabaseHelper;
import com.example.receiver.NotificationActionReceiver;
import com.example.ui.MainActivity;

import java.util.Locale;

public class ServiceManager {
    public static final String ACTION_STATE_CHANGED = "com.example.dns.ACTION_STATE_CHANGED";
    /**
     * Sent to a running service's own onStartCommand() to make it tear
     * itself down directly (see DnsVpnService/DnsServerService.performTeardown()).
     * Deliberately NOT relying on Context.stopService() alone: once a
     * VpnService's tunnel is established, the system also holds its own
     * internal binding to it, and a Service is only destroyed once it is
     * BOTH un-started AND fully unbound - so stopService() can silently
     * fail to ever invoke onDestroy(), leaving the tunnel (and its
     * notification) running forever regardless of what the app UI says.
     * Routing the stop through the service's own onStartCommand sidesteps
     * that entirely.
     */
    public static final String ACTION_STOP = "com.example.service.ACTION_STOP";
    public static final String CHANNEL_ID = "dns_service_channel";
    public static final int NOTIFICATION_ID = 1001;

    public static final int STATE_STOPPED = 0;
    public static final int STATE_SERVER = 1;
    public static final int STATE_VPN = 2;

    private static final long TICK_INTERVAL_MS = 2000L;

    private static volatile int currentState = STATE_STOPPED;

    // Live-notification ticker. Guarded so only one ticker (for the currently
    // running service) is ever active at a time.
    private static Handler tickHandler;
    private static Runnable tickRunnable;

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

    /**
     * Single, authoritative way to stop whatever is currently running.
     * Sends ACTION_STOP to BOTH service classes unconditionally (regardless
     * of tracked state) via startService(), so each one tears itself down
     * directly from inside its own onStartCommand() - see ACTION_STOP above.
     */
    public static void stopActiveService(Context context) {
        sendStopAction(context, DnsVpnService.class);
        sendStopAction(context, DnsServerService.class);
    }

    private static void sendStopAction(Context context, Class<?> serviceClass) {
        try {
            Intent stopIntent = new Intent(context, serviceClass);
            stopIntent.setAction(ACTION_STOP);
            context.startService(stopIntent);
        } catch (Exception ignored) {
            // Service isn't running / can't be started in this state - fine,
            // there's nothing to stop.
        }
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
        return buildForegroundNotification(context, modeText, -1, -1);
    }

    /**
     * @param queriesPerSecond live throughput, or a negative value if not yet known
     * @param avgLatencyMs     live average latency in ms, or a negative value if not yet known
     */
    public static Notification buildForegroundNotification(Context context, String modeText,
                                                             double queriesPerSecond, double avgLatencyMs) {
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

        long[] stats = DatabaseHelper.getInstance(context).getStats();
        String statsText;
        if (queriesPerSecond >= 0 && avgLatencyMs >= 0) {
            statsText = String.format(Locale.getDefault(),
                    context.getString(R.string.notification_live_status),
                    queriesPerSecond, avgLatencyMs, stats[0], stats[1]);
        } else {
            statsText = stats[0] + " queries | " + stats[1] + " blocked";
        }

        return new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_shield)
                .setContentTitle(modeText)
                .setContentText(statsText)
                .setContentIntent(contentPendingIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .addAction(R.drawable.ic_power, context.getString(R.string.action_stop), stopPendingIntent)
                .build();
    }

    /**
     * Starts a periodic ticker that refreshes the foreground notification
     * with live speed (queries/sec) and average latency, every couple of
     * seconds, until {@link #stopLiveNotificationUpdates()} is called.
     */
    public static synchronized void startLiveNotificationUpdates(final Service service, final String modeText) {
        stopLiveNotificationUpdates();

        tickHandler = new Handler(Looper.getMainLooper());
        tickRunnable = new Runnable() {
            long lastTickAt = System.currentTimeMillis();

            @Override
            public void run() {
                if (currentState == STATE_STOPPED) {
                    return;
                }
                long now = System.currentTimeMillis();
                long windowMs = Math.max(1000L, now - lastTickAt);
                lastTickAt = now;

                DatabaseHelper db = DatabaseHelper.getInstance(service);
                int recentCount = db.getRecentWindowQueryCount(windowMs);
                double qps = recentCount / (windowMs / 1000.0);
                double latency = db.getAverageResponseTimeMs(10_000L);
                if (latency <= 0) {
                    latency = db.getAverageResponseTimeMs();
                }

                Notification notification = buildForegroundNotification(service, modeText, qps, latency);
                NotificationManager nm = (NotificationManager) service.getSystemService(Context.NOTIFICATION_SERVICE);
                if (nm != null) {
                    nm.notify(NOTIFICATION_ID, notification);
                }

                if (tickHandler != null) {
                    tickHandler.postDelayed(this, TICK_INTERVAL_MS);
                }
            }
        };
        tickHandler.postDelayed(tickRunnable, TICK_INTERVAL_MS);
    }

    public static synchronized void stopLiveNotificationUpdates() {
        if (tickHandler != null && tickRunnable != null) {
            tickHandler.removeCallbacks(tickRunnable);
        }
        tickHandler = null;
        tickRunnable = null;
    }
}
