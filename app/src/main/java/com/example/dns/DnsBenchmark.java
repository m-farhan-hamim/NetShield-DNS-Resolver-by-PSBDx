package com.example.dns;

import android.os.SystemClock;

import java.io.ByteArrayOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

public class DnsBenchmark {

    public static class Result {
        private final String name;
        private final String ip;
        private final String dohUrl;
        private final long latencyMs;
        private final boolean success;

        public Result(String name, String ip, String dohUrl, long latencyMs, boolean success) {
            this.name = name;
            this.ip = ip;
            this.dohUrl = dohUrl;
            this.latencyMs = latencyMs;
            this.success = success;
        }

        public String getName() {
            return name;
        }

        public String getIp() {
            return ip;
        }

        public String getDohUrl() {
            return dohUrl;
        }

        public long getLatencyMs() {
            return latencyMs;
        }

        public boolean isSuccess() {
            return success;
        }
    }

    public static byte[] buildQueryPacket(String domain) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            baos.write(0xAB);
            baos.write(0xCD);
            baos.write(0x01); // Standard query with recursion desired
            baos.write(0x00);
            baos.write(0x00);
            baos.write(0x01); // QDCOUNT = 1
            baos.write(0x00);
            baos.write(0x00);
            baos.write(0x00);
            baos.write(0x00);
            baos.write(0x00);
            baos.write(0x00);

            String[] labels = domain.split("\\.");
            for (String l : labels) {
                byte[] b = l.getBytes("UTF-8");
                baos.write(b.length);
                baos.write(b);
            }
            baos.write(0x00); // end of labels

            // QTYPE = A (1)
            baos.write(0x00);
            baos.write(0x01);
            // QCLASS = IN (1)
            baos.write(0x00);
            baos.write(0x01);

            return baos.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }

    public static List<Result> runBenchmark() {
        List<Result> results = new ArrayList<>();
        byte[] query = buildQueryPacket("google.com");
        if (query == null) return results;

        String[][] servers = new String[][]{
                {"Cloudflare", "1.1.1.1", "https://cloudflare-dns.com/dns-query"},
                {"Google DNS", "8.8.8.8", "https://dns.google/dns-query"},
                {"AdGuard DNS", "94.140.14.14", "https://dns.adguard-dns.com/dns-query"},
                {"Quad9", "9.9.9.9", "https://dns.quad9.net/dns-query"}
        };

        for (String[] s : servers) {
            String name = s[0];
            String ip = s[1];
            String dohUrl = s[2];
            long latency = -1;
            boolean ok = false;

            try {
                long start = SystemClock.elapsedRealtime();
                DatagramSocket socket = new DatagramSocket();
                socket.setSoTimeout(1500);
                InetAddress addr = InetAddress.getByName(ip);
                DatagramPacket sendPkt = new DatagramPacket(query, query.length, addr, 53);
                socket.send(sendPkt);

                byte[] buf = new byte[512];
                DatagramPacket recvPkt = new DatagramPacket(buf, buf.length);
                socket.receive(recvPkt);
                latency = SystemClock.elapsedRealtime() - start;
                ok = (recvPkt.getLength() >= 12);
                socket.close();
            } catch (Exception e) {
                ok = false;
                latency = -1;
            }

            results.add(new Result(name, ip, dohUrl, latency, ok));
        }

        return results;
    }
}
