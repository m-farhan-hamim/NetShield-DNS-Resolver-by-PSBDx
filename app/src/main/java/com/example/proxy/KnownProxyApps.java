package com.example.proxy;

import android.content.Context;
import android.content.pm.PackageManager;

import com.example.R;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Proxy apps NetShield knows how to talk to, with the port each one listens on by default. */
public final class KnownProxyApps {
    private KnownProxyApps() {
    }

    public static final class App {
        public final String[] packages;
        public final String label;
        public final int port;
        public final int hintRes;

        App(String[] packages, String label, int port, int hintRes) {
            this.packages = packages;
            this.label = label;
            this.port = port;
            this.hintRes = hintRes;
        }
    }

    public static final App V2RAYNG = new App(new String[]{"com.v2ray.ang"}, "v2rayNG", 10808, R.string.proxy_hint_v2rayng);
    public static final App ORBOT = new App(new String[]{"org.torproject.android"}, "Orbot", 9050, R.string.proxy_hint_orbot);
    public static final App INVIZIBLE = new App(new String[]{
            "pan.alexander.tordnscrypt.stable", "pan.alexander.tordnscrypt.gp", "pan.alexander.tordnscrypt"},
            "InviZible", 9050, R.string.proxy_hint_invizible);

    public static List<App> all() {
        return Collections.unmodifiableList(Arrays.asList(V2RAYNG, ORBOT, INVIZIBLE));
    }

    /** The installed package of this app, or null if it is not installed. */
    public static String installedPackage(Context context, App app) {
        PackageManager pm = context.getPackageManager();
        for (String pkg : app.packages) {
            try {
                pm.getPackageInfo(pkg, 0);
                return pkg;
            } catch (PackageManager.NameNotFoundException ignored) {
                // try the next variant
            }
        }
        return null;
    }

    public static App byPackage(String pkg) {
        if (pkg == null) {
            return null;
        }
        for (App a : all()) {
            for (String p : a.packages) {
                if (p.equals(pkg)) {
                    return a;
                }
            }
        }
        return null;
    }
}
