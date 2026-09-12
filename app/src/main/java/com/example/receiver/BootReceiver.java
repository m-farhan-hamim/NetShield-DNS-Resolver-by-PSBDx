package com.example.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import com.example.dns.DnsResolverEngine;
import com.example.service.DnsServerService;
import com.example.service.DnsVpnService;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            SharedPreferences prefs = context.getSharedPreferences(DnsResolverEngine.PREFS_NAME, Context.MODE_PRIVATE);
            boolean autoStart = prefs.getBoolean("auto_start_on_boot", false);
            if (!autoStart) return;

            String mode = prefs.getString("operation_mode", "VPN");
            Intent serviceIntent;
            if ("SERVER".equalsIgnoreCase(mode)) {
                serviceIntent = new Intent(context, DnsServerService.class);
            } else {
                serviceIntent = new Intent(context, DnsVpnService.class);
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }
        }
    }
}
