import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * A fake SOCKS5 proxy with a few canned destinations (selected by port):
 * 8080 HTTP (/size/N, /whoami), 7 echo, 9001 server-speaks-first, 9100 slow upload sink,
 * 9999 accepts then closes, 9998 refuses the target.
 */
final class FakeSocks {
    private final boolean requireAuth;
    private ServerSocket server;

    FakeSocks(boolean requireAuth) {
        this.requireAuth = requireAuth;
    }

    void start() throws IOException {
        server = new ServerSocket(0, 200, InetAddress.getByName("127.0.0.1"));
        Thread t = new Thread(() -> {
            while (true) {
                try {
                    final Socket s = server.accept();
                    Thread h = new Thread(() -> handle(s));
                    h.setDaemon(true);
                    h.start();
                } catch (IOException e) {
                    return;
                }
            }
        }, "fake-socks-accept");
        t.setDaemon(true);
        t.start();
    }

    int port() {
        return server.getLocalPort();
    }

    private void handle(Socket s) {
        try (Socket sock = s) {
            sock.setTcpNoDelay(true);
            DataInputStream in = new DataInputStream(new BufferedInputStream(sock.getInputStream(), 1 << 16));
            OutputStream out = sock.getOutputStream();
            in.readUnsignedByte();
            int n = in.readUnsignedByte();
            in.readFully(new byte[n]);
            if (requireAuth) {
                out.write(new byte[]{5, 2});
                out.flush();
                in.readUnsignedByte();
                byte[] u = new byte[in.readUnsignedByte()];
                in.readFully(u);
                byte[] p = new byte[in.readUnsignedByte()];
                in.readFully(p);
                boolean ok = "u".equals(new String(u, StandardCharsets.UTF_8)) && "p".equals(new String(p, StandardCharsets.UTF_8));
                out.write(new byte[]{1, (byte) (ok ? 0 : 1)});
                out.flush();
                if (!ok) {
                    return;
                }
            } else {
                out.write(new byte[]{5, 0});
                out.flush();
            }
            in.readUnsignedByte();
            in.readUnsignedByte();
            in.readUnsignedByte();
            int atyp = in.readUnsignedByte();
            String host;
            if (atyp == 1) {
                byte[] a = new byte[4];
                in.readFully(a);
                host = (a[0] & 0xFF) + "." + (a[1] & 0xFF) + "." + (a[2] & 0xFF) + "." + (a[3] & 0xFF);
            } else if (atyp == 3) {
                byte[] a = new byte[in.readUnsignedByte()];
                in.readFully(a);
                host = new String(a, StandardCharsets.UTF_8);
            } else {
                in.readFully(new byte[16]);
                host = "ipv6";
            }
            int port = in.readUnsignedShort();
            if (port == 9998) {
                out.write(new byte[]{5, 5, 0, 1, 0, 0, 0, 0, 0, 0});
                out.flush();
                return;
            }
            out.write(new byte[]{5, 0, 0, 1, 0, 0, 0, 0, 0, 0});
            out.flush();
            serve(host, port, in, out);
        } catch (IOException ignored) {
            // connection ended
        }
    }

    private void serve(String host, int port, DataInputStream in, OutputStream out) throws IOException {
        switch (port) {
            case 8080:
                http(host, in, out);
                break;
            case 7: {
                byte[] b = new byte[8192];
                int r;
                while ((r = in.read(b)) > 0) {
                    out.write(b, 0, r);
                    out.flush();
                }
                break;
            }
            case 9001:
                pattern(out, 100000);
                out.flush();
                break;
            case 9100: {
                byte[] b = new byte[1024];
                long total = 0;
                int r;
                while ((r = in.read(b)) > 0) {
                    total += r;
                    try {
                        Thread.sleep(1);
                    } catch (InterruptedException e) {
                        return;
                    }
                }
                for (int i = 7; i >= 0; i--) {
                    out.write((int) (total >>> (8 * i)));
                }
                out.flush();
                break;
            }
            default:
                break; // 9999 and anything else: just close
        }
    }

    private void http(String host, DataInputStream in, OutputStream out) throws IOException {
        String first = readLine(in);
        while (!readLine(in).isEmpty()) {
            // skip headers
        }
        String[] parts = first.split(" ");
        String path = parts.length > 1 ? parts[1] : "/";
        if (path.startsWith("/size/")) {
            long n = Long.parseLong(path.substring(6));
            String head = "HTTP/1.1 200 OK\r\nContent-Length: " + n + "\r\nX-Requested-Host: " + host
                    + "\r\nConnection: close\r\n\r\n";
            out.write(head.getBytes(StandardCharsets.US_ASCII));
            pattern(out, n);
        } else {
            byte[] body = ("host=" + host).getBytes(StandardCharsets.US_ASCII);
            String head = "HTTP/1.1 200 OK\r\nContent-Length: " + body.length + "\r\nConnection: close\r\n\r\n";
            out.write(head.getBytes(StandardCharsets.US_ASCII));
            out.write(body);
        }
        out.flush();
    }

    /** byte i = (i * 31 + 7) & 0xFF, so the client can verify it without storing it. */
    private static void pattern(OutputStream out, long n) throws IOException {
        byte[] chunk = new byte[65536];
        long off = 0;
        while (off < n) {
            int len = (int) Math.min(chunk.length, n - off);
            for (int i = 0; i < len; i++) {
                chunk[i] = (byte) (((off + i) * 31 + 7) & 0xFF);
            }
            out.write(chunk, 0, len);
            off += len;
        }
    }

    private static String readLine(DataInputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        int c;
        while ((c = in.read()) >= 0) {
            if (c == '\n') {
                break;
            }
            if (c != '\r') {
                sb.append((char) c);
            }
        }
        return sb.toString();
    }
}
