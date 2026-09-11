package com.example.db;

public class TrafficBucket {
    private final String label;
    private final int allowedCount;
    private final int blockedCount;

    public TrafficBucket(String label, int allowedCount, int blockedCount) {
        this.label = label;
        this.allowedCount = allowedCount;
        this.blockedCount = blockedCount;
    }

    public String getLabel() {
        return label;
    }

    public int getAllowedCount() {
        return allowedCount;
    }

    public int getBlockedCount() {
        return blockedCount;
    }

    public int getTotal() {
        return allowedCount + blockedCount;
    }
}
