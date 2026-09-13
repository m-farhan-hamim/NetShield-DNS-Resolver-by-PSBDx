package com.example.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.VpnService;
import android.os.Build;
import android.util.Log;
import android.widget.RemoteViews;

import androidx.core.content.ContextCompat;

import com.example.R;
import com.example.db.DatabaseHelper;
import com.example.dns.DnsResolverEngine;
import com.example.service.DnsServerService;
import com.example.service.DnsVpnService;
import com.example.service.ServiceManager;
import com.example.ui.MainActivity;

public class NetShieldWidgetProvider extends AppWidgetProvider {
    private static final String TAG = "NetShieldWidget";
    public static final String ACTION_TOGGLE = "com.example.widget.ACTION_TOGGLE_VPN";

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int id : appWidgetIds) {
            updateWidget(context, appWidgetManager, id);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        String action = intent.getAction();
        if (ACTION_TOGGLE.equals(action)) {
            Log.d(TAG, "ACTION_TOGGLE received");
            handleToggle(context);
            refreshAllWidgets(context);
        } else if (ServiceManager.ACTION_STATE_CHANGED.equals(action)) {
            refreshAllWidgets(context);
        }
    }

    private void handleToggle(Context context) {
        int state = ServiceManager.getCurrentState();
        if (state != ServiceManager.STATE_STOPPED) {
            ServiceManager.stopActiveService(context);
            ServiceManager.setCurrentState(context, ServiceManager.STATE_STOPPED);
            return;
        }

        SharedPreferences prefs = context.getSharedPreferences(DnsResolverEngine.PREFS_NAME, Context.MODE_PRIVATE);
        String mode = prefs.getString("operation_mode", "VPN");
        if ("VPN".equalsIgnoreCase(mode)) {
            Intent vpnPrepare = VpnService.prepare(context);
            if (vpnPrepare == null) {
                startForegroundServiceCompat(context, new Intent(context, DnsVpnService.class));
                ServiceManager.setCurrentState(context, ServiceManager.STATE_VPN);
            } else {
                // First-time VPN consent can only be granted through an Activity.
                Intent launch = new Intent(context, MainActivity.class);
                launch.putExtra(MainActivity.EXTRA_AUTO_START, true);
                launch.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                context.startActivity(launch);
            }
        } else {
            startForegroundServiceCompat(context, new Intent(context, DnsServerService.class));
            ServiceManager.setCurrentState(context, ServiceManager.STATE_SERVER);
        }
    }

    private void startForegroundServiceCompat(Context context, Intent intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
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

    private void updateWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_netshield);

        int state = ServiceManager.getCurrentState();
        boolean running = state != ServiceManager.STATE_STOPPED;
        int accentColor = ContextCompat.getColor(context, running ? R.color.colorAccent : R.color.text_muted);

        String statusText = running
                ? (state == ServiceManager.STATE_VPN
                    ? context.getString(R.string.status_vpn_running)
                    : context.getString(R.string.status_server_running))
                : context.getString(R.string.status_stopped);
        views.setTextViewText(R.id.tv_widget_status, statusText);
        views.setTextColor(R.id.tv_widget_status, accentColor);
        views.setInt(R.id.iv_widget_icon, "setColorFilter", accentColor);
        views.setInt(R.id.btn_widget_toggle, "setColorFilter", accentColor);

        long[] stats = DatabaseHelper.getInstance(context).getStats();
        views.setTextViewText(R.id.tv_widget_stats, stats[0] + " queries • " + stats[1] + " blocked");

        // Tap the card -> open the app.
        Intent openAppIntent = new Intent(context, MainActivity.class);
        openAppIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent openAppPendingIntent = PendingIntent.getActivity(context, 0, openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_root, openAppPendingIntent);

        // Tap the power button -> toggle directly, single click, no app open needed.
        Intent toggleIntent = new Intent(context, NetShieldWidgetProvider.class);
        toggleIntent.setAction(ACTION_TOGGLE);
        PendingIntent togglePendingIntent = PendingIntent.getBroadcast(context, appWidgetId, toggleIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.btn_widget_toggle, togglePendingIntent);

        appWidgetManager.updateAppWidget(appWidgetId, views);
    }
}
