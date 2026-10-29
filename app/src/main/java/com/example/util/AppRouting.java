package com.example.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.VpnService;

import androidx.annotation.Nullable;

import com.example.R;

import java.util.HashSet;
import java.util.Set;

/**
 * Which apps go through NetShield's tunnel. One setting, shared by the DNS resolver and the
 * proxy mode, because both run inside the same VpnService.
 *
 * Two modes, and Android does not allow mixing them on one VPN:
 * <ul>
 *   <li><b>Exclude</b> (the original behaviour, and still the default): every app uses
 *       NetShield except the ones you pick, which bypass it
 *       ({@link VpnService.Builder#addDisallowedApplication}).</li>
 *   <li><b>Include</b> (GitHub issue #26): ONLY the apps you pick use NetShield. Everything
 *       else - including system processes - stays on the normal direct connection
 *       ({@link VpnService.Builder#addAllowedApplication}).</li>
 * </ul>
 * The two lists are stored separately so switching mode never throws a list away.
 */
public final class AppRouting {

    public static final String KEY_MODE = "split_tunnel_mode";
    /** Historical name from before include mode existed: the apps that BYPASS the tunnel. */
    public static final String KEY_EXCLUDE = "split_tunnel_apps";
    /** The apps that are the ONLY ones to use the tunnel while include mode is on. */
    public static final String KEY_INCLUDE = "split_tunnel_include_apps";

    public static final String MODE_EXCLUDE = "exclude";
    public static final String MODE_INCLUDE = "include";

    private AppRouting() {
    }

    public static boolean isIncludeMode(SharedPreferences prefs) {
        return MODE_INCLUDE.equals(prefs.getString(KEY_MODE, MODE_EXCLUDE));
    }

    public static Set<String> getExcluded(SharedPreferences prefs) {
        return copyOf(prefs.getStringSet(KEY_EXCLUDE, null));
    }

    public static Set<String> getIncluded(SharedPreferences prefs) {
        return copyOf(prefs.getStringSet(KEY_INCLUDE, null));
    }

    /** Saves the mode and both lists in one commit-free (apply) edit. */
    public static void save(SharedPreferences prefs, boolean includeMode, Set<String> excluded, Set<String> included) {
        prefs.edit()
                .putString(KEY_MODE, includeMode ? MODE_INCLUDE : MODE_EXCLUDE)
                .putStringSet(KEY_EXCLUDE, new HashSet<>(excluded))
                .putStringSet(KEY_INCLUDE, new HashSet<>(included))
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
     * Applies the saved routing to a VPN being built. Must be called exactly once per Builder,
     * after the routes are set.
     *
     * @param ownPackage   this app's package name
     * @param proxyPackage the user's proxy app while proxy mode is on, otherwise null
     */
    public static void apply(VpnService.Builder builder, SharedPreferences prefs,
                             String ownPackage, @Nullable String proxyPackage) {
        if (isIncludeMode(prefs)) {
            int added = 0;
            for (String pkg : effectiveIncludeList(getIncluded(prefs), proxyPackage)) {
                try {
                    builder.addAllowedApplication(pkg);
                    added++;
                } catch (Exception ignored) {
                    // not installed any more: nothing to include
                }
            }
            if (added == 0) {
                // An allow-list with no entries means "everyone" to Android, which is the exact
                // opposite of what include mode promises. Allow only this app so that nothing
                // else is pulled into the tunnel until the user has picked apps.
                try {
                    builder.addAllowedApplication(ownPackage);
                } catch (Exception ignored) {
                }
            }
            return;
        }

        if (proxyPackage != null) {
            try {
                builder.addDisallowedApplication(proxyPackage);
            } catch (PackageManager.NameNotFoundException ignored) {
                // not installed: nothing to exclude
            }
        }
        for (String pkg : getExcluded(prefs)) {
            try {
                builder.addDisallowedApplication(pkg);
            } catch (Exception ignored) {
            }
        }
    }

    /** One line for the Settings card and the proxy screen. */
    public static String summary(Context context, SharedPreferences prefs) {
        if (isIncludeMode(prefs)) {
            int n = getIncluded(prefs).size();
            return n == 0
                    ? context.getString(R.string.routing_summary_include_none)
                    : context.getResources().getQuantityString(R.plurals.routing_summary_include, n, n);
        }
        int n = getExcluded(prefs).size();
        return n == 0
                ? context.getString(R.string.routing_summary_exclude_none)
                : context.getResources().getQuantityString(R.plurals.routing_summary_exclude, n, n);
    }

    private static Set<String> copyOf(@Nullable Set<String> stored) {
        return stored == null ? new HashSet<String>() : new HashSet<>(stored);
    }
}
