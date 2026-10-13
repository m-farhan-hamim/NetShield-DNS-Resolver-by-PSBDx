package com.example.proxy.core;

/** Where the user's local SOCKS5 proxy (v2rayNG, Orbot, InviZible, ...) is listening. */
public final class ProxyEndpoint {
    public final String host;
    public final int port;
    /** Optional login; null or empty means no login. */
    public final String username;
    public final String password;

    public ProxyEndpoint(String host, int port, String username, String password) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
    }

    public boolean hasCredentials() {
        return username != null && !username.isEmpty();
    }

    @Override
    public String toString() {
        return host + ":" + port; // never include the password
    }
}
