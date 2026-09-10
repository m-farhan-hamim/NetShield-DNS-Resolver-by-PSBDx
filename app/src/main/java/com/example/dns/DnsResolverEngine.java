package com.example.dns;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;

import com.example.db.DatabaseHelper;
import com.example.db.DnsLog;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DnsResolverEngine {
    public static final String ACTION_LOG_UPDATED = "com.example.dns.ACTION_LOG_UPDATED";
    public static final String PREFS_NAME = "dns_prefs";

    private static DnsResolverEngine instance;

    private final Context context;
    private final DatabaseHelper dbHelper;
    private final BlocklistManager blocklistManager;
    private final DnsCache cache;
    private final ExecutorService logExecutor = Executors.newSingleThreadExecutor();
    private final SharedPreferences prefs;

    public static synchronized DnsResolverEngine getInstance(Context context) {
        if (instance == null) {
            instance = new DnsResolverEngine(context.getApplicationContext());
        }
        return instance;
    }

    private DnsResolverEngine(Context context) {
        this.context = context;
        this.dbHelper = DatabaseHelper.getInstance(context);
        this.blocklistManager = BlocklistManager.getInstance(context);
        this.cache = new DnsCache(2000);
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public DnsCache getCache() {
        return cache;
    }

    public byte[] resolve(byte[] queryPacket, int length, String clientIp) {
        long startTime = SystemClock.elapsedRealtime();

        DnsPacketParser.DnsQuestion q = DnsPacketParser.parseQuestion(queryPacket, length);
        if (q == null || q.domain == null || q.domain.isEmpty()) {
            return null;
        }

        String domain = q.domain;
        String typeName = DnsPacketParser.getTypeName(q.qType);

        // 1. Check Custom Local Mapping
        String customIp = blocklistManager.getCustomMapping(domain);
        if (customIp != null && !customIp.isEmpty()) {
            byte[] response = DnsPacketParser.buildCustomMappingResponse(queryPacket, length, customIp);
            long latency = SystemClock.elapsedRealtime() - startTime;
            recordLog(domain, typeName, "LOCAL", latency, "Local Record (" + customIp + ")", clientIp);
            return response;
        }

        // 2. Check Whitelist & Blocklist (Sinkhole)
        if (blocklistManager.isBlocked(domain)) {
            String blockAction = prefs.getString("block_action", "ZERO_IP");
            byte[] response = DnsPacketParser.buildBlockedResponse(queryPacket, length, blockAction);
            long latency = SystemClock.elapsedRealtime() - startTime;
            recordLog(domain, typeName, "BLOCKED", latency, "Sinkhole (" + blockAction + ")", clientIp);
            return response;
        }

        // 3. Check Smart In-Memory Cache
        boolean cacheEnabled = prefs.getBoolean("cache_enabled", true);
        if (cacheEnabled) {
            byte[] cachedResponse = cache.get(domain, q.qType, q.transactionId);
            if (cachedResponse != null) {
                long latency = SystemClock.elapsedRealtime() - startTime;
                recordLog(domain, typeName, "CACHED", latency, "In-Memory LRU Cache", clientIp);
                return cachedResponse;
            }
        }

        // 4. Query Upstream
        String upstreamMode = prefs.getString("upstream_mode", "DOH");
        String upstreamDesc;
        byte[] upstreamResponse = null;

        if ("DOT".equalsIgnoreCase(upstreamMode)) {
            String dotHost = prefs.getString("dot_host", "dns.google");
            int dotPort = prefs.getInt("dot_port", 853);
            upstreamDesc = "DoT (" + dotHost + ")";
            upstreamResponse = DoTClient.query(dotHost, dotPort, queryPacket, length);
        } else if ("UDP".equalsIgnoreCase(upstreamMode)) {
            String primaryDns = prefs.getString("upstream_primary", "1.1.1.1");
            String secondaryDns = prefs.getString("upstream_secondary", "8.8.8.8");
            upstreamDesc = "UDP (" + primaryDns + ")";
            upstreamResponse = DnsUdpClient.query(primaryDns, secondaryDns, queryPacket, length);
        } else {
            // Default: DNS-over-HTTPS (DoH)
            String dohUrl = prefs.getString("doh_url", "https://cloudflare-dns.com/dns-query");
            upstreamDesc = "DoH (" + extractHost(dohUrl) + ")";
            upstreamResponse = DoHClient.query(dohUrl, queryPacket, length);
        }

        long latency = SystemClock.elapsedRealtime() - startTime;

        if (upstreamResponse != null && upstreamResponse.length >= 12) {
            // Cache successful response
            if (cacheEnabled) {
                long ttl = DnsPacketParser.extractTtl(upstreamResponse, upstreamResponse.length);
                cache.put(domain, q.qType, upstreamResponse, ttl);
            }
            recordLog(domain, typeName, "ALLOWED", latency, upstreamDesc, clientIp);
            return upstreamResponse;
        } else {
            // If DoH/DoT failed, try fallback to Plain UDP
            if (!"UDP".equalsIgnoreCase(upstreamMode)) {
                upstreamResponse = DnsUdpClient.query("1.1.1.1", "8.8.8.8", queryPacket, length);
                if (upstreamResponse != null) {
                    recordLog(domain, typeName, "ALLOWED", latency, "Fallback UDP (1.1.1.1)", clientIp);
                    return upstreamResponse;
                }
            }
            recordLog(domain, typeName, "FAILED", latency, upstreamDesc + " [Timeout]", clientIp);
            return null;
        }
    }

    private void recordLog(final String domain, final String type, final String status, final long latency, final String upstream, final String clientIp) {
        logExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    DnsLog log = new DnsLog(
                            System.currentTimeMillis(),
                            domain,
                            type,
                            status,
                            latency,
                            upstream,
                            clientIp != null ? clientIp : "127.0.0.1"
                    );
                    dbHelper.insertLog(log);

                    // Notify UI to refresh logs/dashboard
                    Intent intent = new Intent(ACTION_LOG_UPDATED);
                    intent.setPackage(context.getPackageName());
                    context.sendBroadcast(intent);
                } catch (Exception ignored) {
                }
            }
        });
    }

    private String extractHost(String url) {
        try {
            java.net.URI uri = new java.net.URI(url);
            String host = uri.getHost();
            return host != null ? host : url;
        } catch (Exception e) {
            return url;
        }
    }
}
