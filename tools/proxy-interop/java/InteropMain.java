import com.example.proxy.core.ProxyEndpoint;
import com.example.proxy.core.ProxyTunEngine;
import com.example.proxy.core.Socks5Exception;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Runs the real engine behind a framed TCP "cable" ([2-byte length][IP packet]) that a Python
 * script connects to a Linux TUN device, so the kernel's own TCP stack is the client.
 */
public final class InteropMain {
    public static void main(String[] args) throws Exception {
        System.out.println(SelfTest.run());
        System.out.flush();

        FakeSocks socks = new FakeSocks(false);
        socks.start();
        ServerSocket cable = new ServerSocket(39001, 1, InetAddress.getByName("127.0.0.1"));
        System.out.println("READY");
        System.out.flush();
        Socket bridge = cable.accept();
        bridge.setTcpNoDelay(true);
        final DataOutputStream out = new DataOutputStream(new BufferedOutputStream(bridge.getOutputStream(), 1 << 16));
        DataInputStream in = new DataInputStream(new BufferedInputStream(bridge.getInputStream(), 1 << 16));

        ProxyTunEngine.PacketSink sink = (pkt, off, len) -> {
            synchronized (out) {
                out.writeShort(len);
                out.write(pkt, off, len);
                out.flush();
            }
        };
        ProxyTunEngine.Listener listener = new ProxyTunEngine.Listener() {
            @Override
            public void onProxyDown(Socks5Exception.Kind kind) {
                System.out.println("EVENT proxy-down " + kind);
            }

            @Override
            public void onProxyUp() {
                System.out.println("EVENT proxy-up");
            }
        };
        final ProxyTunEngine engine = new ProxyTunEngine(
                new ProxyEndpoint("127.0.0.1", socks.port(), null, null), sink, new FakeDns(), listener, true);

        Thread stats = new Thread(() -> {
            while (true) {
                System.out.println("STATS active=" + engine.activeSessions() + " total=" + engine.totalConnections()
                        + " failed=" + engine.failedConnections() + " up=" + engine.bytesUp() + " down=" + engine.bytesDown());
                System.out.flush();
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }, "stats");
        stats.setDaemon(true);
        stats.start();

        byte[] buf = new byte[70000];
        try {
            while (true) {
                int len = in.readUnsignedShort();
                in.readFully(buf, 0, len);
                engine.onPacket(buf, len);
            }
        } catch (EOFException e) {
            // the Python side closed the cable: done
        }
        engine.close();
    }
}
