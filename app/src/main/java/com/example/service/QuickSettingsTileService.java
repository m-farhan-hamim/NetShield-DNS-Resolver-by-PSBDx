package com.example.service;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.drawable.Icon;
import android.net.VpnService;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.util.Log;

import androidx.core.content.ContextCompat;

import com.example.R;
import com.example.dns.DnsResolverEngine;
import com.example.ui.MainActivity;

/**
 * Lets the user activate/deactivate the resolver with a single tap from the
 * Quick Settings panel (the same "control panel" that holds Wi-Fi,
 * Flashlight, etc.) - no need to open the app.
 */
public class QuickSettingsTileService extends TileService {
    private static final String TAG = "QuickSettingsTile";

    private final android.content.BroadcastReceiver stateReceiver = new android.content.BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateTile();
        }
    };
    private boolean receiverRegistered = false;

    @Override
    public void onStartListening() {
        super.onStartListening();
        updateTile();
        if (!receiverRegistered) {
            IntentFilter filter = new IntentFilter(ServiceManager.ACTION_STATE_CHANGED);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.registerReceiver(this, stateReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(stateReceiver, filter);
            }
            receiverRegistered = true;
        }
    }

    @Override
    public void onStopListening() {
        if (receiverRegistered) {
            try {
                unregisterReceiver(stateReceiver);
            } catch (Exception ignored) {
            }
            receiverRegistered = false;
        }
        super.onStopListening();
    }

    @Override
    public void onClick() {
        super.onClick();
        int state = ServiceManager.getCurrentState();
        Log.d(TAG, "onClick: currentState=" + state);
        if (state != ServiceManager.STATE_STOPPED) {
            ServiceManager.stopActiveService(this);
            ServiceManager.setCurrentState(this, ServiceManager.STATE_STOPPED);
            updateTile();
        } else {
            startFromTile();
        }
    }

    private void startFromTile() {
        SharedPreferences prefs = getSharedPreferences(DnsResolverEngine.PREFS_NAME, MODE_PRIVATE);
        String mode = prefs.getString("operation_mode", "VPN");
        Log.d(TAG, "startFromTile: mode=" + mode);

        if ("VPN".equalsIgnoreCase(mode)) {
            Intent vpnPrepare = VpnService.prepare(this);
            if (vpnPrepare == null) {
                // Permission already granted - start right here, no need to open the app.
                startForegroundServiceCompat(new Intent(this, DnsVpnService.class));
                ServiceManager.setCurrentState(this, ServiceManager.STATE_VPN);
                updateTile();
            } else {
                // First-time VPN consent can only be granted through an Activity.
                launchAppToFinishStart();
            }
        } else {
            startForegroundServiceCompat(new Intent(this, DnsServerService.class));
            ServiceManager.setCurrentState(this, ServiceManager.STATE_SERVER);
            updateTile();
        }
    }

    private void startForegroundServiceCompat(Intent intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    private void launchAppToFinishStart() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra(MainActivity.EXTRA_AUTO_START, true);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            startActivityAndCollapse(pendingIntent);
        } else {
            //noinspection deprecation - only reached below API 34
            startActivityAndCollapse(intent);
        }
    }

    private void updateTile() {
        Tile tile = getQsTile();
        if (tile == null) return;

        int state = ServiceManager.getCurrentState();
        boolean running = state != ServiceManager.STATE_STOPPED;

        tile.setState(running ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.setLabel(getString(R.string.qs_tile_label));
        tile.setIcon(Icon.createWithResource(this, R.drawable.ic_shield));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.setSubtitle(running
                    ? (state == ServiceManager.STATE_VPN
                        ? getString(R.string.status_vpn_running)
                        : getString(R.string.status_server_running))
                    : getString(R.string.status_stopped));
        }
        tile.updateTile();
    }
}
