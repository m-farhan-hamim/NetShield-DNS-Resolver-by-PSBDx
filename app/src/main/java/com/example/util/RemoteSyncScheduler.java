package com.example.util;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.example.R;
import com.example.dns.BlocklistManager;
import com.example.dns.DnsResolverEngine;
import com.example.service.RemoteSyncJobService;

import java.util.concurrent.TimeUnit;

/**
 * Optional automatic sync of the remote blocklists, once every 24 hours, with a notification when it
 * finishes. Uses the system JobScheduler (no new library): the system picks a good moment, keeps the
 * job across reboots and only runs it with a network connection.
 */
public final class RemoteSyncScheduler {

    public static final String KEY_AUTO_SYNC = "remote_sync_auto";
    public static final String KEY_LAST_SYNC = "remote_sync_last";

    private static final int JOB_ID = 4201;
    private static final int NOTIFICATION_ID = 4202;
    private static final String CHANNEL_ID = "list_sync_channel";
    private static final long PERIOD_MS = TimeUnit.HOURS.toMillis(24);

    private RemoteSyncScheduler() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(DnsResolverEngine.PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static boolean isEnabled(Context context) {
        return prefs(context).getBoolean(KEY_AUTO_SYNC, false);
    }

    public static long lastSyncMillis(Context context) {
        return prefs(context).getLong(KEY_LAST_SYNC, 0L);
    }

    /** Turns auto sync on or off. Turning it on also syncs once right away so the lists are filled. */
    public static void setEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_AUTO_SYNC, enabled).apply();
        if (enabled) {
            schedule(context);
            syncNow(context);
        } else {
            JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
            if (scheduler != null) {
                scheduler.cancel(JOB_ID);
            }
        }
    }

    /** Makes sure the job exists when auto sync is on. Cheap and safe to call on every app start. */
    public static void scheduleIfEnabled(Context context) {
        if (isEnabled(context)) {
            schedule(context);
        }
    }

    private static void schedule(Context context) {
        JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (scheduler == null) {
            return;
        }
        // Keep the existing job: re-scheduling would restart its 24 hour clock on every app start.
        if (scheduler.getPendingJob(JOB_ID) != null) {
            return;
        }
        JobInfo.Builder builder = new JobInfo.Builder(JOB_ID, new ComponentName(context, RemoteSyncJobService.class))
                .setPeriodic(PERIOD_MS)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPersisted(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setRequiresBatteryNotLow(true);
        }
        scheduler.schedule(builder.build());
    }

    /** One sync now, in the background, ending with the same notification as the daily one. */
    public static void syncNow(final Context context) {
        final Context app = context.getApplicationContext();
        BlocklistManager.getInstance(app).syncRemoteBlocklists(new BlocklistManager.SyncCallback() {
            @Override
            public void onProgress(String message) {
            }

            @Override
            public void onComplete(int totalRules) {
                recordSuccess(app, totalRules);
            }

            @Override
            public void onError(String error) {
                notifyResult(app, false, error);
            }
        });
    }

    public static void recordSuccess(Context context, int totalRules) {
        prefs(context).edit().putLong(KEY_LAST_SYNC, System.currentTimeMillis()).apply();
        notifyResult(context, true, context.getString(R.string.sync_notification_ok, totalRules));
    }

    public static void notifyResult(Context context, boolean ok, String detail) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return; // the user did not allow notifications; the sync itself still happened
        }
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                    context.getString(R.string.sync_channel_name), NotificationManager.IMPORTANCE_LOW);
            channel.setDescription(context.getString(R.string.sync_channel_desc));
            manager.createNotificationChannel(channel);
        }
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_shield)
                .setContentTitle(context.getString(ok ? R.string.sync_notification_title_ok : R.string.sync_notification_title_fail))
                .setContentText(detail)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(detail))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_LOW);
        manager.notify(NOTIFICATION_ID, builder.build());
    }
}
