package com.example.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.example.service.ServiceManager;

public class NotificationActionReceiver extends BroadcastReceiver {
    public static final String ACTION_STOP_SERVICE = "com.example.dns.ACTION_STOP_SERVICE";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();

        if (ACTION_STOP_SERVICE.equals(action)) {
            // Let the running service's own onDestroy() be the sole place
            // that flips state to STOPPED - see ServiceManager.stopActiveService().
            ServiceManager.stopActiveService(context);
        }
    }
}
