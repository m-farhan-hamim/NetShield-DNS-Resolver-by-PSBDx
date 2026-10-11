package com.example.proxy.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Tiny DNS helpers for the proxy path (no Android dependencies). */
public final class DnsTools {
    public static final int TYPE_A = 1;
    public static final int TYPE_AAAA = 28;

    private DnsTools() {
    }

    /** Offset just after the question section, or -1 if the packet is not a plain one-question query. */
    private static int questionEnd(byte[] q, int len) {
        if (q == null || len < 17) {
            return -1;
        }
        if ((((q[4] & 0xFF) << 8) | (q[5] & 0xFF)) != 1) {
            return -1;
        }
        int pos = 12;
        while (pos < len) {
            int l = q[pos] & 0xFF;
            if (l == 0) {
                pos++;
                break;
            }
            if ((l & 0xC0) != 0) {
                return -1; // compression pointers are not valid in a question
            }
            pos += l + 1;
        }
        return pos + 4 <= len ? pos + 4 : -1;
    }

    /** Query type (1 = A, 28 = AAAA, ...) or -1 if unparseable. */
    public static int queryType(byte[] q, int len) {
        int end = questionEnd(q, len);
        if (end < 0) {
            return -1;
        }
        return ((q[end - 4] & 0xFF) << 8) | (q[end - 3] & 0xFF);
    }

    /** The asked name in lower case (no trailing dot) or null. */
    public static String queryName(byte[] q, int len) {
        int end = questionEnd(q, len);
        if (end < 0) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        int pos = 12;
        while (pos < end - 4) {
            int l = q[pos] & 0xFF;
            if (l == 0) {
                break;
            }
            if (sb.length() > 0) {
                sb.append('.');
            }
            for (int i = 1; i <= l; i++) {
                sb.append((char) (q[pos + i] & 0xFF));
            }
            pos += l + 1;
        }
        return sb.toString().toLowerCase(java.util.Locale.ROOT);
    }

    /** A "no such record" answer (NOERROR, zero answers) for the given query, or null if unparseable. */
    public static byte[] emptyAnswer(byte[] q, int len) {
        int end = questionEnd(q, len);
        if (end < 0) {
            return null;
        }
        byte[] r = Arrays.copyOf(q, end);
        r[2] = (byte) (0x80 | (q[2] & 0x79)); // QR=1, keep opcode and RD
        r[3] = (byte) 0x80;                   // RA=1, RCODE=0
        for (int i = 6; i < 12; i++) {
            r[i] = 0;                         // no answers/authority/additional (drops EDNS too)
        }
        return r;
    }

    /** All IPv4 addresses (as ints) in the answer section of a response. */
    public static List<Integer> answerIpv4(byte[] resp, int len) {
        List<Integer> out = new ArrayList<>();
        try {
            if (resp == null || len < 12) {
                return out;
            }
            int qd = ((resp[4] & 0xFF) << 8) | (resp[5] & 0xFF);
            int an = ((resp[6] & 0xFF) << 8) | (resp[7] & 0xFF);
            int pos = 12;
            for (int i = 0; i < qd; i++) {
                pos = skipName(resp, pos, len) + 4;
            }
            for (int i = 0; i < an && pos < len; i++) {
                pos = skipName(resp, pos, len);
                if (pos + 10 > len) {
                    break;
                }
                int type = ((resp[pos] & 0xFF) << 8) | (resp[pos + 1] & 0xFF);
                int rdlen = ((resp[pos + 8] & 0xFF) << 8) | (resp[pos + 9] & 0xFF);
                pos += 10;
                if (pos + rdlen > len) {
                    break;
                }
                if (type == TYPE_A && rdlen == 4) {
                    out.add(((resp[pos] & 0xFF) << 24) | ((resp[pos + 1] & 0xFF) << 16)
                            | ((resp[pos + 2] & 0xFF) << 8) | (resp[pos + 3] & 0xFF));
                }
                pos += rdlen;
            }
        } catch (RuntimeException ignored) {
            // malformed response: return whatever was parsed
        }
        return out;
    }

    private static int skipName(byte[] b, int pos, int len) {
        while (pos < len) {
            int l = b[pos] & 0xFF;
            if (l == 0) {
                return pos + 1;
            }
            if ((l & 0xC0) == 0xC0) {
                return pos + 2;
            }
            pos += l + 1;
        }
        return len;
    }
}
