package com.example.service;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import com.example.R;
import com.example.dns.DnsResolverEngine;
import com.example.dns.IpPacketUtils;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class DnsVpnService extends VpnService {
    private static final String TAG = "DnsVpnService";
    private ParcelFileDescriptor vpnInterface;
    private Thread vpnThread;
    private volatile boolean isRunning = false;
    private ExecutorService workerPool;
    private DnsResolverEngine engine;
    private FileOutputStream outStream;

    @Override
    public void onCreate() {
        super.onCreate();
        engine = DnsResolverEngine.getInstance(this);
        workerPool = Executors.newFixedThreadPool(4);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ServiceManager.ACTION_STOP.equals(intent.getAction())) {
            Log.d(TAG, "onStartCommand: ACTION_STOP received, tearing down directly");
            performTeardown();
            stopSelf();
            return START_NOT_STICKY;
        }

        Log.d(TAG, "onStartCommand: isRunning=" + isRunning + " startId=" + startId);
        startForeground(ServiceManager.NOTIFICATION_ID,
                ServiceManager.buildForegroundNotification(this, getString(R.string.status_vpn_running)));
        ServiceManager.setCurrentState(this, ServiceManager.STATE_VPN);
        ServiceManager.startLiveNotificationUpdates(this, getString(R.string.status_vpn_running));

        if (!isRunning) {
            startVpn();
        }
        // NOT_STICKY: if this process is killed, stay stopped rather than
        // have the system silently restart the VPN behind the user's back.
        return START_NOT_STICKY;
    }

    private void startVpn() {
        isRunning = true;
        try {
            Builder builder = new Builder();
            builder.setSession("Local DNS Resolver");
            builder.setMtu(1500);
            builder.addAddress("10.0.0.2", 32);
            builder.addDnsServer("10.0.0.1");
            builder.addRoute("10.0.0.1", 32);

            // Apply Per-App Split Tunneling (Disallowed Applications)
            SharedPreferences prefs = getSharedPreferences(DnsResolverEngine.PREFS_NAME, Context.MODE_PRIVATE);
            Set<String> excludedApps = prefs.getStringSet("split_tunnel_apps", null);
            if (excludedApps != null && !excludedApps.isEmpty()) {
                for (String pkg : excludedApps) {
                    try {
                        builder.addDisallowedApplication(pkg);
                    } catch (Exception ignored) {
                    }
                }
            }

            vpnInterface = builder.establish();
            if (vpnInterface == null) {
                stopSelf();
                return;
            }

            final FileInputStream inStream = new FileInputStream(vpnInterface.getFileDescriptor());
            outStream = new FileOutputStream(vpnInterface.getFileDescriptor());

            vpnThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    byte[] packet = new byte[32767];
                    while (isRunning) {
                        try {
                            int length = inStream.read(packet);
                            if (length <= 0) continue;

                            final byte[] packetCopy = new byte[length];
                            System.arraycopy(packet, 0, packetCopy, 0, length);

                            final IpPacketUtils.UdpPacketInfo udp = IpPacketUtils.parseUdpPacket(packetCopy, length);
                            if (udp != null && udp.destPort == 53) {
                                workerPool.execute(new Runnable() {
                                    @Override
                                    public void run() {
                                        try {
                                            byte[] queryPayload = new byte[udp.payloadLength];
                                            System.arraycopy(packetCopy, udp.payloadOffset, queryPayload, 0, udp.payloadLength);

                                            String clientIp = (udp.sourceIp[0] & 0xFF) + "."
                                                    + (udp.sourceIp[1] & 0xFF) + "."
                                                    + (udp.sourceIp[2] & 0xFF) + "."
                                                    + (udp.sourceIp[3] & 0xFF);

                                            byte[] dnsResponse = engine.resolve(queryPayload, queryPayload.length, clientIp);
                                            if (dnsResponse != null && isRunning && outStream != null) {
                                                byte[] responsePacket = IpPacketUtils.buildUdpResponsePacket(udp, dnsResponse);
                                                synchronized (outStream) {
                                                    outStream.write(responsePacket);
                                                    outStream.flush();
                                                }
                                            }
                                        } catch (Exception ignored) {
                                        }
                                    }
                                });
                            }
                        } catch (Exception e) {
                            break;
                        }
                    }
                }
            }, "DnsVpnThread");
            vpnThread.start();

        } catch (Exception e) {
            stopSelf();
        }
    }

    /**
     * Called by the system if VPN permission is revoked externally (e.g. the
     * user disables it from system VPN settings, or another VPN app takes
     * over). Route this through the exact same teardown path as an explicit
     * stop so state and the notification never end up stale.
     */
    @Override
    public void onRevoke() {
        performTeardown();
        stopSelf();
        super.onRevoke();
    }

    /**
     * Tears everything down directly and unconditionally - closes the TUN
     * interface, cancels the notification, and updates the tracked state -
     * regardless of whether the Service object itself ever actually gets
     * destroyed afterward.
     *
     * This exists because relying on Context.stopService() -> onDestroy()
     * alone is NOT reliable for a VpnService: once a tunnel is established,
     * the system also holds its own internal binding to this Service to
     * manage the VPN connection, and a Service is only destroyed once it is
     * BOTH un-started AND fully unbound. If that system-held binding hasn't
     * cleared yet, stopService() can silently fail to ever invoke
     * onDestroy() - which left the tunnel (and its notification) running
     * forever no matter what the app UI said. Closing the interface here,
     * directly, from an explicit self-stop action (see ServiceManager.ACTION_STOP)
     * fixes that regardless of what the framework decides to do with the
     * Service object afterward.
     */
    private synchronized void performTeardown() {
        if (!isRunning && vpnInterface == null) {
            Log.d(TAG, "performTeardown: already torn down, skipping");
            return;
        }
        Log.d(TAG, "performTeardown: tearing down VPN");
        isRunning = false;
        ServiceManager.stopLiveNotificationUpdates();

        if (vpnThread != null) {
            vpnThread.interrupt();
        }
        if (workerPool != null) {
            workerPool.shutdownNow();
        }
        if (outStream != null) {
            try {
                outStream.close();
            } catch (Exception ignored) {
            }
            outStream = null;
        }
        if (vpnInterface != null) {
            try {
                vpnInterface.close();
            } catch (Exception ignored) {
            }
            vpnInterface = null;
        }
        // Closing the interface unblocks the blocking read() above; wait
        // (briefly) for the loop to actually exit so "stopped" is true by
        // the time this method returns, instead of leaving a straggling
        // thread that keeps running for a moment after the UI says it's off.
        if (vpnThread != null) {
            try {
                vpnThread.join(TimeUnit.SECONDS.toMillis(1));
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            vpnThread = null;
        }
        // Cancel the notification and clear tracked state directly here -
        // don't wait for onDestroy(), which may never come (see above).
        stopForeground(true);
        ServiceManager.setCurrentState(this, ServiceManager.STATE_STOPPED);
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");
        // Defensive: covers the case where the Service is destroyed via some
        // path other than our own ACTION_STOP (e.g. the system killing it
        // directly). performTeardown() is idempotent, so calling it again
        // here even after an explicit stop already ran it is harmless.
        performTeardown();
        super.onDestroy();
    }
}
