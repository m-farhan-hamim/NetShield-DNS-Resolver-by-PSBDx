package com.example.proxy.core;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Remembers which name an app asked for when it got an IP, so the proxy can be given the
 * name (and resolve it remotely) instead of the bare IP.
 */
public final class DnsNameCache {
    private static final int MAX_ENTRIES = 4096;
    private static final long TTL_MS = 10L * 60_000L;

    private static final class NameEntry {
        final String name;
        final long expiresAt;

        NameEntry(String name, long expiresAt) {
            this.name = name;
            this.expiresAt = expiresAt;
        }
    }

    private final Map<Integer, NameEntry> map = new LinkedHashMap<Integer, NameEntry>(256, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Integer, NameEntry> eldest) {
            return size() > MAX_ENTRIES;
        }
    };

    public synchronized void remember(String name, List<Integer> ips) {
        if (name == null || name.isEmpty() || ips == null) {
            return;
        }
        long expires = System.currentTimeMillis() + TTL_MS;
        for (Integer ip : ips) {
            if (ip != 0) {
                map.put(ip, new NameEntry(name, expires));
            }
        }
    }

    /** The name last asked for that resolved to this IP, or null. */
    public synchronized String lookup(int ip) {
        NameEntry e = map.get(ip);
        if (e == null) {
            return null;
        }
        if (e.expiresAt < System.currentTimeMillis()) {
            map.remove(ip);
            return null;
        }
        return e.name;
    }
}
