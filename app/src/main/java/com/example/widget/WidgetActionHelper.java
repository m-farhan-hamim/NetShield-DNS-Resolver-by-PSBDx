package com.example.widget;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.VpnService;
import android.os.Build;

import androidx.core.content.ContextCompat;

import com.example.R;
import com.example.dns.DnsResolverEngine;
import com.example.service.DnsServerService;
import com.example.service.DnsVpnService;
import com.example.service.ServiceManager;
import com.example.ui.MainActivity;

/** Shared by every NetShield widget provider - keeps the toggle/color logic in one place instead of tripled across widgets. */
final class WidgetActionHelper {

    private WidgetActionHelper() {
    }

    static boolean isRunning() {
        return ServiceManager.getCurrentState() != ServiceManager.STATE_STOPPED;
    }

    static void handleToggle(Context context) {
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

    static void startForegroundServiceCompat(Context context, Intent intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    /** Prefers the Material You wallpaper-derived accent (API 31+); falls back to the app's fixed accent otherwise. */
    static int resolveAccentColor(Context context, boolean running) {
        if (!running) {
            return ContextCompat.getColor(context, R.color.text_muted);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                return ContextCompat.getColor(context, android.R.color.system_accent1_400);
            } catch (Exception ignored) {
                // Fall through to the fixed color if the OEM's resource table is missing this.
            }
        }
        return ContextCompat.getColor(context, R.color.colorAccent);
    }

    static Intent openAppIntent(Context context) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return intent;
    }
}
