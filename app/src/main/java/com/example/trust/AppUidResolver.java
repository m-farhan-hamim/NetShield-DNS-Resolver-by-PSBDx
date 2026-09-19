package com.example.trust;

import android.content.Context;
import android.net.ConnectivityManager;
import android.os.Build;
import android.system.OsConstants;
import android.util.Log;

import java.net.InetAddress;
import java.net.InetSocketAddress;

/**
 * Attributes an intercepted UDP packet (as seen on the VPN's TUN interface)
 * back to the installed app whose socket sent it, using the same technique
 * per-app-firewall VPN apps rely on: the local port survives interception
 * unchanged, so matching (local port, remote address:port) against the
 * live OS connection table identifies the owning UID.
 *
 * Requires Android 10+ (API 29, {@link ConnectivityManager#getConnectionOwnerUid}).
 * On older versions, or if the lookup fails for any reason (timing race,
 * OEM restrictions, etc.), this returns null - callers should treat that as
 * "unknown," not as any particular app.
 */
public final class AppUidResolver {
    private static final String TAG = "AppUidResolver";

    private AppUidResolver() {
    }

    public static boolean isSupported() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q;
    }

    /** Returns the package name owning this UDP connection, or null if unknown/unsupported. */
    public static String resolvePackageName(Context context, String localIp, int localPort,
                                             String remoteIp, int remotePort) {
        if (!isSupported()) return null;
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return null;

            InetSocketAddress local = new InetSocketAddress(InetAddress.getByName(localIp), localPort);
            InetSocketAddress remote = new InetSocketAddress(InetAddress.getByName(remoteIp), remotePort);
            int uid = cm.getConnectionOwnerUid(OsConstants.IPPROTO_UDP, local, remote);
            if (uid <= 0) return null;

            String[] packages = context.getPackageManager().getPackagesForUid(uid);
            return (packages != null && packages.length > 0) ? packages[0] : null;
        } catch (Exception e) {
            Log.d(TAG, "UID resolution failed (non-fatal): " + e.getMessage());
            return null;
        }
    }
}
