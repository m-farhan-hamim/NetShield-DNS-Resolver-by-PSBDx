package com.example.widget;

import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.content.ContextCompat;

import com.example.R;
import com.example.service.ServiceManager;
import com.example.ui.MainActivity;

/** Shared by every NetShield widget provider - keeps the toggle/color logic in one place instead of tripled across widgets. */
final class WidgetActionHelper {

    private WidgetActionHelper() {
    }

    static boolean isRunning() {
        return ServiceManager.getCurrentState() != ServiceManager.STATE_STOPPED;
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
