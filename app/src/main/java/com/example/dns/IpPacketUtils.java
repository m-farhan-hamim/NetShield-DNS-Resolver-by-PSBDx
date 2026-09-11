package com.example.dns;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;

public class IpPacketUtils {
    private static final AtomicInteger ipIdSequence = new AtomicInteger(1000);

    public static class UdpPacketInfo {
        public byte[] sourceIp = new byte[4];
        public byte[] destIp = new byte[4];
        public int sourcePort;
        public int destPort;
        public int payloadOffset;
        public int payloadLength;
    }

    public static UdpPacketInfo parseUdpPacket(byte[] packet, int length) {
        if (packet == null || length < 28) {
            return null;
        }

        // Check IPv4
        int version = (packet[0] >> 4) & 0x0F;
        if (version != 4) {
            return null;
        }

        int ihl = (packet[0] & 0x0F) * 4;
        if (ihl < 20 || length < ihl + 8) {
            return null;
        }

        int protocol = packet[9] & 0xFF;
        if (protocol != 17) { // Protocol 17 = UDP
            return null;
        }

        UdpPacketInfo info = new UdpPacketInfo();
        System.arraycopy(packet, 12, info.sourceIp, 0, 4);
        System.arraycopy(packet, 16, info.destIp, 0, 4);

        int udpOffset = ihl;
        info.sourcePort = ((packet[udpOffset] & 0xFF) << 8) | (packet[udpOffset + 1] & 0xFF);
        info.destPort = ((packet[udpOffset + 2] & 0xFF) << 8) | (packet[udpOffset + 3] & 0xFF);

        int udpLen = ((packet[udpOffset + 4] & 0xFF) << 8) | (packet[udpOffset + 5] & 0xFF);
        info.payloadOffset = udpOffset + 8;
        info.payloadLength = udpLen - 8;

        if (info.payloadLength <= 0 || info.payloadOffset + info.payloadLength > length) {
            return null;
        }

        return info;
    }

    public static byte[] buildUdpResponsePacket(UdpPacketInfo requestInfo, byte[] dnsResponsePayload) {
        int ipHeaderLen = 20;
        int udpHeaderLen = 8;
        int totalLen = ipHeaderLen + udpHeaderLen + dnsResponsePayload.length;
        byte[] packet = new byte[totalLen];

        // IPv4 Header
        packet[0] = 0x45; // Version 4, IHL 5 (20 bytes)
        packet[1] = 0x00; // TOS
        packet[2] = (byte) ((totalLen >> 8) & 0xFF);
        packet[3] = (byte) (totalLen & 0xFF);

        int id = ipIdSequence.incrementAndGet() & 0xFFFF;
        packet[4] = (byte) ((id >> 8) & 0xFF);
        packet[5] = (byte) (id & 0xFF);

        packet[6] = 0x40; // Flags: Don't Fragment
        packet[7] = 0x00;
        packet[8] = 64;   // TTL
        packet[9] = 17;   // Protocol: UDP
        packet[10] = 0x00; // Checksum placeholder
        packet[11] = 0x00;

        // Source IP = original Destination IP
        System.arraycopy(requestInfo.destIp, 0, packet, 12, 4);
        // Dest IP = original Source IP
        System.arraycopy(requestInfo.sourceIp, 0, packet, 16, 4);

        // Compute IP Header Checksum
        int ipChecksum = computeChecksum(packet, 0, ipHeaderLen);
        packet[10] = (byte) ((ipChecksum >> 8) & 0xFF);
        packet[11] = (byte) (ipChecksum & 0xFF);

        // UDP Header
        int udpOffset = ipHeaderLen;
        // Source Port = original Dest Port (53)
        packet[udpOffset] = (byte) ((requestInfo.destPort >> 8) & 0xFF);
        packet[udpOffset + 1] = (byte) (requestInfo.destPort & 0xFF);
        // Dest Port = original Source Port
        packet[udpOffset + 2] = (byte) ((requestInfo.sourcePort >> 8) & 0xFF);
        packet[udpOffset + 3] = (byte) (requestInfo.sourcePort & 0xFF);

        int udpLen = udpHeaderLen + dnsResponsePayload.length;
        packet[udpOffset + 4] = (byte) ((udpLen >> 8) & 0xFF);
        packet[udpOffset + 5] = (byte) (udpLen & 0xFF);

        // Checksum: in IPv4 UDP, 0x0000 means no checksum is transmitted
        packet[udpOffset + 6] = 0x00;
        packet[udpOffset + 7] = 0x00;

        // Copy DNS Response Payload
        System.arraycopy(dnsResponsePayload, 0, packet, udpOffset + 8, dnsResponsePayload.length);

        return packet;
    }

    private static int computeChecksum(byte[] data, int offset, int length) {
        int sum = 0;
        for (int i = offset; i < offset + length - 1; i += 2) {
            int word = ((data[i] & 0xFF) << 8) | (data[i + 1] & 0xFF);
            sum += word;
        }
        if (length % 2 != 0) {
            sum += ((data[offset + length - 1] & 0xFF) << 8);
        }
        while ((sum >> 16) > 0) {
            sum = (sum & 0xFFFF) + (sum >> 16);
        }
        return (~sum) & 0xFFFF;
    }
}
