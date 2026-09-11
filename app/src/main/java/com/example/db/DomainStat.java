package com.example.db;

public class DomainStat {
    private final String domain;
    private final int count;
    private final String status;

    public DomainStat(String domain, int count, String status) {
        this.domain = domain;
        this.count = count;
        this.status = status;
    }

    public String getDomain() {
        return domain;
    }

    public int getCount() {
        return count;
    }

    public String getStatus() {
        return status;
    }
}
