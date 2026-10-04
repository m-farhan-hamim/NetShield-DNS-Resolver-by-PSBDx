package com.example.dns;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * Sends one real DNS query through the user's selected upstream method and reports
 * whether it worked. Unlike the resolver clients (which swallow errors for speed),
 * this keeps the exact exception so it can be shown to the user and pasted in a bug report.
 * Blocking: call from a background thread.
 */
public final class ConnectionVerifier {
    private static final String TAG = "ConnectionVerifier";
    private static final int TIMEOUT_MS = 5000;
    private static final int UDP_TIMEOUT_MS = 3000;
    private static final int MAX_ERROR_CHARS = 1800;

    public static final class Result {
        public final boolean ok;
        /** Short human label, e.g. "DoH (cloudflare-dns.com)". */
        public final String method;
        /** Full, copyable error text. Empty when ok. */
        public final String error;

        Result(boolean ok, String method, String error) {
            this.ok = ok;
            this.method = method;
            this.error = error;
        }
    }

    private ConnectionVerifier() {
    }

    public static Result verifySelected(Context context) {
        UpstreamNet.init(context);
        SharedPreferences prefs = context.getSharedPreferences(DnsResolverEngine.PREFS_NAME, Context.MODE_PRIVATE);
        String mode = prefs.getString("upstream_mode", "DOH");
        byte[] query = buildProbeQuery();

        String method;
        StringBuilder errors = new StringBuilder();
        boolean ok;

        if ("DOT".equalsIgnoreCase(mode)) {
            String host = prefs.getString("dot_host", "dns.google");
            int port = prefs.getInt("dot_port", 853);
            method = "DoT (" + host + ":" + port + ")";
            ok = tryStep(errors, method, () -> probeDot(host, port, query));
        } else if ("UDP".equalsIgnoreCase(mode)) {
            String primary = prefs.getString("upstream_primary", "1.1.1.1");
            String secondary = prefs.getString("upstream_secondary", "8.8.8.8");
            method = "UDP (" + primary + ")";
            ok = tryStep(errors, "UDP " + primary, () -> probeUdp(primary, query));
            if (!ok && secondary != null && !secondary.isEmpty() && !secondary.equals(primary)) {
                ok = tryStep(errors, "UDP " + secondary, () -> probeUdp(secondary, query));
            }
        } else {
            String url = prefs.getString("doh_url", "https://cloudflare-dns.com/dns-query");
            method = "DoH (" + url + ")";
            ok = tryStep(errors, method, () -> probeDoh(url, query));
        }

        if (ok) {
            return new Result(true, method, "");
        }
        return new Result(false, method, header(context, mode) + errors);
    }

    // ---- probes: each throws on any failure, with a message that says what went wrong ----

    private interface Probe {
        void run() throws Exception;
    }

    private static boolean tryStep(StringBuilder errors, String label, Probe probe) {
        try {
            probe.run();
            return true;
        } catch (Throwable t) {
            Log.w(TAG, label + " failed", t);
            errors.append("[").append(label).append("]\n").append(describe(t)).append("\n");
            return false;
        }
    }

