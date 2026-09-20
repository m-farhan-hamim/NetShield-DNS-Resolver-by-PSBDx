package com.example.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import com.example.R;
import com.example.db.DatabaseHelper;
import com.example.dns.BlocklistManager;
import com.example.service.ServiceManager;

import java.util.Calendar;

public class NetShieldStatsWidgetProvider extends AppWidgetProvider {

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
        if (ServiceManager.ACTION_STATE_CHANGED.equals(action)
                || BlocklistManager.ACTION_PAUSE_STATE_CHANGED.equals(action)) {
            refreshAllWidgets(context);
        }
    }

    private void refreshAllWidgets(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        ComponentName thisWidget = new ComponentName(context, NetShieldStatsWidgetProvider.class);
        int[] ids = manager.getAppWidgetIds(thisWidget);
        for (int id : ids) {
            updateWidget(context, manager, id);
        }
    }

    private void updateWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_netshield_stats);

        boolean running = WidgetActionHelper.isRunning();
        int accentColor = WidgetActionHelper.resolveAccentColor(context, running);
        views.setInt(R.id.iv_stats_icon, "setColorFilter", accentColor);
        views.setTextColor(R.id.tv_stats_big_number, accentColor);

        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long startOfToday = cal.getTimeInMillis();

        DatabaseHelper db = DatabaseHelper.getInstance(context);
        int blockedToday = db.getBlockedCountBetween(startOfToday, System.currentTimeMillis());
        long[] allTimeStats = db.getStats();

        views.setTextViewText(R.id.tv_stats_big_number, String.valueOf(blockedToday));

        String statusText = running
                ? context.getString(R.string.status_active_short)
                : context.getString(R.string.status_stopped);
        views.setTextViewText(R.id.tv_stats_sub,
                context.getString(R.string.widget_stats_sub_format, allTimeStats[0], statusText));

        PendingIntent openAppPendingIntent = PendingIntent.getActivity(context, 0, WidgetActionHelper.openAppIntent(context),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_stats_root, openAppPendingIntent);

        appWidgetManager.updateAppWidget(appWidgetId, views);
    }
}
