package com.example.dns;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.example.db.BlocklistSource;
import com.example.db.CustomRule;
import com.example.db.DatabaseHelper;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BlocklistManager {
    private static BlocklistManager instance;

    public interface SyncCallback {
        void onProgress(String message);
        void onComplete(int totalRules);
        void onError(String error);
    }

    private final Context context;
    private final DatabaseHelper dbHelper;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    // Thread-safe fast in-memory sets
    private final Set<String> blockedDomains = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    private final Set<String> whitelistDomains = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    private final Map<String, String> customMappings = new ConcurrentHashMap<>();

    private volatile boolean isInitialized = false;
    private volatile long pauseUntilTimestamp = 0;

    public void pauseProtection(long durationMillis) {
        this.pauseUntilTimestamp = System.currentTimeMillis() + durationMillis;
    }

    public void resumeProtection() {
        this.pauseUntilTimestamp = 0;
    }

    public boolean isPaused() {
        return System.currentTimeMillis() < pauseUntilTimestamp;
    }

    public long getRemainingPauseMillis() {
        long remaining = pauseUntilTimestamp - System.currentTimeMillis();
        return remaining > 0 ? remaining : 0;
    }

    public static synchronized BlocklistManager getInstance(Context context) {
        if (instance == null) {
            instance = new BlocklistManager(context.getApplicationContext());
        }
        return instance;
    }

    private BlocklistManager(Context context) {
        this.context = context;
        this.dbHelper = DatabaseHelper.getInstance(context);
        reloadRules();
    }

    public void reloadRules() {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    List<CustomRule> rules = dbHelper.getAllRules();
                    Set<String> newBlocked = new HashSet<>();
                    Set<String> newWhite = new HashSet<>();
                    Map<String, String> newMappings = new ConcurrentHashMap<>();

                    for (CustomRule r : rules) {
                        if (!r.isEnabled()) continue;
                        String d = r.getDomain().trim().toLowerCase();
                        if (CustomRule.TYPE_BLOCK.equals(r.getRuleType())) {
                            newBlocked.add(d);
                        } else if (CustomRule.TYPE_WHITE.equals(r.getRuleType())) {
                            newWhite.add(d);
                        } else if (CustomRule.TYPE_MAPPING.equals(r.getRuleType())) {
                            if (r.getTargetIp() != null && !r.getTargetIp().isEmpty()) {
                                newMappings.put(d, r.getTargetIp().trim());
                            }
                        }
                    }

                    blockedDomains.clear();
                    blockedDomains.addAll(newBlocked);

                    whitelistDomains.clear();
                    whitelistDomains.addAll(newWhite);

                    customMappings.clear();
                    customMappings.putAll(newMappings);

                    isInitialized = true;
                } catch (Exception ignored) {
                }
            }
        });
    }

    public boolean isWhitelisted(String domain) {
        if (domain == null) return false;
        String d = domain.toLowerCase();
        if (whitelistDomains.contains(d)) return true;

        // Check parent domains
        int dot = d.indexOf('.');
        while (dot != -1) {
            String parent = d.substring(dot + 1);
            if (whitelistDomains.contains(parent)) return true;
            dot = d.indexOf('.', dot + 1);
        }
        return false;
    }

    public String getCustomMapping(String domain) {
        if (domain == null) return null;
        return customMappings.get(domain.toLowerCase());
    }

    public boolean isBlocked(String domain) {
        if (domain == null) return false;
        if (isPaused()) return false;
        String d = domain.toLowerCase();

        // 1. Whitelist takes precedence
        if (isWhitelisted(d)) return false;

        // 2. Exact match
        if (blockedDomains.contains(d)) return true;

        // 3. Parent domain / Subdomain wildcard matching
        int dot = d.indexOf('.');
        while (dot != -1) {
            String parent = d.substring(dot + 1);
            if (blockedDomains.contains(parent)) return true;
            dot = d.indexOf('.', dot + 1);
        }
        return false;
    }

    public void syncRemoteBlocklists(final SyncCallback callback) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                final Handler mainHandler = new Handler(Looper.getMainLooper());
                try {
                    List<BlocklistSource> sources = dbHelper.getBlocklistSources();
                    int totalSynced = 0;

                    for (final BlocklistSource source : sources) {
                        if (!source.isEnabled()) continue;

                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                if (callback != null) {
                                    callback.onProgress("Downloading " + source.getName() + "…");
                                }
                            }
                        });

                        int count = downloadAndParseSource(source.getUrl());
                        source.setRuleCount(count);
                        source.setLastUpdated(System.currentTimeMillis());
                        dbHelper.updateBlocklistSource(source);
                        totalSynced += count;
                    }

                    final int finalTotal = blockedDomains.size();
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (callback != null) {
                                callback.onComplete(finalTotal);
                            }
                        }
                    });
                } catch (final Exception e) {
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (callback != null) {
                                callback.onError(e.getMessage() != null ? e.getMessage() : "Sync failed");
                            }
                        }
                    });
                }
            }
        });
    }

    private int downloadAndParseSource(String sourceUrl) {
        HttpURLConnection conn = null;
        int count = 0;
        try {
            URL url = new URL(sourceUrl);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(15000);
            conn.setUseCaches(false);
            conn.setRequestProperty("User-Agent", "LocalDNS-Resolver/1.0");

            if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                InputStream is = conn.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(is));
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;

                    // Remove inline comments
                    int hashIdx = line.indexOf('#');
                    if (hashIdx != -1) {
                        line = line.substring(0, hashIdx).trim();
                    }

                    // Format might be: "127.0.0.1 domain.com" or "0.0.0.0 domain.com" or just "domain.com"
                    String[] parts = line.split("\\s+");
                    String domain = null;
                    if (parts.length >= 2) {
                        String ip = parts[0];
                        if (ip.equals("127.0.0.1") || ip.equals("0.0.0.0")) {
                            domain = parts[1];
                        }
                    } else if (parts.length == 1) {
                        domain = parts[0];
                    }

                    if (domain != null) {
                        domain = domain.toLowerCase();
                        if (!domain.equals("localhost") && !domain.equals("broadcasthost")
                                && !domain.equals("local") && domain.contains(".")) {
                            blockedDomains.add(domain);
                            count++;
                        }
                    }
                }
                reader.close();
            }
        } catch (Exception ignored) {
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
        return count;
    }

    public int getBlockedCount() {
        return blockedDomains.size();
    }

    public int getWhitelistCount() {
        return whitelistDomains.size();
    }

    public int getMappingCount() {
        return customMappings.size();
    }
}
