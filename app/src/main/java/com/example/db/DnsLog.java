package com.example.db;

public class DnsLog {
    private long id;
    private long timestamp;
    private String domain;
    private String queryType;
    private String status; // ALLOWED, BLOCKED, CACHED, LOCAL
    private long responseTimeMs;
    private String upstream;
    private String clientIp;

    public DnsLog() {
    }

    public DnsLog(long timestamp, String domain, String queryType, String status, long responseTimeMs, String upstream, String clientIp) {
        this.timestamp = timestamp;
        this.domain = domain;
        this.queryType = queryType;
        this.status = status;
        this.responseTimeMs = responseTimeMs;
        this.upstream = upstream;
        this.clientIp = clientIp;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public String getQueryType() {
        return queryType;
    }

    public void setQueryType(String queryType) {
        this.queryType = queryType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public long getResponseTimeMs() {
        return responseTimeMs;
    }

    public void setResponseTimeMs(long responseTimeMs) {
        this.responseTimeMs = responseTimeMs;
    }

    public String getUpstream() {
        return upstream;
    }

    public void setUpstream(String upstream) {
        this.upstream = upstream;
    }

    public String getClientIp() {
        return clientIp;
    }

    public void setClientIp(String clientIp) {
        this.clientIp = clientIp;
    }
}
