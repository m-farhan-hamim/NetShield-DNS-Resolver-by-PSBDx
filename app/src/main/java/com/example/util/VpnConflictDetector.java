package com.example.util;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

import com.example.service.ServiceManager;

/**
 * Android lets only one app hold the VPN at a time, so starting NetShield's VPN mode
 * silently disconnects any other VPN or proxy app (v2rayNG, WireGuard, ...).
 * This tells the start flow whether that is about to happen.
 */
public final class VpnConflictDetector {
    private VpnConflictDetector() {
    }

    /**
     * True when a VPN from another app currently carries the device's default network.
     * A split-tunnel VPN that is not the default network is not detected.
     */
    public static boolean isOtherVpnActive(Context context) {
        if (ServiceManager.getCurrentState() == ServiceManager.STATE_VPN) {
            return false; // the active VPN is NetShield's own
        }
        try {
            ConnectivityManager cm =
                    (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) {
                return false;
            }
            Network active = cm.getActiveNetwork();
            if (active == null) {
                return false;
            }
            NetworkCapabilities caps = cm.getNetworkCapabilities(active);
            return caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN);
        } catch (SecurityException e) {
            return false;
        }
    }
}