    private static void probeDoh(String dohUrl, byte[] query) throws Exception {
        HttpURLConnection conn = null;
        try {
            conn = UpstreamNet.openHttps(new URL(dohUrl));
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setDoOutput(true);
            conn.setUseCaches(false);
            conn.setRequestProperty("Content-Type", "application/dns-message");
            conn.setRequestProperty("Accept", "application/dns-message");
            conn.setRequestProperty("User-Agent", "LocalDNS-Resolver/1.0");
            try (OutputStream os = conn.getOutputStream()) {
                os.write(query);
                os.flush();
            }
            int code = conn.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                throw new java.io.IOException("HTTP " + code + " " + conn.getResponseMessage());
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (InputStream is = conn.getInputStream()) {
                byte[] buf = new byte[1024];
                int n;
                while ((n = is.read(buf)) != -1) {
                    baos.write(buf, 0, n);
                }
            }
            checkResponse(baos.toByteArray(), query);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static void probeDot(String host, int port, byte[] query) throws Exception {
        Socket plain = null;
        SSLSocket ssl = null;
        try {
            plain = UpstreamNet.connect(host, port, TIMEOUT_MS);
            SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
            ssl = (SSLSocket) factory.createSocket(plain, host, port, true);
            ssl.startHandshake();
            if (!HttpsURLConnection.getDefaultHostnameVerifier().verify(host, ssl.getSession())) {
                throw new SSLPeerUnverifiedException("Certificate does not match hostname " + host);
            }
            DataOutputStream dos = new DataOutputStream(ssl.getOutputStream());
            dos.writeShort(query.length);
            dos.write(query);
            dos.flush();
            DataInputStream dis = new DataInputStream(ssl.getInputStream());
            int len = dis.readUnsignedShort();
            if (len < 12) {
                throw new java.io.IOException("DoT server sent a " + len + "-byte reply");
            }
            byte[] resp = new byte[len];
            dis.readFully(resp);
            checkResponse(resp, query);
        } finally {
            try {
                if (ssl != null) ssl.close();
            } catch (Exception ignored) {
            }
            try {
                if (plain != null) plain.close();
            } catch (Exception ignored) {
            }
        }
    }

    private static void probeUdp(String server, byte[] query) throws Exception {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(UDP_TIMEOUT_MS);
            InetAddress addr = InetAddress.getByName(server);
            socket.send(new DatagramPacket(query, query.length, addr, 53));
            byte[] buf = new byte[1500];
            DatagramPacket reply = new DatagramPacket(buf, buf.length);
            socket.receive(reply);
            byte[] resp = new byte[reply.getLength()];
            System.arraycopy(buf, 0, resp, 0, resp.length);
            checkResponse(resp, query);
        }
    }

    // ---- helpers ----

    /** Standard A query for example.com, fixed transaction id. */
    private static byte[] buildProbeQuery() {
        return new byte[]{
                0x4E, 0x53,             // id "NS"
                0x01, 0x00,             // RD
                0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                7, 'e', 'x', 'a', 'm', 'p', 'l', 'e',
                3, 'c', 'o', 'm', 0,
                0x00, 0x01,             // A
                0x00, 0x01              // IN
        };
    }

    private static void checkResponse(byte[] resp, byte[] query) throws java.io.IOException {
        if (resp.length < 12) {
            throw new java.io.IOException("DNS reply too short (" + resp.length + " bytes)");
        }
        if (resp[0] != query[0] || resp[1] != query[1]) {
            throw new java.io.IOException("DNS reply has a different transaction id");
        }
        if ((resp[2] & 0x80) == 0) {
            throw new java.io.IOException("Reply is not a DNS response");
        }
        int rcode = resp[3] & 0x0F;
        if (rcode != 0 && rcode != 3) {
            throw new java.io.IOException("DNS server returned error code RCODE=" + rcode);
        }
    }

    private static String header(Context context, String mode) {
        String app = "?";
        try {
            PackageInfo pi = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            app = pi.versionName;
        } catch (Exception ignored) {
        }
        return "NetShield " + app + " | Android " + Build.VERSION.RELEASE
                + " (API " + Build.VERSION.SDK_INT + ") | " + Build.MANUFACTURER + " " + Build.MODEL
                + "\nUpstream mode: " + mode + "\n\n";
    }

    private static String describe(Throwable t) {
        StringBuilder sb = new StringBuilder();
        Throwable cur = t;
        int depth = 0;
        while (cur != null && depth < 4) {
            sb.append(depth == 0 ? "" : "Caused by: ").append(cur.getClass().getName());
            if (cur.getMessage() != null) {
                sb.append(": ").append(cur.getMessage());
            }
            sb.append("\n");
            cur = cur.getCause();
            depth++;
        }
        StackTraceElement[] st = t.getStackTrace();
        for (int i = 0; i < st.length && i < 4; i++) {
            sb.append("    at ").append(st[i]).append("\n");
        }
        String s = sb.toString();
        return s.length() > MAX_ERROR_CHARS ? s.substring(0, MAX_ERROR_CHARS) + "\n..." : s;
    }
}
