package com.example.db;

public class ClientDeviceStat {
    private final String clientIp;
    private final int totalQueries;
    private final int blockedQueries;
    private final long lastSeen;

    public ClientDeviceStat(String clientIp, int totalQueries, int blockedQueries, long lastSeen) {
        this.clientIp = clientIp;
        this.totalQueries = totalQueries;
        this.blockedQueries = blockedQueries;
        this.lastSeen = lastSeen;
    }

    public String getClientIp() {
        return clientIp;
    }

    public int getTotalQueries() {
        return totalQueries;
    }

    public int getBlockedQueries() {
        return blockedQueries;
    }

    public long getLastSeen() {
        return lastSeen;
    }
}
