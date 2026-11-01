package com.example.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.VpnService;
import android.util.Log;

import androidx.annotation.Nullable;

import com.example.R;

import java.util.HashSet;
import java.util.Set;

/**
 * Which apps go where. There are two separate settings, each with an exclude and an include mode:
 *
 * <ul>
 *   <li>{@link Scope#APP_WIDE} (Settings > Apps and routing): which apps use NetShield AT ALL. An app
 *       outside it is not filtered and not proxied; it keeps the plain direct connection.</li>
 *   <li>{@link Scope#PROXY} (the proxy screen): which apps use the PROXY. An app that skips the proxy
 *       is still filtered by NetShield, it just connects directly.</li>
 * </ul>
 *
 * Android does not allow mixing allowed and disallowed apps on one VPN, hence the two modes, and the
 * two lists of a scope are stored separately so switching mode never throws a list away.
 */
public final class AppRouting {

    public enum Scope {
        APP_WIDE("split_tunnel_mode", "split_tunnel_apps", "split_tunnel_include_apps",
                R.string.routing_dialog_title, R.string.routing_mode_exclude, R.string.routing_mode_include,
                R.string.routing_desc_exclude, R.string.routing_desc_include,
                R.string.routing_summary_exclude_none, R.plurals.routing_summary_exclude,
                R.string.routing_summary_include_none, R.plurals.routing_summary_include,
                R.string.routing_saved, R.string.routing_include_empty),
        PROXY("proxy_route_mode", "proxy_route_exclude_apps", "proxy_route_include_apps",
                R.string.proxy_routing_dialog_title, R.string.proxy_mode_exclude, R.string.proxy_mode_include,
                R.string.proxy_desc_exclude, R.string.proxy_desc_include,
                R.string.proxy_summary_exclude_none, R.plurals.proxy_summary_exclude,
                R.string.proxy_summary_include_none, R.plurals.proxy_summary_include,
                R.string.routing_saved, R.string.proxy_include_empty);

        final String modeKey;
        final String excludeKey;
        final String includeKey;
        public final int titleRes;
        public final int modeExcludeRes;
        public final int modeIncludeRes;
        public final int descExcludeRes;
        public final int descIncludeRes;
        final int summaryExcludeNone;
        final int summaryExcludePlural;
        final int summaryIncludeNone;
        final int summaryIncludePlural;
        public final int savedRes;
        public final int includeEmptyRes;

        Scope(String modeKey, String excludeKey, String includeKey, int titleRes, int modeExcludeRes,
              int modeIncludeRes, int descExcludeRes, int descIncludeRes, int summaryExcludeNone,
              int summaryExcludePlural, int summaryIncludeNone, int summaryIncludePlural, int savedRes,
              int includeEmptyRes) {
            this.modeKey = modeKey;
            this.excludeKey = excludeKey;
            this.includeKey = includeKey;
            this.titleRes = titleRes;
            this.modeExcludeRes = modeExcludeRes;
            this.modeIncludeRes = modeIncludeRes;
            this.descExcludeRes = descExcludeRes;
            this.descIncludeRes = descIncludeRes;
            this.summaryExcludeNone = summaryExcludeNone;
            this.summaryExcludePlural = summaryExcludePlural;
            this.summaryIncludeNone = summaryIncludeNone;
            this.summaryIncludePlural = summaryIncludePlural;
            this.savedRes = savedRes;
            this.includeEmptyRes = includeEmptyRes;
        }
    }

    public static final String MODE_EXCLUDE = "exclude";
    public static final String MODE_INCLUDE = "include";

    private static final String TAG = "AppRouting";

    private AppRouting() {
    }

    public static boolean isIncludeMode(SharedPreferences prefs, Scope scope) {
        return MODE_INCLUDE.equals(prefs.getString(scope.modeKey, MODE_EXCLUDE));
    }

    public static Set<String> getExcluded(SharedPreferences prefs, Scope scope) {
        return copyOf(prefs.getStringSet(scope.excludeKey, null));
    }

    public static Set<String> getIncluded(SharedPreferences prefs, Scope scope) {
        return copyOf(prefs.getStringSet(scope.includeKey, null));
    }

    /** Saves the mode and both lists of a scope in one edit. */
    public static void save(SharedPreferences prefs, Scope scope, boolean includeMode,
                            Set<String> excluded, Set<String> included) {
        prefs.edit()
                .putString(scope.modeKey, includeMode ? MODE_INCLUDE : MODE_EXCLUDE)
                .putStringSet(scope.excludeKey, new HashSet<>(excluded))
                .putStringSet(scope.includeKey, new HashSet<>(included))
                .apply();
    }

    /**
     * The apps that may really be handed to addAllowedApplication. The proxy app is always
     * removed: its own connections have to stay OUTSIDE the tunnel or they would loop straight
     * back into it. Pure function, so it is easy to reason about.
     */
    public static Set<String> effectiveIncludeList(Set<String> selected, @Nullable String proxyPackage) {
        Set<String> out = new HashSet<>(selected);
        if (proxyPackage != null) {
            out.remove(proxyPackage);
        }
        return out;
    }

    /**
     * Applies the APP_WIDE routing to a VPN being built. Must be called exactly once per Builder,
     * after the routes are set.
     *
     * @param ownPackage   this app's package name
     * @param proxyPackage the user's proxy app while proxy mode is on, otherwise null
     */
    public static void apply(VpnService.Builder builder, SharedPreferences prefs,
                             String ownPackage, @Nullable String proxyPackage) {
        final Scope scope = Scope.APP_WIDE;
        if (isIncludeMode(prefs, scope)) {
            int added = 0;
            for (String pkg : effectiveIncludeList(getIncluded(prefs, scope), proxyPackage)) {
                try {
                    builder.addAllowedApplication(pkg);
                    added++;
                } catch (Exception e) {
                    Log.w(TAG, "Cannot include " + pkg + ": " + e.getMessage());
                }
            }
            if (added == 0) {
                // An allow-list with no entries means "everyone" to Android, which is the exact
                // opposite of what include mode promises. Allow only this app so that nothing
                // else is pulled into the tunnel until the user has picked apps.
                try {
                    builder.addAllowedApplication(ownPackage);
                } catch (Exception e) {
                    Log.w(TAG, "Cannot restrict include mode to this app: " + e.getMessage());
                }
            }
            return;
        }

        if (proxyPackage != null) {
            try {
                builder.addDisallowedApplication(proxyPackage);
            } catch (PackageManager.NameNotFoundException e) {
                Log.d(TAG, "Proxy app not installed, nothing to exclude");
            }
        }
        for (String pkg : getExcluded(prefs, scope)) {
            try {
                builder.addDisallowedApplication(pkg);
            } catch (Exception e) {
                Log.w(TAG, "Cannot exclude " + pkg + ": " + e.getMessage());
            }
        }
    }

    /** One line for the Settings card and the proxy screen. */
    public static String summary(Context context, SharedPreferences prefs, Scope scope) {
        if (isIncludeMode(prefs, scope)) {
            int n = getIncluded(prefs, scope).size();
            return n == 0
                    ? context.getString(scope.summaryIncludeNone)
                    : context.getResources().getQuantityString(scope.summaryIncludePlural, n, n);
        }
        int n = getExcluded(prefs, scope).size();
        return n == 0
                ? context.getString(scope.summaryExcludeNone)
                : context.getResources().getQuantityString(scope.summaryExcludePlural, n, n);
    }

    private static Set<String> copyOf(@Nullable Set<String> stored) {
        return stored == null ? new HashSet<String>() : new HashSet<>(stored);
    }
}
