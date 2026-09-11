package com.example.dns;

import java.io.ByteArrayOutputStream;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.nio.ByteBuffer;

public class DnsPacketParser {

    public static class DnsQuestion {
        public int transactionId;
        public String domain;
        public int qType;
        public int qClass;
        public int questionSectionLength; // length of QNAME + QTYPE + QCLASS
    }

    public static DnsQuestion parseQuestion(byte[] data, int length) {
        if (data == null || length < 12) {
            return null;
        }

        try {
            ByteBuffer buffer = ByteBuffer.wrap(data, 0, length);
            DnsQuestion q = new DnsQuestion();
            q.transactionId = buffer.getShort(0) & 0xFFFF;

            int qdCount = buffer.getShort(4) & 0xFFFF;
            if (qdCount < 1) {
                return null;
            }

            int offset = 12;
            StringBuilder domain = new StringBuilder();

            while (offset < length) {
                int len = data[offset] & 0xFF;
                if (len == 0) {
                    offset++;
                    break;
                }
                if ((len & 0xC0) == 0xC0) {
                    // Pointer in question section (uncommon for standard queries)
                    offset += 2;
                    break;
                }
                offset++;
                if (offset + len > length) {
                    return null;
                }
                for (int i = 0; i < len; i++) {
                    domain.append((char) data[offset++]);
                }
                domain.append('.');
            }

            if (domain.length() > 0 && domain.charAt(domain.length() - 1) == '.') {
                domain.setLength(domain.length() - 1);
            }
            q.domain = domain.toString().toLowerCase();

            if (offset + 4 <= length) {
                q.qType = ((data[offset] & 0xFF) << 8) | (data[offset + 1] & 0xFF);
                q.qClass = ((data[offset + 2] & 0xFF) << 8) | (data[offset + 3] & 0xFF);
                offset += 4;
            }

            q.questionSectionLength = offset - 12;
            return q;
        } catch (Exception e) {
            return null;
        }
    }

    public static String getTypeName(int qType) {
        switch (qType) {
            case 1:
                return "A";
            case 28:
                return "AAAA";
            case 5:
                return "CNAME";
            case 15:
                return "MX";
            case 16:
                return "TXT";
            case 12:
                return "PTR";
            case 2:
                return "NS";
            case 6:
                return "SOA";
            case 65:
                return "HTTPS";
            default:
                return "TYPE" + qType;
        }
    }

    public static int getTransactionId(byte[] packet) {
        if (packet == null || packet.length < 2) return 0;
        return ((packet[0] & 0xFF) << 8) | (packet[1] & 0xFF);
    }

    public static void setTransactionId(byte[] packet, int id) {
        if (packet != null && packet.length >= 2) {
            packet[0] = (byte) ((id >> 8) & 0xFF);
            packet[1] = (byte) (id & 0xFF);
        }
    }

