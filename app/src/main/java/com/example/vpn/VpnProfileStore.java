package com.example.vpn;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Simple JSON-backed store for imported OpenVPN/WireGuard profiles - no new DB schema needed. */
public class VpnProfileStore {
    private static final String TAG = "VpnProfileStore";
    private static final String PREFS_NAME = "vpn_profiles";
    private static final String KEY_PROFILES = "profiles_json";
    private static final String KEY_ACTIVE_ID = "active_profile_id";

    private final SharedPreferences prefs;

    public VpnProfileStore(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public List<VpnProfile> getAll() {
        List<VpnProfile> result = new ArrayList<>();
        String json = prefs.getString(KEY_PROFILES, null);
        if (json == null) return result;
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                VpnProfile profile = new VpnProfile(
                        obj.getString("id"),
                        obj.getString("name"),
                        obj.getString("type"),
                        obj.optString("server", "unknown"),
                        obj.optInt("port", 0),
                        obj.optString("protocol", ""),
                        obj.getString("rawConfig"),
                        obj.optLong("importedAt", 0L)
                );
                result.add(profile);
            }
        } catch (JSONException e) {
            Log.e(TAG, "Failed to parse stored profiles", e);
        }
        return result;
    }

    public VpnProfile add(String name, String type, String server, int port, String protocol, String rawConfig) {
        VpnProfile profile = new VpnProfile(
                UUID.randomUUID().toString(), name, type, server, port, protocol, rawConfig,
                System.currentTimeMillis());
        List<VpnProfile> all = getAll();
        all.add(profile);
        saveAll(all);
        return profile;
    }

    public void delete(String id) {
        List<VpnProfile> all = getAll();
        List<VpnProfile> kept = new ArrayList<>();
        for (VpnProfile p : all) {
            if (!p.getId().equals(id)) kept.add(p);
        }
        saveAll(kept);
        if (id.equals(getActiveProfileId())) {
            setActiveProfileId(null);
        }
    }

    public String getActiveProfileId() {
        return prefs.getString(KEY_ACTIVE_ID, null);
    }

    public void setActiveProfileId(String id) {
        prefs.edit().putString(KEY_ACTIVE_ID, id).apply();
    }

    public VpnProfile getActiveProfile() {
        String id = getActiveProfileId();
        if (id == null) return null;
        for (VpnProfile p : getAll()) {
            if (p.getId().equals(id)) return p;
        }
        return null;
    }

    private void saveAll(List<VpnProfile> profiles) {
        try {
            JSONArray array = new JSONArray();
            for (VpnProfile p : profiles) {
                JSONObject obj = new JSONObject();
                obj.put("id", p.getId());
                obj.put("name", p.getName());
                obj.put("type", p.getType());
                obj.put("server", p.getServer());
                obj.put("port", p.getPort());
                obj.put("protocol", p.getProtocol());
                obj.put("rawConfig", p.getRawConfig());
                obj.put("importedAt", p.getImportedAt());
                array.put(obj);
            }
            prefs.edit().putString(KEY_PROFILES, array.toString()).apply();
        } catch (JSONException e) {
            Log.e(TAG, "Failed to save profiles", e);
        }
    }
}
