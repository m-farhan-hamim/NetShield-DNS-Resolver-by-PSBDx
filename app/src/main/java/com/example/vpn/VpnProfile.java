package com.example.vpn;

public class VpnProfile {
    public static final String TYPE_OPENVPN = "OPENVPN";
    public static final String TYPE_WIREGUARD = "WIREGUARD";

    private String id;
    private String name;
    private String type;
    private String server;
    private int port;
    private String protocol;
    private String rawConfig;
    private long importedAt;

    public VpnProfile() {
    }

    public VpnProfile(String id, String name, String type, String server, int port,
                       String protocol, String rawConfig, long importedAt) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.server = server;
        this.port = port;
        this.protocol = protocol;
        this.rawConfig = rawConfig;
        this.importedAt = importedAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getServer() {
        return server;
    }

    public void setServer(String server) {
        this.server = server;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    public String getRawConfig() {
        return rawConfig;
    }

    public void setRawConfig(String rawConfig) {
        this.rawConfig = rawConfig;
    }

    public long getImportedAt() {
        return importedAt;
    }

    public void setImportedAt(long importedAt) {
        this.importedAt = importedAt;
    }
}
