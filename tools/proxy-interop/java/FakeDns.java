import com.example.proxy.core.DnsTools;
import com.example.proxy.core.ProxyTunEngine;

import java.io.ByteArrayOutputStream;

/** DNS stand-in: example.test -> 10.0.0.77, everything else -> 93.184.216.34 (A only). */
final class FakeDns implements ProxyTunEngine.DnsHook {
    @Override
    public byte[] resolve(byte[] q, int len, String clientIp, int sourcePort, String destIp, int destPort) {
        int type = DnsTools.queryType(q, len);
        if (type != DnsTools.TYPE_A) {
            return DnsTools.emptyAnswer(q, len);
        }
        String name = DnsTools.queryName(q, len);
        int ip = "example.test".equals(name) ? ((10 << 24) | 77) : 0x5DB8D822;
        return buildAnswer(q, len, ip);
    }

    static byte[] buildAnswer(byte[] q, int len, int ip) {
        int pos = 12;
        while (q[pos] != 0) {
            pos += (q[pos] & 0xFF) + 1;
        }
        int qEnd = pos + 1 + 4;
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        b.write(q[0]);
        b.write(q[1]);
        b.write(0x81);
        b.write(0x80);
        b.write(0);
        b.write(1);
        b.write(0);
        b.write(1);
        b.write(0);
        b.write(0);
        b.write(0);
        b.write(0);
        b.write(q, 12, qEnd - 12);
        b.write(0xC0);
        b.write(0x0C);
        b.write(0);
        b.write(1);
        b.write(0);
        b.write(1);
        b.write(0);
        b.write(0);
        b.write(0);
        b.write(60);
        b.write(0);
        b.write(4);
        b.write(ip >>> 24);
        b.write(ip >>> 16);
        b.write(ip >>> 8);
        b.write(ip);
        return b.toByteArray();
    }
}
