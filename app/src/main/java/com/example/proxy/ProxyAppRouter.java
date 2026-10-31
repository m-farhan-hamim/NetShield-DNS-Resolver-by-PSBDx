package com.example.proxy;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;

import com.example.proxy.core.AppPolicy;
import com.example.proxy.core.ProxyTunEngine;
import com.example.util.AppRouting;
import com.example.util.ConnectionOwner;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-app choice of proxy or direct, for the proxy settings screen. Apps that skip the proxy still go
 * through NetShield (so its DNS filtering applies to them); only their connections leave directly.
 * This is separate from Settings > Apps and routing, which decides which apps use NetShield at all.
 */
public final class ProxyAppRouter implements ProxyTunEngine.RouteDecider {

    private final Context context;
    private final PackageManager pm;
    private final boolean includeMode;
    private final Set<String> selected;
    /** uid -> answer. The settings cannot change while the tunnel runs, so answers stay valid. */
    private final Map<Integer, Boolean> cache = new ConcurrentHashMap<>();

    public ProxyAppRouter(Context context, SharedPreferences prefs) {
        this.context = context.getApplicationContext();
        this.pm = this.context.getPackageManager();
        this.includeMode = AppRouting.isIncludeMode(prefs, AppRouting.Scope.PROXY);
        this.selected = includeMode
                ? AppRouting.getIncluded(prefs, AppRouting.Scope.PROXY)
                : AppRouting.getExcluded(prefs, AppRouting.Scope.PROXY);
    }

    /** False when nothing is chosen, so the engine can skip the per-connection lookup entirely. */
    public boolean isActive() {
        return !selected.isEmpty();
    }

    @Override
    public boolean useProxy(String clientIp, int clientPort, String destIp, int destPort) {
        int uid = ConnectionOwner.tcpOwnerUid(context, clientIp, clientPort, destIp, destPort);
        if (uid == ConnectionOwner.UNKNOWN) {
            return true; // cannot tell who it is: keep it inside the proxy
        }
        Boolean cached = cache.get(uid);
        if (cached != null) {
            return cached;
        }
        String[] names = pm.getPackagesForUid(uid);
        List<String> packages = names == null ? Collections.<String>emptyList() : Arrays.asList(names);
        boolean result = AppPolicy.useProxy(includeMode, selected, packages);
        cache.put(uid, result);
        return result;
    }
}
