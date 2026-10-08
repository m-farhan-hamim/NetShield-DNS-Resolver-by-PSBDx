package com.example.vpn;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * JSON-backed store for proxy subscription URLs. The URLs carry a secret token, so this prefs
 * file is excluded from Android backups (see backup_rules.xml / data_extraction_rules.xml),
 * and NetShield never fetches or logs them: it only hands them to the user's proxy app.
 */
public class ProxySubscriptionStore {
    private static final String TAG = "ProxySubscriptionStore";
    /** Keep in sync with the backup exclusions: the file is PREFS_NAME + ".xml". */
    public static final String PREFS_NAME = "proxy_subscriptions";
    public static final String V2RAYNG_PACKAGE = "com.v2ray.ang";
    public static final int MAX_URL_LENGTH = 2048;
    private static final String KEY_SUBSCRIPTIONS = "subscriptions_json";

    private final SharedPreferences prefs;

    public ProxySubscriptionStore(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public List<ProxySubscription> getAll() {
        List<ProxySubscription> result = new ArrayList<>();
        String json = prefs.getString(KEY_SUBSCRIPTIONS, null);
        if (json == null) return result;
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                result.add(new ProxySubscription(
                        obj.getString("id"),
                        obj.getString("name"),
                        obj.getString("url"),
                        obj.optLong("addedAt", 0L)));
            }
        } catch (JSONException e) {
            Log.e(TAG, "Failed to parse stored subscriptions");
        }
        return result;
    }

    public boolean contains(String url) {
        for (ProxySubscription s : getAll()) {
            if (s.getUrl().equals(url)) return true;
        }
        return false;
    }

    /** @param url already checked with {@link #normalizeUrl(String)} */
    public ProxySubscription add(String name, String url) {
        String label = name == null ? "" : name.trim();
        if (label.isEmpty()) {
            label = displayHost(url);
        }
        ProxySubscription sub = new ProxySubscription(
                UUID.randomUUID().toString(), label, url, System.currentTimeMillis());
        List<ProxySubscription> all = getAll();
        all.add(sub);
        saveAll(all);
        return sub;
    }

    public void delete(String id) {
        List<ProxySubscription> kept = new ArrayList<>();
        for (ProxySubscription s : getAll()) {
            if (!s.getId().equals(id)) kept.add(s);
        }
        saveAll(kept);
    }

    /**
     * Returns the trimmed URL if it is an https URL with a host and no whitespace or control
     * characters, otherwise null. https only: the URL carries a token that must not travel in clear.
     */
    public static String normalizeUrl(String input) {
        if (input == null) return null;
        String url = input.trim();
        if (url.isEmpty() || url.length() > MAX_URL_LENGTH) return null;
        for (int i = 0; i < url.length(); i++) {
            char c = url.charAt(i);
            if (Character.isWhitespace(c) || Character.isISOControl(c)) return null;
        }
        Uri uri = Uri.parse(url);
        if (!"https".equalsIgnoreCase(uri.getScheme())) return null;
        String host = uri.getHost();
        if (host == null || host.isEmpty()) return null;
        return url;
    }

    /** Host only; the path and query usually hold the secret token, so they are never displayed. */
    public static String displayHost(String url) {
        String host = Uri.parse(url).getHost();
        return host == null ? "subscription" : host;
    }

    private void saveAll(List<ProxySubscription> subs) {
        try {
            JSONArray array = new JSONArray();
            for (ProxySubscription s : subs) {
                JSONObject obj = new JSONObject();
                obj.put("id", s.getId());
                obj.put("name", s.getName());
                obj.put("url", s.getUrl());
                obj.put("addedAt", s.getAddedAt());
                array.put(obj);
            }
            prefs.edit().putString(KEY_SUBSCRIPTIONS, array.toString()).apply();
        } catch (JSONException e) {
            Log.e(TAG, "Failed to save subscriptions");
        }
    }
}
