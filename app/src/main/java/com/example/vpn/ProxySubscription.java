package com.example.vpn;

/** A proxy provider's subscription URL (e.g. Xeovo) that the user saved to hand to v2rayNG. */
public class ProxySubscription {
    private final String id;
    private final String name;
    private final String url;
    private final long addedAt;

    public ProxySubscription(String id, String name, String url, long addedAt) {
        this.id = id;
        this.name = name;
        this.url = url;
        this.addedAt = addedAt;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    /** Contains the provider's secret token. Never log it or show it in full. */
    public String getUrl() {
        return url;
    }

    public long getAddedAt() {
        return addedAt;
    }
}
