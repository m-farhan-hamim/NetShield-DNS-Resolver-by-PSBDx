package com.example.util;

import android.content.Context;
import android.net.ConnectivityManager;
import android.os.Build;
import android.system.OsConstants;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Locale;

/**
 * Finds which app (Android user id) owns a TCP connection that came out of our VPN tunnel.
 * Android 10+ answers this itself for the running VPN app; older versions let us read the kernel's
 * connection table in /proc/net/tcp.
 */
public final class ConnectionOwner {

    /** Returned when the owner could not be found. */
    public static final int UNKNOWN = -1;

    private ConnectionOwner() {
    }

    public static int tcpOwnerUid(Context context, String clientIp, int clientPort, String destIp, int destPort) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
                if (cm == null) {
                    return UNKNOWN;
                }
                // Literal IP addresses only, so no DNS lookup happens here.
                int uid = cm.getConnectionOwnerUid(OsConstants.IPPROTO_TCP,
                        new InetSocketAddress(InetAddress.getByName(clientIp), clientPort),
                        new InetSocketAddress(InetAddress.getByName(destIp), destPort));
                return uid < 0 ? UNKNOWN : uid;
            }
            return uidFromProc(clientIp, clientPort);
        } catch (IOException | RuntimeException e) { // RuntimeException includes SecurityException
            return UNKNOWN;
        }
    }

    /** Android 9 and older: the line for our local address and port in /proc/net/tcp has the uid. */
    private static int uidFromProc(String clientIp, int clientPort) throws IOException {
        String[] parts = clientIp.split("\\.");
        if (parts.length != 4) {
            return UNKNOWN;
        }
        // /proc/net/tcp prints the IPv4 address as the raw little-endian 32-bit value in hex.
        String hexIp = String.format(Locale.ROOT, "%02X%02X%02X%02X",
                Integer.parseInt(parts[3]), Integer.parseInt(parts[2]),
                Integer.parseInt(parts[1]), Integer.parseInt(parts[0]));
        String local = hexIp + String.format(Locale.ROOT, ":%04X", clientPort);
        try (BufferedReader reader = new BufferedReader(new FileReader("/proc/net/tcp"))) {
            String line = reader.readLine(); // header
            while ((line = reader.readLine()) != null) {
                String[] f = line.trim().split("\\s+");
                if (f.length > 7 && f[1].equals(local)) {
                    return Integer.parseInt(f[7]);
                }
            }
        }
        return UNKNOWN;
    }
}
