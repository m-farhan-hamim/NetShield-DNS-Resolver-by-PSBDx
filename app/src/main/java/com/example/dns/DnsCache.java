package com.example.dns;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public class DnsCache {
    private static class CacheEntry {
        byte[] responseData;
        long expiryTimeMs;

        CacheEntry(byte[] data, long ttlSeconds) {
            this.responseData = data.clone();
            this.expiryTimeMs = System.currentTimeMillis() + (ttlSeconds * 1000L);
        }

        boolean isExpired() {
            return System.currentTimeMillis() > expiryTimeMs;
        }
    }

    private final int maxEntries;
    private final Map<String, CacheEntry> cache;
    private final AtomicLong hitCount = new AtomicLong(0);
    private final AtomicLong missCount = new AtomicLong(0);

    public DnsCache(int maxEntries) {
        this.maxEntries = maxEntries;
        this.cache = new LinkedHashMap<String, CacheEntry>(maxEntries, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, CacheEntry> eldest) {
                return size() > DnsCache.this.maxEntries;
            }
        };
    }

    private String makeKey(String domain, int qType) {
        return (domain + "#" + qType).toLowerCase();
    }

    public synchronized byte[] get(String domain, int qType, int clientTransactionId) {
        String key = makeKey(domain, qType);
        CacheEntry entry = cache.get(key);
        if (entry != null) {
            if (!entry.isExpired()) {
                hitCount.incrementAndGet();
                byte[] copy = entry.responseData.clone();
                DnsPacketParser.setTransactionId(copy, clientTransactionId);
                return copy;
            } else {
                cache.remove(key);
            }
        }
        missCount.incrementAndGet();
        return null;
    }

    public synchronized void put(String domain, int qType, byte[] response, long ttlSeconds) {
        if (domain == null || response == null || ttlSeconds <= 0) return;
        // Cap TTL between 10s and 24 hours
        long clampedTtl = Math.max(10, Math.min(ttlSeconds, 86400));
        String key = makeKey(domain, qType);
        cache.put(key, new CacheEntry(response, clampedTtl));
    }

    public synchronized void clear() {
        cache.clear();
    }

    public long getHitCount() {
        return hitCount.get();
    }

    public long getMissCount() {
        return missCount.get();
    }

    public synchronized int size() {
        return cache.size();
    }
}
