package com.example.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.example.service.DnsServerService;
import com.example.service.DnsVpnService;
import com.example.service.ServiceManager;

public class NotificationActionReceiver extends BroadcastReceiver {
    public static final String ACTION_STOP_SERVICE = "com.example.dns.ACTION_STOP_SERVICE";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();

        if (ACTION_STOP_SERVICE.equals(action)) {
            context.stopService(new Intent(context, DnsServerService.class));
            context.stopService(new Intent(context, DnsVpnService.class));
            ServiceManager.setCurrentState(context, ServiceManager.STATE_STOPPED);
        }
    }
}
