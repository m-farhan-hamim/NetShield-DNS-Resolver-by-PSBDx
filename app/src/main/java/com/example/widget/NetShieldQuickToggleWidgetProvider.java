package com.example.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.RemoteViews;

import com.example.R;
import com.example.dns.BlocklistManager;
import com.example.service.ServiceManager;

public class NetShieldQuickToggleWidgetProvider extends AppWidgetProvider {
    private static final String TAG = "NetShieldQuickToggle";
    public static final String ACTION_TOGGLE = "com.example.widget.ACTION_QUICK_TOGGLE";

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
            WidgetActionHelper.handleToggle(context);
            refreshAllWidgets(context);
        } else if (ServiceManager.ACTION_STATE_CHANGED.equals(action)
                || BlocklistManager.ACTION_PAUSE_STATE_CHANGED.equals(action)) {
            refreshAllWidgets(context);
        }
    }

    private void refreshAllWidgets(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        ComponentName thisWidget = new ComponentName(context, NetShieldQuickToggleWidgetProvider.class);
        int[] ids = manager.getAppWidgetIds(thisWidget);
        for (int id : ids) {
            updateWidget(context, manager, id);
        }
    }

    private void updateWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_netshield_quick_toggle);

        boolean running = WidgetActionHelper.isRunning();
        int accentColor = WidgetActionHelper.resolveAccentColor(context, running);
        views.setInt(R.id.iv_quick_toggle_icon, "setColorFilter", accentColor);

        Intent toggleIntent = new Intent(context, NetShieldQuickToggleWidgetProvider.class);
        toggleIntent.setAction(ACTION_TOGGLE);
        PendingIntent togglePendingIntent = PendingIntent.getBroadcast(context, appWidgetId, toggleIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_quick_toggle_root, togglePendingIntent);

        appWidgetManager.updateAppWidget(appWidgetId, views);
    }
}
