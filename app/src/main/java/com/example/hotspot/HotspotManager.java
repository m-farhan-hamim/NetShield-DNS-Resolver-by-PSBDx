package com.example.hotspot;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Handles the parts of "hotspot device management" that are actually
 * achievable without root or system/privileged permissions: blocking a
 * device's DNS resolution (not its network access - see the Hotspot tab's
 * disclaimer) and capping DNS *query counts* per device and globally (not
 * data/bandwidth - this resolver never sees the actual bytes a device
 * transfers afterward, only its DNS lookups).
 */
public class HotspotManager {
    private static final String TAG = "HotspotManager";
    private static final String PREFS_NAME = "hotspot_prefs";
    private static final String KEY_BLOCKED_CLIENTS = "blocked_clients_json";
    private static final String KEY_CLIENT_LIMITS = "client_limits_json";
    private static final String KEY_GLOBAL_LIMIT = "global_daily_query_limit";
    private static final String KEY_CLIENT_NAMES = "client_names_json";

    private static volatile HotspotManager instance;

    private final SharedPreferences prefs;
    private final Set<String> blockedClients = new HashSet<>();
    private final Map<String, Integer> clientLimits = new HashMap<>();
    private final Map<String, String> clientNames = new HashMap<>();

    // In-memory daily counters, reset whenever the calendar day rolls over.
    private final Map<String, AtomicInteger> clientCountsToday = new ConcurrentHashMap<>();
    private final AtomicInteger globalCountToday = new AtomicInteger(0);
    private volatile String countersDateKey;

    public static synchronized HotspotManager getInstance(Context context) {
        if (instance == null) {
            instance = new HotspotManager(context.getApplicationContext());
        }
        return instance;
    }

    private HotspotManager(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        loadBlockedClients();
        loadClientLimits();
        loadClientNames();
        countersDateKey = todayKey();
    }

    private String todayKey() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    private void rolloverCountersIfNewDay() {
        String today = todayKey();
        if (!today.equals(countersDateKey)) {
            clientCountsToday.clear();
            globalCountToday.set(0);
            countersDateKey = today;
        }
    }

    // ---- Recording (called once per resolved/attempted query) ----

    /** Returns true if this query should be allowed to proceed past limit checks. */
    public boolean recordAndCheckAllowed(String clientIp) {
        rolloverCountersIfNewDay();

        int globalLimit = getGlobalDailyLimit();
        int globalCount = globalCountToday.incrementAndGet();
        if (globalLimit > 0 && globalCount > globalLimit) {
            return false;
        }

        Integer perClientLimit = clientLimits.get(clientIp);
        AtomicInteger counter = clientCountsToday.get(clientIp);
        if (counter == null) {
            counter = new AtomicInteger(0);
            AtomicInteger existing = clientCountsToday.putIfAbsent(clientIp, counter);
            if (existing != null) counter = existing;
        }
        int count = counter.incrementAndGet();
        if (perClientLimit != null && perClientLimit > 0) {
            return count <= perClientLimit;
        }
        return true;
    }

    public int getTodayQueryCount(String clientIp) {
        rolloverCountersIfNewDay();
        AtomicInteger counter = clientCountsToday.get(clientIp);
        return counter != null ? counter.get() : 0;
    }

    public int getGlobalTodayQueryCount() {
        rolloverCountersIfNewDay();
        return globalCountToday.get();
    }

    // ---- Blocking ----

    public boolean isBlocked(String clientIp) {
        return clientIp != null && blockedClients.contains(clientIp);
    }

    public void setBlocked(String clientIp, boolean blocked) {
        if (clientIp == null) return;
        if (blocked) {
            blockedClients.add(clientIp);
        } else {
            blockedClients.remove(clientIp);
        }
        saveBlockedClients();
    }

    public Set<String> getBlockedClients() {
        return new HashSet<>(blockedClients);
    }

    // ---- Per-client daily query limit ----

    /** dailyLimit <= 0 means no limit. */
    public void setClientLimit(String clientIp, int dailyLimit) {
        if (clientIp == null) return;
        if (dailyLimit <= 0) {
            clientLimits.remove(clientIp);
        } else {
            clientLimits.put(clientIp, dailyLimit);
        }
        saveClientLimits();
    }

    public int getClientLimit(String clientIp) {
        Integer limit = clientLimits.get(clientIp);
        return limit != null ? limit : 0;
    }

    // ---- Global daily query limit ----

    public void setGlobalDailyLimit(int dailyLimit) {
        prefs.edit().putInt(KEY_GLOBAL_LIMIT, Math.max(0, dailyLimit)).apply();
    }

    public int getGlobalDailyLimit() {
        return prefs.getInt(KEY_GLOBAL_LIMIT, 0);
    }

    // ---- Friendly device names (user-assigned labels) ----

    public void setClientName(String clientIp, String name) {
        if (clientIp == null) return;
        if (name == null || name.trim().isEmpty()) {
            clientNames.remove(clientIp);
        } else {
            clientNames.put(clientIp, name.trim());
        }
        saveClientNames();
    }

    public String getClientName(String clientIp) {
        return clientNames.get(clientIp);
    }

    // ---- Persistence ----

    private void loadBlockedClients() {
        blockedClients.clear();
        String json = prefs.getString(KEY_BLOCKED_CLIENTS, null);
        if (json == null) return;
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                blockedClients.add(array.getString(i));
            }
        } catch (JSONException e) {
            Log.e(TAG, "Failed to load blocked clients", e);
        }
    }

    private void saveBlockedClients() {
        JSONArray array = new JSONArray();
        for (String ip : blockedClients) array.put(ip);
        prefs.edit().putString(KEY_BLOCKED_CLIENTS, array.toString()).apply();
    }

    private void loadClientLimits() {
        clientLimits.clear();
        String json = prefs.getString(KEY_CLIENT_LIMITS, null);
        if (json == null) return;
        try {
            JSONObject obj = new JSONObject(json);
            for (Iterator<String> it = obj.keys(); it.hasNext(); ) {
                String key = it.next();
                clientLimits.put(key, obj.getInt(key));
            }
        } catch (JSONException e) {
            Log.e(TAG, "Failed to load client limits", e);
        }
    }

    private void saveClientLimits() {
        try {
            JSONObject obj = new JSONObject();
            for (Map.Entry<String, Integer> entry : clientLimits.entrySet()) {
                obj.put(entry.getKey(), entry.getValue());
            }
            prefs.edit().putString(KEY_CLIENT_LIMITS, obj.toString()).apply();
        } catch (JSONException e) {
            Log.e(TAG, "Failed to save client limits", e);
        }
    }

    private void loadClientNames() {
        clientNames.clear();
        String json = prefs.getString(KEY_CLIENT_NAMES, null);
        if (json == null) return;
        try {
            JSONObject obj = new JSONObject(json);
            for (Iterator<String> it = obj.keys(); it.hasNext(); ) {
                String key = it.next();
                clientNames.put(key, obj.getString(key));
            }
        } catch (JSONException e) {
            Log.e(TAG, "Failed to load client names", e);
        }
    }

    private void saveClientNames() {
        try {
            JSONObject obj = new JSONObject();
            for (Map.Entry<String, String> entry : clientNames.entrySet()) {
                obj.put(entry.getKey(), entry.getValue());
            }
            prefs.edit().putString(KEY_CLIENT_NAMES, obj.toString()).apply();
        } catch (JSONException e) {
            Log.e(TAG, "Failed to save client names", e);
        }
    }
}
