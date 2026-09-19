package com.example.trust;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Domains or apps added here get a full, unconditional bypass of every
 * blocking/limit check in the app - the sinkhole blocklist, per-device
 * hotspot blocks, and per-device/global query limits. Seeded on first run
 * with "psbdx.com" (this app's own update-check endpoint - see
 * UpdateChecker - blocking it would prevent the app from ever learning
 * about new versions or security fixes), which the user is free to remove
 * like any other entry.
 */
public class TrustedListManager {
    private static final String TAG = "TrustedListManager";
    private static final String PREFS_NAME = "trusted_list_prefs";
    private static final String KEY_TRUSTED_DOMAINS = "trusted_domains_json";
    private static final String KEY_TRUSTED_APPS = "trusted_apps_json";
    private static final String KEY_SEEDED = "seeded_default_domain";

    /** The one domain pre-populated on first run. Purely a starting default - not special-cased anywhere else. */
    public static final String DEFAULT_TRUSTED_DOMAIN = "psbdx.com";

    private static volatile TrustedListManager instance;

    private final SharedPreferences prefs;
    private final Set<String> trustedDomains = ConcurrentHashMap.newKeySet();
    private final Set<String> trustedApps = ConcurrentHashMap.newKeySet();

    public static synchronized TrustedListManager getInstance(Context context) {
        if (instance == null) {
            instance = new TrustedListManager(context.getApplicationContext());
        }
        return instance;
    }

    private TrustedListManager(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        loadDomains();
        loadApps();
        if (!prefs.getBoolean(KEY_SEEDED, false)) {
            trustedDomains.add(DEFAULT_TRUSTED_DOMAIN);
            saveDomains();
            prefs.edit().putBoolean(KEY_SEEDED, true).apply();
        }
    }

    // ---- Domains ----

    public boolean isDomainTrusted(String domain) {
        if (domain == null || domain.isEmpty()) return false;
        String d = domain.toLowerCase(Locale.ROOT);
        if (trustedDomains.contains(d)) return true;
        // Subdomain match: trusting "psbdx.com" also trusts "www.psbdx.com", "api.psbdx.com", etc.
        int dot = d.indexOf('.');
        while (dot >= 0) {
            String parent = d.substring(dot + 1);
            if (trustedDomains.contains(parent)) return true;
            dot = d.indexOf('.', dot + 1);
        }
        return false;
    }

    public void addTrustedDomain(String domain) {
        if (domain == null || domain.trim().isEmpty()) return;
        trustedDomains.add(domain.trim().toLowerCase(Locale.ROOT));
        saveDomains();
    }

    public void removeTrustedDomain(String domain) {
        if (domain == null) return;
        trustedDomains.remove(domain.trim().toLowerCase(Locale.ROOT));
        saveDomains();
    }

    public List<String> getTrustedDomains() {
        List<String> list = new ArrayList<>(trustedDomains);
        Collections.sort(list);
        return list;
    }

    // ---- Apps ----

    public boolean isAppTrusted(String packageName) {
        return packageName != null && trustedApps.contains(packageName);
    }

    public void addTrustedApp(String packageName) {
        if (packageName == null || packageName.trim().isEmpty()) return;
        trustedApps.add(packageName.trim());
        saveApps();
    }

    public void removeTrustedApp(String packageName) {
        if (packageName == null) return;
        trustedApps.remove(packageName);
        saveApps();
    }

    public List<String> getTrustedApps() {
        List<String> list = new ArrayList<>(trustedApps);
        Collections.sort(list);
        return list;
    }

    // ---- Persistence ----

    private void loadDomains() {
        trustedDomains.clear();
        loadSet(KEY_TRUSTED_DOMAINS, trustedDomains);
    }

    private void loadApps() {
        trustedApps.clear();
        loadSet(KEY_TRUSTED_APPS, trustedApps);
    }

    private void loadSet(String key, Set<String> target) {
        String json = prefs.getString(key, null);
        if (json == null) return;
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                target.add(array.getString(i));
            }
        } catch (JSONException e) {
            Log.e(TAG, "Failed to load " + key, e);
        }
    }

    private void saveDomains() {
        saveSet(KEY_TRUSTED_DOMAINS, trustedDomains);
    }

    private void saveApps() {
        saveSet(KEY_TRUSTED_APPS, trustedApps);
    }

    private void saveSet(String key, Set<String> source) {
        JSONArray array = new JSONArray();
        for (String s : source) array.put(s);
        prefs.edit().putString(key, array.toString()).apply();
    }
}
