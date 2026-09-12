package com.example.db;

public class BlocklistSource {
    private long id;
    private String name;
    private String url;
    private boolean enabled;
    private long lastUpdated;
    private int ruleCount;

    public BlocklistSource() {
    }

    public BlocklistSource(String name, String url, boolean enabled) {
        this(name, url, enabled, 0, 0);
    }

    public BlocklistSource(String name, String url, boolean enabled, long lastUpdated, int ruleCount) {
        this.name = name;
        this.url = url;
        this.enabled = enabled;
        this.lastUpdated = lastUpdated;
        this.ruleCount = ruleCount;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public long getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(long lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    public int getRuleCount() {
        return ruleCount;
    }

    public void setRuleCount(int ruleCount) {
        this.ruleCount = ruleCount;
    }
}
