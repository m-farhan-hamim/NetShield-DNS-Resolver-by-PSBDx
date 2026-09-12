package com.example.service;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.util.Log;

import com.example.R;
import com.example.dns.DnsResolverEngine;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class DnsServerService extends Service {
    private static final String TAG = "DnsServerService";
    private DatagramSocket serverSocket;
    private Thread serverThread;
    private volatile boolean isRunning = false;
    private ExecutorService workerPool;
    private DnsResolverEngine engine;

    @Override
    public void onCreate() {
        super.onCreate();
        engine = DnsResolverEngine.getInstance(this);
        workerPool = Executors.newFixedThreadPool(4);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand: isRunning=" + isRunning + " startId=" + startId);
        startForeground(ServiceManager.NOTIFICATION_ID,
                ServiceManager.buildForegroundNotification(this, getString(R.string.status_server_running)));
        ServiceManager.setCurrentState(this, ServiceManager.STATE_SERVER);
        ServiceManager.startLiveNotificationUpdates(this, getString(R.string.status_server_running));

        if (!isRunning) {
            startDnsServer();
        }
        return START_NOT_STICKY;
    }

    private void startDnsServer() {
        isRunning = true;
        SharedPreferences prefs = getSharedPreferences(DnsResolverEngine.PREFS_NAME, Context.MODE_PRIVATE);
        final int port = prefs.getInt("server_port", 5353);

        serverThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    serverSocket = new DatagramSocket(null);
                    serverSocket.setReuseAddress(true);
                    serverSocket.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), port));

                    byte[] buffer = new byte[1500];

                    while (isRunning && !serverSocket.isClosed()) {
                        final DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                        serverSocket.receive(packet);

                        final byte[] queryData = new byte[packet.getLength()];
                        System.arraycopy(packet.getData(), 0, queryData, 0, packet.getLength());
                        final InetAddress clientAddr = packet.getAddress();
                        final int clientPort = packet.getPort();

                        workerPool.execute(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    String clientIp = clientAddr != null ? clientAddr.getHostAddress() : "127.0.0.1";
                                    byte[] response = engine.resolve(queryData, queryData.length, clientIp);
                                    if (response != null && isRunning && serverSocket != null && !serverSocket.isClosed()) {
                                        DatagramPacket respPacket = new DatagramPacket(
                                                response, response.length, clientAddr, clientPort
                                        );
                                        serverSocket.send(respPacket);
                                    }
                                } catch (Exception ignored) {
                                }
                            }
                        });
                    }
                } catch (Exception e) {
                    // Socket closed or port conflict
                }
            }
        }, "DnsServerThread");
        serverThread.start();
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy: tearing down DNS server");
        isRunning = false;
        ServiceManager.stopLiveNotificationUpdates();

        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (Exception ignored) {
            }
        }
        if (serverThread != null) {
            serverThread.interrupt();
        }
        if (workerPool != null) {
            workerPool.shutdownNow();
        }
        // Closing the socket unblocks the blocking receive() above; wait
        // (briefly) for the loop to actually exit so "stopped" is true by
        // the time this method returns.
        if (serverThread != null) {
            try {
                serverThread.join(TimeUnit.SECONDS.toMillis(1));
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
        ServiceManager.setCurrentState(this, ServiceManager.STATE_STOPPED);
        stopForeground(true);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
