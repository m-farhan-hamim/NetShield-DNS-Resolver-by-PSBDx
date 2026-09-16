package com.example.hotspot;

import android.util.Log;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads /proc/net/arp for a best-effort list of devices seen on the local
 * network (Wi-Fi or hotspot). There is no public Android API for a
 * non-privileged app to get an authoritative hotspot client list - this is
 * the closest available fallback, and it is NOT guaranteed to work on every
 * device/Android version (some kernels/SELinux policies restrict reading
 * this file even for the ARP entries of your own connections). Returns an
 * empty list rather than throwing when unavailable.
 */
public final class LocalNetworkScanner {
    private static final String TAG = "LocalNetworkScanner";
    private static final String ARP_PATH = "/proc/net/arp";
    private static final Pattern MAC_PATTERN = Pattern.compile("^([0-9a-fA-F]{2}:){5}[0-9a-fA-F]{2}$");

    public static class ScannedDevice {
        public final String ip;
        public final String mac;

        public ScannedDevice(String ip, String mac) {
            this.ip = ip;
            this.mac = mac;
        }
    }

    private LocalNetworkScanner() {
    }

    public static List<ScannedDevice> scan() {
        List<ScannedDevice> devices = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(ARP_PATH))) {
            String line = reader.readLine(); // header row
            while ((line = reader.readLine()) != null) {
                String[] fields = line.trim().split("\\s+");
                // Format: IP address / HW type / Flags / HW address / Mask / Device
                if (fields.length >= 4) {
                    String ip = fields[0];
                    String mac = fields[3];
                    if (MAC_PATTERN.matcher(mac).matches() && !"00:00:00:00:00:00".equals(mac)) {
                        devices.add(new ScannedDevice(ip, mac));
                    }
                }
            }
        } catch (Exception e) {
            // Expected on many devices/Android versions - not an error the user needs to see.
            Log.d(TAG, "ARP table unavailable: " + e.getMessage());
        }
        return devices;
    }
}
