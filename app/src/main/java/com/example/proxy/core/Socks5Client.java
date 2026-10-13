package com.example.proxy.core;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;

/**
 * Minimal SOCKS5 client (RFC 1928 + username/password login, RFC 1929), TCP CONNECT only.
 * The destination is sent as a domain name whenever one is known, so the proxy resolves it
 * remotely and the lookup never leaves through the user's own network.
 */
public final class Socks5Client {
    private Socks5Client() {
    }

    /** Returns a socket already connected to host:port through the proxy. */
    public static Socket connect(ProxyEndpoint proxy, String host, int port, int timeoutMs) throws IOException {
        Socket socket = new Socket();
        try {
            socket.setTcpNoDelay(true);
            try {
                socket.connect(new InetSocketAddress(proxy.host, proxy.port), timeoutMs);
            } catch (SocketTimeoutException e) {
                throw new Socks5Exception(Socks5Exception.Kind.TIMEOUT, "Proxy did not answer", e);
            } catch (IOException e) {
                throw new Socks5Exception(Socks5Exception.Kind.PROXY_UNREACHABLE, "Cannot reach proxy " + proxy, e);
            }
            socket.setSoTimeout(timeoutMs);
            try {
                handshake(socket, proxy, host, port);
            } catch (SocketTimeoutException e) {
                throw new Socks5Exception(Socks5Exception.Kind.TIMEOUT, "Proxy stopped answering", e);
            } catch (EOFException e) {
                throw new Socks5Exception(Socks5Exception.Kind.NOT_SOCKS5, "Proxy closed the connection", e);
            }
            socket.setSoTimeout(0);
            return socket;
        } catch (IOException | RuntimeException e) {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
            throw e;
        }
    }

    private static void handshake(Socket socket, ProxyEndpoint proxy, String host, int port) throws IOException {
        OutputStream out = socket.getOutputStream();
        DataInputStream in = new DataInputStream(socket.getInputStream());

        if (proxy.hasCredentials()) {
            out.write(new byte[]{5, 2, 0, 2});
        } else {
            out.write(new byte[]{5, 1, 0});
        }
        out.flush();

        int version = in.readUnsignedByte();
        int method = in.readUnsignedByte();
        if (version != 5) {
            throw new Socks5Exception(Socks5Exception.Kind.NOT_SOCKS5, "Not a SOCKS5 proxy");
        }
        if (method == 0xFF) {
            throw new Socks5Exception(Socks5Exception.Kind.LOGIN_REQUIRED, "Proxy needs a login");
        }
        if (method == 2) {
            if (!proxy.hasCredentials()) {
                throw new Socks5Exception(Socks5Exception.Kind.LOGIN_REQUIRED, "Proxy needs a login");
            }
            byte[] user = proxy.username.getBytes(StandardCharsets.UTF_8);
            byte[] pass = (proxy.password == null ? "" : proxy.password).getBytes(StandardCharsets.UTF_8);
            if (user.length > 255 || pass.length > 255) {
                throw new Socks5Exception(Socks5Exception.Kind.LOGIN_FAILED, "Login too long");
            }
            ByteArrayOutputStream auth = new ByteArrayOutputStream();
            auth.write(1);
            auth.write(user.length);
            auth.write(user);
            auth.write(pass.length);
            auth.write(pass);
            out.write(auth.toByteArray());
            out.flush();
            in.readUnsignedByte(); // auth version
            if (in.readUnsignedByte() != 0) {
                throw new Socks5Exception(Socks5Exception.Kind.LOGIN_FAILED, "Proxy rejected the login");
            }
        } else if (method != 0) {
            throw new Socks5Exception(Socks5Exception.Kind.PROTOCOL, "Unsupported SOCKS5 method " + method);
        }

        ByteArrayOutputStream req = new ByteArrayOutputStream();
        req.write(5); // version
        req.write(1); // CONNECT
        req.write(0); // reserved
        req.write(encodeAddress(host));
        req.write((port >> 8) & 0xFF);
        req.write(port & 0xFF);
        out.write(req.toByteArray());
        out.flush();

        int rv = in.readUnsignedByte();
        int rep = in.readUnsignedByte();
        in.readUnsignedByte(); // reserved
        int atyp = in.readUnsignedByte();
        if (rv != 5) {
            throw new Socks5Exception(Socks5Exception.Kind.PROTOCOL, "Bad SOCKS5 reply");
        }
        int skip;
        if (atyp == 1) {
            skip = 4;
        } else if (atyp == 4) {
            skip = 16;
        } else if (atyp == 3) {
            skip = in.readUnsignedByte();
        } else {
            throw new Socks5Exception(Socks5Exception.Kind.PROTOCOL, "Bad SOCKS5 address type");
        }
        in.readFully(new byte[skip + 2]);
        if (rep != 0) {
            throw new Socks5Exception(Socks5Exception.Kind.PROXY_REFUSED_TARGET,
                    "Proxy could not reach " + host + ":" + port + " (code " + rep + ")", rep, null);
        }
    }

    private static byte[] encodeAddress(String host) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        byte[] v4 = parseIpv4(host);
        if (v4 != null) {
            b.write(1);
            b.write(v4);
        } else if (host.indexOf(':') >= 0) {
            byte[] raw = InetAddress.getByName(host).getAddress(); // an IPv6 literal: no DNS lookup
            if (raw.length == 4) {
                b.write(1);
            } else {
                b.write(4);
            }
            b.write(raw);
        } else {
            byte[] name = host.getBytes(StandardCharsets.UTF_8);
            if (name.length == 0 || name.length > 255) {
                throw new Socks5Exception(Socks5Exception.Kind.PROTOCOL, "Bad host name");
            }
            b.write(3);
            b.write(name.length);
            b.write(name);
        }
        return b.toByteArray();
    }

    static byte[] parseIpv4(String s) {
        String[] parts = s.split("\\.", -1);
        if (parts.length != 4) {
            return null;
        }
        byte[] out = new byte[4];
        for (int i = 0; i < 4; i++) {
            String p = parts[i];
            if (p.isEmpty() || p.length() > 3) {
                return null;
            }
            int v = 0;
            for (int j = 0; j < p.length(); j++) {
                char c = p.charAt(j);
                if (c < '0' || c > '9') {
                    return null;
                }
                v = v * 10 + (c - '0');
            }
            if (v > 255) {
                return null;
            }
            out[i] = (byte) v;
        }
        return out;
    }
}
