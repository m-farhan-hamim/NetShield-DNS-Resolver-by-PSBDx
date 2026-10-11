package com.example.proxy.core;

import java.io.IOException;

/** A SOCKS5 failure with a machine-readable reason, so the UI can explain it in plain words. */
public final class Socks5Exception extends IOException {
    public enum Kind {
        /** Nothing is listening on the proxy address (the proxy app is not running). */
        PROXY_UNREACHABLE,
        /** The proxy accepted the connection but did not answer in time. */
        TIMEOUT,
        /** Something answered, but it does not speak SOCKS5 (wrong port, HTTP proxy, ...). */
        NOT_SOCKS5,
        LOGIN_REQUIRED,
        LOGIN_FAILED,
        /** The proxy is fine but could not reach the destination (SOCKS reply code in replyCode). */
        PROXY_REFUSED_TARGET,
        PROTOCOL
    }

    public final Kind kind;
    public final int replyCode;

    public Socks5Exception(Kind kind, String message) {
        this(kind, message, -1, null);
    }

    public Socks5Exception(Kind kind, String message, Throwable cause) {
        this(kind, message, -1, cause);
    }

    public Socks5Exception(Kind kind, String message, int replyCode, Throwable cause) {
        super(message, cause);
        this.kind = kind;
        this.replyCode = replyCode;
    }
}
