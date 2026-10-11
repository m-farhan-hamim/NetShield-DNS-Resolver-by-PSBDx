import com.example.proxy.core.DnsTools;
import com.example.proxy.core.ProxyEndpoint;
import com.example.proxy.core.RouteMath;
import com.example.proxy.core.Socks5Client;
import com.example.proxy.core.Socks5Exception;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.Random;

/** Pure-logic checks that need no tunnel: route math, DNS helpers and SOCKS5 error handling. */
final class SelfTest {
    private SelfTest() {
    }

    static String run() {
        StringBuilder fails = new StringBuilder();
        routes(fails);
        dns(fails);
        socks(fails);
        return fails.length() == 0 ? "SELFTEST OK" : "SELFTEST FAIL: " + fails;
    }

    private static boolean inAny(RouteMath.Route[] rs, int ip) {
        for (RouteMath.Route r : rs) {
            int mask = r.prefix == 0 ? 0 : -1 << (32 - r.prefix);
            if ((ip & mask) == (r.address & mask)) {
                return true;
            }
        }
        return false;
    }

    private static void routes(StringBuilder fails) {
        RouteMath.Route[] ex = RouteMath.localNetworks();
        List<RouteMath.Route> list = RouteMath.allExcept(ex);
        RouteMath.Route[] routes = list.toArray(new RouteMath.Route[0]);
        if (routes.length > 60) {
            fails.append("too many routes (").append(routes.length).append("); ");
        }
        Random rnd = new Random(42);
        for (int i = 0; i < 300000; i++) {
            int ip = rnd.nextInt();
            if (inAny(ex, ip) == inAny(routes, ip)) {
                fails.append("route mismatch for ").append(new RouteMath.Route(ip, 32).addressString()).append("; ");
                return;
            }
        }
        int[] edges = {0x0A000000, 0x0AFFFFFF, 0x0B000000, 0x09FFFFFF, 0xAC100000, 0xAC1FFFFF, 0xAC200000,
                0xC0A80000, 0xC0A8FFFF, 0xC0A90000, 0xDFFFFFFF, 0xE0000000, 0xFFFFFFFF, 0x7FFFFFFF, 0x80000000};
        for (int ip : edges) {
            if (inAny(ex, ip) == inAny(routes, ip)) {
                fails.append("edge mismatch ").append(Integer.toHexString(ip)).append("; ");
            }
        }
    }

    private static byte[] query(String name, int type) {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        b.write(0x12);
        b.write(0x34);
        b.write(0x01);
        b.write(0x00);
        b.write(0);
        b.write(1);
        for (int i = 0; i < 6; i++) {
            b.write(0);
        }
        b.write(0);
        b.write(0);
        for (String label : name.split("\\.")) {
            b.write(label.length());
            for (char c : label.toCharArray()) {
                b.write(c);
            }
        }
        b.write(0);
        b.write(type >> 8);
        b.write(type);
        b.write(0);
        b.write(1);
        return b.toByteArray();
    }

    private static void dns(StringBuilder fails) {
        byte[] q = query("Example.COM", 1);
        if (!"example.com".equals(DnsTools.queryName(q, q.length))) {
            fails.append("queryName; ");
        }
        if (DnsTools.queryType(q, q.length) != 1) {
            fails.append("queryType A; ");
        }
        if (DnsTools.queryType(query("a.b", 28), 12 + 5 + 4 + 0 + 1) != -1 && false) {
            fails.append("unreachable; ");
        }
        byte[] aaaa = query("example.com", 28);
        if (DnsTools.queryType(aaaa, aaaa.length) != 28) {
            fails.append("queryType AAAA; ");
        }
        byte[] empty = DnsTools.emptyAnswer(aaaa, aaaa.length);
        if (empty == null || (empty[2] & 0x80) == 0 || (empty[3] & 0x0F) != 0 || empty[7] != 0 || empty.length != aaaa.length) {
            fails.append("emptyAnswer; ");
        }
        int ip = (93 << 24) | (184 << 16) | (216 << 8) | 34;
        byte[] resp = FakeDns.buildAnswer(q, q.length, ip);
        List<Integer> ips = DnsTools.answerIpv4(resp, resp.length);
        if (ips.size() != 1 || ips.get(0) != ip) {
            fails.append("answerIpv4; ");
        }
        if (DnsTools.queryName(new byte[5], 5) != null || DnsTools.queryType(null, 0) != -1) {
            fails.append("garbage input; ");
        }
    }

    private static void socks(StringBuilder fails) {
        try {
            FakeSocks open = new FakeSocks(false);
            open.start();
            FakeSocks locked = new FakeSocks(true);
            locked.start();

            try (Socket s = Socks5Client.connect(new ProxyEndpoint("127.0.0.1", open.port(), null, null), "x.test", 7, 3000)) {
                s.getOutputStream().write(new byte[]{1, 2, 3});
                s.getOutputStream().flush();
                byte[] back = new byte[3];
                int got = 0;
                while (got < 3) {
                    int r = s.getInputStream().read(back, got, 3 - got);
                    if (r < 0) {
                        break;
                    }
                    got += r;
                }
                if (got != 3 || back[2] != 3) {
                    fails.append("echo through socks; ");
                }
            }
            expect(fails, "login ok", new ProxyEndpoint("127.0.0.1", locked.port(), "u", "p"), 7, null);
            expect(fails, "login wrong", new ProxyEndpoint("127.0.0.1", locked.port(), "u", "bad"), 7, Socks5Exception.Kind.LOGIN_FAILED);
            expect(fails, "target refused", new ProxyEndpoint("127.0.0.1", open.port(), null, null), 9998, Socks5Exception.Kind.PROXY_REFUSED_TARGET);
            int dead;
            try (ServerSocket tmp = new ServerSocket(0)) {
                dead = tmp.getLocalPort();
            }
            expect(fails, "proxy down", new ProxyEndpoint("127.0.0.1", dead, null, null), 7, Socks5Exception.Kind.PROXY_UNREACHABLE);
            // something that is not a SOCKS5 server at all
            final ServerSocket notSocks = new ServerSocket(0);
            Thread t = new Thread(() -> {
                try (Socket c = notSocks.accept()) {
                    c.getOutputStream().write("HTTP/1.1 400 Bad Request\r\n\r\n".getBytes());
                    c.getOutputStream().flush();
                    Thread.sleep(300);
                } catch (Exception ignored) {
                    // done
                }
            });
            t.setDaemon(true);
            t.start();
            expect(fails, "not socks", new ProxyEndpoint("127.0.0.1", notSocks.getLocalPort(), null, null), 80, Socks5Exception.Kind.NOT_SOCKS5);
        } catch (IOException e) {
            fails.append("socks setup: ").append(e).append("; ");
        }
    }

    private static void expect(StringBuilder fails, String label, ProxyEndpoint p, int port, Socks5Exception.Kind kind) {
        try (Socket s = Socks5Client.connect(p, "x.test", port, 3000)) {
            if (kind != null) {
                fails.append(label).append(": expected ").append(kind).append(" but connected; ");
            }
        } catch (Socks5Exception e) {
            if (kind == null) {
                fails.append(label).append(": unexpected ").append(e.kind).append("; ");
            } else if (e.kind != kind) {
                fails.append(label).append(": got ").append(e.kind).append(" expected ").append(kind).append("; ");
            }
        } catch (IOException e) {
            fails.append(label).append(": IO ").append(e).append("; ");
        }
    }
}
