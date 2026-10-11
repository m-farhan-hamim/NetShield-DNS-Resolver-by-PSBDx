package com.example.proxy;

import android.content.Context;
import android.content.Intent;

/** Whether the proxy NetShield is using currently works; set by the VPN service, shown by the UI. */
public final class ProxyStatus {
    public static final String ACTION_STATUS = "com.example.proxy.ACTION_STATUS";
    private static volatile boolean down;

    private ProxyStatus() {
    }

    public static boolean isDown() {
        return down;
    }

    public static void set(Context context, boolean isDown) {
        down = isDown;
        Intent intent = new Intent(ACTION_STATUS);
        intent.setPackage(context.getPackageName());
        context.sendBroadcast(intent);
    }
}
