package com.example.util;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;

/** The user's light/dark choice. "system" follows the phone and is the default. */
public final class ThemeManager {
    public static final String SYSTEM = "system";
    public static final String LIGHT = "light";
    public static final String DARK = "dark";
    private static final String PREFS = "ui_prefs";
    private static final String KEY = "app_theme";

    private ThemeManager() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static String get(Context context) {
        String v = prefs(context).getString(KEY, SYSTEM);
        return LIGHT.equals(v) || DARK.equals(v) ? v : SYSTEM;
    }

    /** Saves the choice and applies it; open screens are recreated by AppCompat. */
    public static void set(Context context, String mode) {
        prefs(context).edit().putString(KEY, mode).apply();
        apply(mode);
    }

    /** Call once at startup, before any screen is created. */
    public static void apply(Context context) {
        apply(get(context));
    }

    private static void apply(String mode) {
        int night = LIGHT.equals(mode) ? AppCompatDelegate.MODE_NIGHT_NO
                : DARK.equals(mode) ? AppCompatDelegate.MODE_NIGHT_YES
                : AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        AppCompatDelegate.setDefaultNightMode(night);
    }
}