    public static long extractTtl(byte[] response, int length) {
        if (response == null || length < 12) return 60;
        try {
            int qdCount = ((response[4] & 0xFF) << 8) | (response[5] & 0xFF);
            int anCount = ((response[6] & 0xFF) << 8) | (response[7] & 0xFF);
            if (anCount == 0) return 60;

            int offset = 12;
            // Skip questions
            for (int i = 0; i < qdCount && offset < length; i++) {
                while (offset < length) {
                    int len = response[offset] & 0xFF;
                    if (len == 0) {
                        offset++;
                        break;
                    }
                    if ((len & 0xC0) == 0xC0) {
                        offset += 2;
                        break;
                    }
                    offset += 1 + len;
                }
                offset += 4; // QTYPE + QCLASS
            }

            // In first answer record, skip NAME
            if (offset < length) {
                int len = response[offset] & 0xFF;
                if ((len & 0xC0) == 0xC0) {
                    offset += 2;
                } else {
                    while (offset < length) {
                        int l = response[offset] & 0xFF;
                        if (l == 0) {
                            offset++;
                            break;
                        }
                        offset += 1 + l;
                    }
                }
                // Now at TYPE (2) + CLASS (2) + TTL (4)
                if (offset + 8 <= length) {
                    offset += 4;
                    long ttl = ((long) (response[offset] & 0xFF) << 24)
                            | ((long) (response[offset + 1] & 0xFF) << 16)
                            | ((long) (response[offset + 2] & 0xFF) << 8)
                            | ((long) (response[offset + 3] & 0xFF));
                    if (ttl > 0 && ttl < 86400) {
                        return ttl;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return 60;
    }

    public static byte[] buildBlockedResponse(byte[] query, int queryLen, String action) {
        DnsQuestion q = parseQuestion(query, queryLen);
        if (q == null) return null;

        boolean isNxDomain = "NXDOMAIN".equalsIgnoreCase(action);

        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            // Header (12 bytes)
            out.write(query[0]); // ID high
            out.write(query[1]); // ID low

            // Flags: QR=1 (response), Opcode=0, AA=1, TC=0, RD=1, RA=1, Z=0, RCODE
            int flags = 0x8580; // QR=1, AA=1, RD=1, RA=1
            if (isNxDomain) {
                flags |= 0x0003; // RCODE = 3 (Name Error)
            }
            out.write((flags >> 8) & 0xFF);
            out.write(flags & 0xFF);

            // QDCOUNT = 1
            out.write(0x00);
            out.write(0x01);

            if (isNxDomain) {
                // ANCOUNT = 0
                out.write(0x00);
                out.write(0x00);
            } else {
                // ANCOUNT = 1 (Return 0.0.0.0 or ::)
                out.write(0x00);
                out.write(0x01);
            }

            // NSCOUNT = 0, ARCOUNT = 0
            out.write(0x00);
            out.write(0x00);
            out.write(0x00);
            out.write(0x00);

            // Copy Question section from query
            int questionEnd = 12 + q.questionSectionLength;
            if (questionEnd > queryLen) questionEnd = queryLen;
            out.write(query, 12, questionEnd - 12);

            // If ZERO_IP, append synthesized Answer
            if (!isNxDomain) {
                // NAME: Compression pointer to question name at offset 12 (0xC00C)
                out.write(0xC0);
                out.write(0x0C);

                if (q.qType == 28) {
                    // AAAA (IPv6)
                    out.write(0x00);
                    out.write(0x1C); // TYPE AAAA
                    out.write(0x00);
                    out.write(0x01); // CLASS IN
                    out.write(new byte[]{0x00, 0x00, 0x00, 0x3C}); // TTL 60s
                    out.write(0x00);
                    out.write(0x10); // RDLENGTH 16 bytes
                    out.write(new byte[16]); // 16 zero bytes (::)
                } else {
                    // A (IPv4) or fallback
                    out.write(0x00);
                    out.write(0x01); // TYPE A
                    out.write(0x00);
                    out.write(0x01); // CLASS IN
                    out.write(new byte[]{0x00, 0x00, 0x00, 0x3C}); // TTL 60s
                    out.write(0x00);
                    out.write(0x04); // RDLENGTH 4 bytes
                    out.write(new byte[]{0x00, 0x00, 0x00, 0x00}); // 0.0.0.0
                }
            }

            return out.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }

    public static byte[] buildCustomMappingResponse(byte[] query, int queryLen, String targetIp) {
        DnsQuestion q = parseQuestion(query, queryLen);
        if (q == null) return null;

        try {
            InetAddress addr = InetAddress.getByName(targetIp);
            byte[] ipBytes = addr.getAddress();

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            // Header
            out.write(query[0]);
            out.write(query[1]);
            int flags = 0x8580; // QR=1, AA=1, RD=1, RA=1, RCODE=0
            out.write((flags >> 8) & 0xFF);
            out.write(flags & 0xFF);
            out.write(0x00);
            out.write(0x01); // QDCOUNT = 1
            out.write(0x00);
            out.write(0x01); // ANCOUNT = 1
            out.write(0x00);
            out.write(0x00); // NSCOUNT = 0
            out.write(0x00);
            out.write(0x00); // ARCOUNT = 0

            // Copy Question
            int questionEnd = 12 + q.questionSectionLength;
            if (questionEnd > queryLen) questionEnd = queryLen;
            out.write(query, 12, questionEnd - 12);

            // Answer Record
            out.write(0xC0);
            out.write(0x0C); // Pointer to QNAME

            if (addr instanceof Inet6Address) {
                out.write(0x00);
                out.write(0x1C); // TYPE AAAA
            } else {
                out.write(0x00);
                out.write(0x01); // TYPE A
            }
            out.write(0x00);
            out.write(0x01); // CLASS IN
            out.write(new byte[]{0x00, 0x00, 0x01, 0x2C}); // TTL 300s
            out.write((ipBytes.length >> 8) & 0xFF);
            out.write(ipBytes.length & 0xFF);
            out.write(ipBytes);

            return out.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }
}
