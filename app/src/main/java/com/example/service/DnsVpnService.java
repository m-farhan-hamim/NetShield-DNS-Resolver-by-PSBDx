package com.example.service;

import android.content.Context;
import android.content.Intent;
import android.app.NotificationManager;
import android.content.SharedPreferences;
import android.net.VpnService;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import com.example.R;
import com.example.dns.DnsResolverEngine;
import com.example.dns.IpPacketUtils;
import com.example.dns.UpstreamNet;
import com.example.proxy.ProxySettings;
import com.example.proxy.ProxyStatus;
import com.example.proxy.core.ProxyEndpoint;
import com.example.proxy.core.ProxyTunEngine;
import com.example.proxy.core.RouteMath;
import com.example.proxy.core.Socks5Exception;
import com.example.util.AppRouting;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
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
    private volatile ProxyTunEngine proxyEngine;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

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

            // Proxy mode: carry ALL traffic so it can be handed to the user's proxy app.
            final ProxySettings proxy = ProxySettings.load(this);
            final boolean proxyMode = proxy.isUsable();
            if (proxyMode) {
                // Everything except the local network (router, printers, casting) goes into the tunnel.
                for (RouteMath.Route r : RouteMath.allExcept(RouteMath.localNetworks())) {
                    builder.addRoute(r.addressString(), r.prefix);
                }
                // IPv6 is not carried: capture it and drop it so it cannot leak around the proxy.
                builder.addAddress("fd00:10::2", 128);
                builder.addRoute("::", 0);
            }

            // Per-app routing, the same for the DNS resolver and for proxy mode: either every app
            // EXCEPT the chosen ones uses the tunnel, or ONLY the chosen ones do (include mode).
            // The proxy app itself is always kept outside the tunnel, or its own connections would
            // loop straight back in. See AppRouting for why the two modes cannot be mixed.
            SharedPreferences prefs = getSharedPreferences(DnsResolverEngine.PREFS_NAME, Context.MODE_PRIVATE);
            AppRouting.apply(builder, prefs, getPackageName(), proxyMode ? proxy.packageName : null);

            vpnInterface = builder.establish();
            if (vpnInterface == null) {
                stopSelf();
                return;
            }

            final FileInputStream inStream = new FileInputStream(vpnInterface.getFileDescriptor());
            outStream = new FileOutputStream(vpnInterface.getFileDescriptor());

            if (proxyMode) {
                startProxyEngine(proxy);
            }

            vpnThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    byte[] packet = new byte[32767];
                    while (isRunning) {
                        try {
                            int length = inStream.read(packet);
                            if (length < 0) {
                                // End of stream: the system closed the tunnel under us (the app was put to
                                // sleep, another VPN took over, the network was reset). It will never
                                // produce data again, and looping on it pins a CPU core at 100%, which is
                                // what users on Android 17 were seeing. Stop the VPN cleanly instead.
                                Log.w(TAG, "Tunnel read returned end-of-stream; shutting the VPN down");
                                mainHandler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (isRunning) {
                                            performTeardown();
                                            stopSelf();
                                        }
                                    }
                                });
                                break;
                            }
                            if (length == 0) {
                                // Nothing to read right now; never spin.
                                Thread.sleep(10);
                                continue;
                            }

                            ProxyTunEngine pe = proxyEngine;
                            if (pe != null) {
                                pe.onPacket(packet, length);
                                continue;
                            }

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
                                            String destIpStr = (udp.destIp[0] & 0xFF) + "."
                                                    + (udp.destIp[1] & 0xFF) + "."
                                                    + (udp.destIp[2] & 0xFF) + "."
                                                    + (udp.destIp[3] & 0xFF);

                                            byte[] dnsResponse = engine.resolve(queryPayload, queryPayload.length,
                                                    clientIp, udp.sourcePort, destIpStr, udp.destPort);
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

    /** Proxy mode: hand the tunnel's traffic to the engine, which relays it through the user's proxy. */
    private void startProxyEngine(final ProxySettings proxy) {
        final ProxyEndpoint endpoint = proxy.endpoint();
        UpstreamNet.init(this);
        UpstreamNet.setProxy(endpoint); // DNS lookups travel through the proxy too
        ProxyStatus.set(this, false);
        proxyEngine = new ProxyTunEngine(endpoint,
                new ProxyTunEngine.PacketSink() {
                    @Override
                    public void write(byte[] packet, int offset, int length) throws IOException {
                        FileOutputStream out = outStream;
                        if (out == null) {
                            throw new IOException("tunnel closed");
                        }
                        synchronized (out) {
                            out.write(packet, offset, length);
                        }
                    }
                },
                new ProxyTunEngine.DnsHook() {
                    @Override
                    public byte[] resolve(byte[] query, int length, String clientIp, int sourcePort,
                                          String destIp, int destPort) {
                        return engine.resolve(query, length, clientIp, sourcePort, destIp, destPort);
                    }
                },
                new ProxyTunEngine.Listener() {
                    @Override
                    public void onProxyDown(Socks5Exception.Kind kind) {
                        postProxyStatus(true);
                    }

                    @Override
                    public void onProxyUp() {
                        postProxyStatus(false);
                    }
                }, true);
        showStatus(getString(R.string.status_vpn_proxy_running));
    }

    private void postProxyStatus(final boolean down) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (!isRunning) {
                    return;
                }
                ProxyStatus.set(DnsVpnService.this, down);
                showStatus(getString(down ? R.string.status_vpn_proxy_down : R.string.status_vpn_proxy_running));
            }
        });
    }

    private void showStatus(String text) {
        ServiceManager.startLiveNotificationUpdates(this, text);
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify(ServiceManager.NOTIFICATION_ID, ServiceManager.buildForegroundNotification(this, text));
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

        ProxyTunEngine pe = proxyEngine;
        proxyEngine = null;
        if (pe != null) {
            pe.close();
        }
        UpstreamNet.setProxy(null);
        ProxyStatus.set(this, false);
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
