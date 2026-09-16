package com.example.hotspot;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

/** Finds this device's non-loopback IPv4 address(es) - the address other devices on the same network would use to reach it. */
public final class LocalIpFinder {

    private LocalIpFinder() {
    }

    public static List<String> getLocalIpv4Addresses() {
        List<String> addresses = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces == null) return addresses;
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (!iface.isUp() || iface.isLoopback()) continue;
                Enumeration<InetAddress> iaddrs = iface.getInetAddresses();
                while (iaddrs.hasMoreElements()) {
                    InetAddress addr = iaddrs.nextElement();
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        addresses.add(addr.getHostAddress());
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return addresses.isEmpty() ? Collections.emptyList() : addresses;
    }

    /** Best single address to show the user, preferring typical hotspot/Wi-Fi ranges, or null if none found. */
    public static String getBestLocalIpv4() {
        List<String> all = getLocalIpv4Addresses();
        for (String ip : all) {
            if (ip.startsWith("192.168.") || ip.startsWith("10.") || ip.startsWith("172.")) {
                return ip;
            }
        }
        return all.isEmpty() ? null : all.get(0);
    }
}
