package com.example.service;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.util.Log;

import androidx.core.content.ContextCompat;

import com.example.R;
import com.example.dns.BlocklistManager;

/**
 * A dedicated Quick Settings tile for pausing/resuming blocklist protection
 * (15 minutes per tap), separate from the main VPN/server on-off tile since
 * they're independent controls - the resolver can keep running while
 * blocking is temporarily paused.
 */
public class PauseTileService extends TileService {
    private static final String TAG = "PauseTileService";
    private static final long PAUSE_DURATION_MS = 15 * 60 * 1000L;

    private final android.content.BroadcastReceiver pauseReceiver = new android.content.BroadcastReceiver() {
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
            IntentFilter filter = new IntentFilter(BlocklistManager.ACTION_PAUSE_STATE_CHANGED);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.registerReceiver(this, pauseReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(pauseReceiver, filter);
            }
            receiverRegistered = true;
        }
    }

    @Override
    public void onStopListening() {
        if (receiverRegistered) {
            try {
                unregisterReceiver(pauseReceiver);
            } catch (Exception ignored) {
            }
            receiverRegistered = false;
        }
        super.onStopListening();
    }

    @Override
    public void onClick() {
        super.onClick();
        BlocklistManager manager = BlocklistManager.getInstance(this);
        boolean wasPaused = manager.isPaused();
        Log.d(TAG, "onClick: wasPaused=" + wasPaused);
        if (wasPaused) {
            manager.resumeProtection();
        } else {
            manager.pauseProtection(PAUSE_DURATION_MS);
        }
        updateTile();
    }

    private void updateTile() {
        Tile tile = getQsTile();
        if (tile == null) return;

        BlocklistManager manager = BlocklistManager.getInstance(this);
        boolean paused = manager.isPaused();

        tile.setLabel(getString(R.string.pause_tile_label));
        tile.setIcon(Icon.createWithResource(this, R.drawable.ic_power));
        if (paused) {
            long remainingMin = Math.max(1, manager.getRemainingPauseMillis() / 60000);
            tile.setState(Tile.STATE_ACTIVE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.setSubtitle(getString(R.string.pause_tile_subtitle_paused, remainingMin));
            }
        } else {
            tile.setState(Tile.STATE_INACTIVE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.setSubtitle(getString(R.string.pause_tile_subtitle_active));
            }
        }
        tile.updateTile();
    }
}
