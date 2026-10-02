package com.example.widget;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

import com.example.dns.BlocklistManager;

/**
 * Handles widget button taps. Not exported, so only this app's own PendingIntents can reach it;
 * the exported AppWidgetProviders no longer act on toggle/pause actions.
 */
public class WidgetActionReceiver extends android.content.BroadcastReceiver {
    public static final String ACTION_TOGGLE_PAUSE = "com.example.widget.ACTION_TOGGLE_PAUSE";
    private static final long PAUSE_DURATION_MS = 15 * 60 * 1000L;

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (ACTION_TOGGLE_PAUSE.equals(action)) {
            BlocklistManager manager = BlocklistManager.getInstance(context);
            if (manager.isPaused()) {
                manager.resumeProtection();
            } else {
                manager.pauseProtection(PAUSE_DURATION_MS);
            }
        } else {
            return;
        }
        refresh(context, NetShieldWidgetProvider.class);
        refresh(context, NetShieldQuickToggleWidgetProvider.class);
        refresh(context, NetShieldStatsWidgetProvider.class);
    }

    private static void refresh(Context context, Class<?> provider) {
        AppWidgetManager mgr = AppWidgetManager.getInstance(context);
        int[] ids = mgr.getAppWidgetIds(new ComponentName(context, provider));
        if (ids.length == 0) return;
        Intent update = new Intent(context, provider);
        update.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
        update.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids);
        context.sendBroadcast(update);
    }
}
