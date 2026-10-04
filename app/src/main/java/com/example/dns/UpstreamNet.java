package com.example.dns;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;

/**
 * Network access for the encrypted upstreams (DoH/DoT).
 *
 * While NetShield's VPN is up, the system resolver is NetShield itself (10.0.0.1). If the DoH/DoT
 * clients looked up "cloudflare-dns.com" through the system resolver, that lookup would come back
 * to NetShield, which is waiting on this very query: a deadlock that ends in a timeout.
 * So the upstream hostname is resolved, and the socket opened, on the real underlying network
 * (Wi-Fi/cellular), bypassing the VPN. Only the upstream's own hostname is looked up this way.
 */
public final class UpstreamNet {
    private static volatile Context appContext;

    private UpstreamNet() {
    }

    public static void init(Context context) {
        if (context != null) {
            appContext = context.getApplicationContext();
        }
    }

    /** Best non-VPN network that has internet, or null if none can be determined. */
    static Network underlying() {
        Context c = appContext;
        if (c == null) return null;
        try {
            ConnectivityManager cm = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return null;
            Network best = null;
            int bestScore = -1;
            for (Network n : cm.getAllNetworks()) {
                NetworkCapabilities caps = cm.getNetworkCapabilities(n);
                if (caps == null) continue;
                if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) continue;
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) continue;
                int score = 0;
                if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) score += 2;
                if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) score += 1;
                if (score > bestScore) {
                    best = n;
                    bestScore = score;
                }
            }
            return best;
        } catch (Exception e) {
            return null;
        }
    }

    /** Opens an HTTPS connection bound to the underlying network (DNS lookup included). */
    public static HttpURLConnection openHttps(URL url) throws IOException {
        Network n = underlying();
        URLConnection c = (n != null) ? n.openConnection(url) : url.openConnection();
        return (HttpURLConnection) c;
    }

    /** Connected plain TCP socket to host:port over the underlying network. */
    public static Socket connect(String host, int port, int timeoutMs) throws IOException {
        Network n = underlying();
        InetAddress[] addrs = (n != null) ? n.getAllByName(host) : InetAddress.getAllByName(host);
        IOException last = null;
        for (InetAddress a : addrs) {
            Socket s = (n != null) ? n.getSocketFactory().createSocket() : new Socket();
            try {
                s.connect(new InetSocketAddress(a, port), timeoutMs);
                s.setSoTimeout(timeoutMs);
                return s;
            } catch (IOException e) {
                last = e;
                try {
                    s.close();
                } catch (Exception ignored) {
                }
            }
        }
        throw last != null ? last : new IOException("No address found for " + host);
    }

    /**
     * True if the device has a working internet connection on a real (non-VPN) network.
     * Android's own validation flag is trusted when set; otherwise (captive-portal check blocked,
     * flaky probe) a short TCP connect to well-known public IPs decides. Blocking: call off the
     * main thread.
     */
    public static boolean hasInternet() {
        Context c = appContext;
        if (c == null) return true;
        List<Network> unvalidated = new ArrayList<>();
        try {
            ConnectivityManager cm = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return true;
            for (Network n : cm.getAllNetworks()) {
                NetworkCapabilities caps = cm.getNetworkCapabilities(n);
                if (caps == null) continue;
                if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) continue;
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) continue;
                if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) return true;
                unvalidated.add(n);
            }
        } catch (Exception e) {
            return true; // cannot tell: do not lock the user out
        }
        String[] probes = {"1.1.1.1", "8.8.8.8"};
        for (Network n : unvalidated) {
            for (String ip : probes) {
                Socket s = null;
                try {
                    s = n.getSocketFactory().createSocket();
                    s.connect(new InetSocketAddress(ip, 443), 3000);
                    return true;
                } catch (Exception ignored) {
                } finally {
                    if (s != null) {
                        try {
                            s.close();
                        } catch (Exception ignored) {
                        }
                    }
                }
            }
        }
        return false;
    }
}
