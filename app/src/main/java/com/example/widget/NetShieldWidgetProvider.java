package com.example.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.RemoteViews;

import com.example.R;
import com.example.db.ClientDeviceStat;
import com.example.db.DatabaseHelper;
import com.example.db.DomainStat;
import com.example.dns.BlocklistManager;
import com.example.service.ServiceManager;

import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class NetShieldWidgetProvider extends AppWidgetProvider {
    private static final String TAG = "NetShieldWidget";
    public static final String ACTION_TOGGLE = "com.example.widget.ACTION_TOGGLE_VPN";
    public static final String ACTION_TOGGLE_PAUSE = "com.example.widget.ACTION_TOGGLE_PAUSE";
    private static final long PAUSE_DURATION_MS = 15 * 60 * 1000L;
    // Below this placed height, hide the extra "detailed" row to avoid clipped/cramped text.
    private static final int DETAILED_MIN_HEIGHT_DP = 110;

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int id : appWidgetIds) {
            updateWidget(context, appWidgetManager, id);
        }
    }

    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager appWidgetManager,
                                           int appWidgetId, Bundle newOptions) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions);
        // The user resized the widget - re-render so the detailed section can appear/disappear.
        updateWidget(context, appWidgetManager, appWidgetId);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        String action = intent.getAction();
        if (ACTION_TOGGLE.equals(action)) {
            Log.d(TAG, "ACTION_TOGGLE received");
            WidgetActionHelper.handleToggle(context);
            refreshAllWidgets(context);
        } else if (ACTION_TOGGLE_PAUSE.equals(action)) {
            Log.d(TAG, "ACTION_TOGGLE_PAUSE received");
            handleTogglePause(context);
            refreshAllWidgets(context);
        } else if (ServiceManager.ACTION_STATE_CHANGED.equals(action)
                || BlocklistManager.ACTION_PAUSE_STATE_CHANGED.equals(action)) {
            refreshAllWidgets(context);
        }
    }

    private void handleTogglePause(Context context) {
        BlocklistManager manager = BlocklistManager.getInstance(context);
        if (manager.isPaused()) {
            manager.resumeProtection();
        } else {
            manager.pauseProtection(PAUSE_DURATION_MS);
        }
    }

    private void refreshAllWidgets(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        ComponentName thisWidget = new ComponentName(context, NetShieldWidgetProvider.class);
        int[] ids = manager.getAppWidgetIds(thisWidget);
        for (int id : ids) {
            updateWidget(context, manager, id);
        }
    }

    private boolean shouldShowDetailed(AppWidgetManager appWidgetManager, int appWidgetId) {
        Bundle options = appWidgetManager.getAppWidgetOptions(appWidgetId);
        if (options == null) return false;
        int minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0);
        return minHeight >= DETAILED_MIN_HEIGHT_DP;
    }

    private void updateWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_netshield);

        boolean running = WidgetActionHelper.isRunning();
        int state = ServiceManager.getCurrentState();
        int accentColor = WidgetActionHelper.resolveAccentColor(context, running);

        String statusText = running
                ? (state == ServiceManager.STATE_VPN
                    ? context.getString(R.string.status_vpn_running)
                    : context.getString(R.string.status_server_running))
                : context.getString(R.string.status_stopped);
        views.setTextViewText(R.id.tv_widget_status, statusText);
        views.setTextColor(R.id.tv_widget_status, accentColor);
        views.setInt(R.id.iv_widget_icon, "setColorFilter", accentColor);
        views.setInt(R.id.btn_widget_toggle, "setColorFilter", accentColor);

        DatabaseHelper db = DatabaseHelper.getInstance(context);
        long[] stats = db.getStats();
        views.setTextViewText(R.id.tv_widget_stats, stats[0] + " queries • " + stats[1] + " blocked");

        List<ClientDeviceStat> devices = db.getClientDeviceStats();
        views.setTextViewText(R.id.tv_widget_devices,
                devices.size() == 1 ? context.getString(R.string.widget_devices_one)
                        : context.getString(R.string.widget_devices_many, devices.size()));

        BlocklistManager blocklistManager = BlocklistManager.getInstance(context);
        boolean paused = blocklistManager.isPaused();
        if (paused) {
            long remainingMin = Math.max(1, blocklistManager.getRemainingPauseMillis() / 60000);
            views.setTextViewText(R.id.tv_widget_pause_action,
                    context.getString(R.string.widget_resume_paused_format, remainingMin));
        } else {
            views.setTextViewText(R.id.tv_widget_pause_action, context.getString(R.string.widget_pause_15m));
        }

        boolean detailed = shouldShowDetailed(appWidgetManager, appWidgetId);
        views.setViewVisibility(R.id.layout_widget_detailed, detailed ? android.view.View.VISIBLE : android.view.View.GONE);
        if (detailed) {
            populateDetailedSection(context, views, db);
        }

        // Tap the card -> open the app.
        PendingIntent openAppPendingIntent = PendingIntent.getActivity(context, 0, WidgetActionHelper.openAppIntent(context),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_root, openAppPendingIntent);

        // Tap the power button -> toggle directly, single click, no app open needed.
        Intent toggleIntent = new Intent(context, NetShieldWidgetProvider.class);
        toggleIntent.setAction(ACTION_TOGGLE);
        PendingIntent togglePendingIntent = PendingIntent.getBroadcast(context, appWidgetId, toggleIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.btn_widget_toggle, togglePendingIntent);

        // Tap "Pause 15m" / "Resume" -> toggle blocklist pause directly.
        Intent pauseIntent = new Intent(context, NetShieldWidgetProvider.class);
        pauseIntent.setAction(ACTION_TOGGLE_PAUSE);
        PendingIntent pausePendingIntent = PendingIntent.getBroadcast(context, appWidgetId + 100000, pauseIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.tv_widget_pause_action, pausePendingIntent);

        appWidgetManager.updateAppWidget(appWidgetId, views);
    }

    private void populateDetailedSection(Context context, RemoteViews views, DatabaseHelper db) {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long startOfToday = cal.getTimeInMillis();
        cal.add(Calendar.DAY_OF_YEAR, -1);
        long startOfYesterday = cal.getTimeInMillis();

        List<DomainStat> topBlockedToday = db.getTopDomains(1, true, startOfToday);
        if (!topBlockedToday.isEmpty()) {
            DomainStat top = topBlockedToday.get(0);
            views.setTextViewText(R.id.tv_widget_top_blocked,
                    context.getString(R.string.widget_top_blocked_format, top.getDomain(), top.getCount()));
        } else {
            views.setTextViewText(R.id.tv_widget_top_blocked, context.getString(R.string.widget_top_blocked_none));
        }

        int todayCount = db.getQueryCountBetween(startOfToday, System.currentTimeMillis());
        int yesterdayCount = db.getQueryCountBetween(startOfYesterday, startOfToday);
        if (yesterdayCount <= 0) {
            views.setTextViewText(R.id.tv_widget_trend, context.getString(R.string.widget_trend_no_data));
        } else {
            int percentChange = (int) Math.round(((todayCount - yesterdayCount) / (double) yesterdayCount) * 100);
            String arrow = percentChange > 0 ? "\u2191" : percentChange < 0 ? "\u2193" : "\u2192";
            views.setTextViewText(R.id.tv_widget_trend,
                    String.format(Locale.getDefault(), "%s %d%% vs yesterday at this time", arrow, Math.abs(percentChange)));
        }
    }
}
