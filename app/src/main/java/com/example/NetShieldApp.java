package com.example;

import android.app.Application;

import com.example.util.ThemeManager;

public class NetShieldApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        ThemeManager.apply(this); // before any screen is created, so there is no flash of the wrong theme
    }
}
