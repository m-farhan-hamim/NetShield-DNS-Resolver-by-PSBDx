package com.example.proxy;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.proxy.core.ProxyEndpoint;

import java.util.Locale;

/**
 * The user's proxy choice. Stored in its own prefs file that is excluded from Android backups:
 * it may hold a proxy password, and restoring "proxy on" onto a new phone without the proxy app
 * would cut off the internet.
 */
public final class ProxySettings {
    public static final String PREFS_NAME = "proxy_settings";
    public static final String DEFAULT_HOST = "127.0.0.1";
    public static final int DEFAULT_PORT = 10808;

    public final boolean enabled;
    /** The proxy app's package (kept outside the tunnel), or null for "another app / custom". */
    public final String packageName;
    public final String host;
    public final int port;
    public final String username;
    public final String password;

    public ProxySettings(boolean enabled, String packageName, String host, int port, String username, String password) {
        this.enabled = enabled;
        this.packageName = packageName;
        this.host = host == null || host.trim().isEmpty() ? DEFAULT_HOST : host.trim();
        this.port = port;
        this.username = username == null ? "" : username;
        this.password = password == null ? "" : password;
    }

    public static ProxySettings load(Context context) {
        SharedPreferences p = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return new ProxySettings(
                p.getBoolean("enabled", false),
                p.getString("package", null),
                p.getString("host", DEFAULT_HOST),
                p.getInt("port", DEFAULT_PORT),
                p.getString("username", ""),
                p.getString("password", ""));
    }

    public void save(Context context) {
        context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putBoolean("enabled", enabled)
                .putString("package", packageName)
                .putString("host", host)
                .putInt("port", port)
                .putString("username", username)
                .putString("password", password)
                .apply();
    }

    public ProxySettings withEnabled(boolean value) {
        return new ProxySettings(value, packageName, host, port, username, password);
    }

    /** The proxy must be an app on this phone: remote proxies are not supported in this version. */
    public static boolean isLoopback(String host) {
        String h = host == null ? "" : host.trim().toLowerCase(Locale.ROOT);
        return h.equals("localhost") || h.startsWith("127.");
    }

    public boolean isUsable() {
        return enabled && port >= 1 && port <= 65535 && isLoopback(host);
    }

    public ProxyEndpoint endpoint() {
        return new ProxyEndpoint(host, port, username, password);
    }
}
