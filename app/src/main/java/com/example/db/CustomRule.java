package com.example.db;

public class CustomRule {
    public static final String TYPE_BLOCK = "BLOCK";
    public static final String TYPE_WHITE = "WHITE";
    public static final String TYPE_MAPPING = "MAPPING";

    private long id;
    private String domain;
    private String targetIp;
    private String ruleType;
    private boolean enabled;

    public CustomRule() {
    }

    public CustomRule(String domain, String ruleType, String targetIp, boolean enabled) {
        this.domain = domain;
        this.ruleType = ruleType;
        this.targetIp = targetIp;
        this.enabled = enabled;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public String getTargetIp() {
        return targetIp;
    }

    public void setTargetIp(String targetIp) {
        this.targetIp = targetIp;
    }

    public String getRuleType() {
        return ruleType;
    }

    public void setRuleType(String ruleType) {
        this.ruleType = ruleType;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
