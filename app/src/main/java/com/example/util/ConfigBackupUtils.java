package com.example.util;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.db.BlocklistSource;
import com.example.db.CustomRule;
import com.example.db.DatabaseHelper;
import com.example.dns.BlocklistManager;
import com.example.dns.DnsResolverEngine;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

public class ConfigBackupUtils {

    public static String exportConfigJson(Context context) {
        try {
            JSONObject root = new JSONObject();
            SharedPreferences prefs = context.getSharedPreferences(DnsResolverEngine.PREFS_NAME, Context.MODE_PRIVATE);

            // Export Preferences
            JSONObject prefsObj = new JSONObject();
            prefsObj.put("operation_mode", prefs.getString("operation_mode", "VPN"));
            prefsObj.put("upstream_mode", prefs.getString("upstream_mode", "DOH"));
            prefsObj.put("doh_url", prefs.getString("doh_url", "https://cloudflare-dns.com/dns-query"));
            prefsObj.put("dot_host", prefs.getString("dot_host", "dns.google"));
            prefsObj.put("dot_port", prefs.getInt("dot_port", 853));
            prefsObj.put("upstream_primary", prefs.getString("upstream_primary", "1.1.1.1"));
            prefsObj.put("upstream_secondary", prefs.getString("upstream_secondary", "8.8.8.8"));
            prefsObj.put("block_action", prefs.getString("block_action", "ZERO_IP"));
            prefsObj.put("cache_enabled", prefs.getBoolean("cache_enabled", true));
            prefsObj.put("auto_start_on_boot", prefs.getBoolean("auto_start_on_boot", false));
            prefsObj.put("server_port", prefs.getInt("server_port", 5353));
            root.put("preferences", prefsObj);

            // Export Rules
            DatabaseHelper db = DatabaseHelper.getInstance(context);
            List<CustomRule> rules = db.getAllRules();
            JSONArray rulesArr = new JSONArray();
            for (CustomRule r : rules) {
                JSONObject rObj = new JSONObject();
                rObj.put("domain", r.getDomain());
                rObj.put("ruleType", r.getRuleType());
                rObj.put("targetIp", r.getTargetIp());
                rObj.put("enabled", r.isEnabled());
                rulesArr.put(rObj);
            }
            root.put("rules", rulesArr);

            // Export Sources
            List<BlocklistSource> sources = db.getBlocklistSources();
            JSONArray sourcesArr = new JSONArray();
            for (BlocklistSource s : sources) {
                JSONObject sObj = new JSONObject();
                sObj.put("name", s.getName());
                sObj.put("url", s.getUrl());
                sObj.put("enabled", s.isEnabled());
                sourcesArr.put(sObj);
            }
            root.put("sources", sourcesArr);

            return root.toString(2);
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean importConfigJson(Context context, String jsonString) {
        try {
            JSONObject root = new JSONObject(jsonString);
            SharedPreferences prefs = context.getSharedPreferences(DnsResolverEngine.PREFS_NAME, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();

            if (root.has("preferences")) {
                JSONObject prefsObj = root.getJSONObject("preferences");
                if (prefsObj.has("operation_mode")) editor.putString("operation_mode", prefsObj.getString("operation_mode"));
                if (prefsObj.has("upstream_mode")) editor.putString("upstream_mode", prefsObj.getString("upstream_mode"));
                if (prefsObj.has("doh_url")) editor.putString("doh_url", prefsObj.getString("doh_url"));
                if (prefsObj.has("dot_host")) editor.putString("dot_host", prefsObj.getString("dot_host"));
                if (prefsObj.has("dot_port")) editor.putInt("dot_port", prefsObj.getInt("dot_port"));
                if (prefsObj.has("upstream_primary")) editor.putString("upstream_primary", prefsObj.getString("upstream_primary"));
                if (prefsObj.has("upstream_secondary")) editor.putString("upstream_secondary", prefsObj.getString("upstream_secondary"));
                if (prefsObj.has("block_action")) editor.putString("block_action", prefsObj.getString("block_action"));
                if (prefsObj.has("cache_enabled")) editor.putBoolean("cache_enabled", prefsObj.getBoolean("cache_enabled"));
                if (prefsObj.has("auto_start_on_boot")) editor.putBoolean("auto_start_on_boot", prefsObj.getBoolean("auto_start_on_boot"));
                if (prefsObj.has("server_port")) editor.putInt("server_port", prefsObj.getInt("server_port"));
                editor.apply();
            }

            DatabaseHelper db = DatabaseHelper.getInstance(context);

            if (root.has("rules")) {
                JSONArray rulesArr = root.getJSONArray("rules");
                for (int i = 0; i < rulesArr.length(); i++) {
                    JSONObject rObj = rulesArr.getJSONObject(i);
                    CustomRule rule = new CustomRule(
                            rObj.getString("domain"),
                            rObj.getString("ruleType"),
                            rObj.optString("targetIp", null),
                            rObj.optBoolean("enabled", true)
                    );
                    db.insertRule(rule);
                }
            }

            if (root.has("sources")) {
                JSONArray sourcesArr = root.getJSONArray("sources");
                for (int i = 0; i < sourcesArr.length(); i++) {
                    JSONObject sObj = sourcesArr.getJSONObject(i);
                    BlocklistSource source = new BlocklistSource(
                            sObj.getString("name"),
                            sObj.getString("url"),
                            sObj.optBoolean("enabled", true)
                    );
                    db.insertBlocklistSource(source);
                }
            }

            BlocklistManager.getInstance(context).reloadRules();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
