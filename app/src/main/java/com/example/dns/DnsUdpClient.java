package com.example.dns;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class DnsUdpClient {
    private static final int TIMEOUT_MS = 3000;

    public static byte[] query(String primaryDns, String secondaryDns, byte[] queryPacket, int length) {
        byte[] result = tryQuery(primaryDns, queryPacket, length);
        if (result != null) {
            return result;
        }
        if (secondaryDns != null && !secondaryDns.isEmpty() && !secondaryDns.equals(primaryDns)) {
            return tryQuery(secondaryDns, queryPacket, length);
        }
        return null;
    }

    private static byte[] tryQuery(String serverIp, byte[] queryPacket, int length) {
        DatagramSocket socket = null;
        try {
            socket = new DatagramSocket();
            socket.setSoTimeout(TIMEOUT_MS);
            InetAddress address = InetAddress.getByName(serverIp);
            DatagramPacket sendPacket = new DatagramPacket(queryPacket, length, address, 53);
            socket.send(sendPacket);

            byte[] buffer = new byte[1500];
            DatagramPacket receivePacket = new DatagramPacket(buffer, buffer.length);
            socket.receive(receivePacket);

            byte[] response = new byte[receivePacket.getLength()];
            System.arraycopy(receivePacket.getData(), 0, response, 0, receivePacket.getLength());
            return response;
        } catch (Exception ignored) {
        } finally {
            if (socket != null) {
                socket.close();
            }
        }
        return null;
    }
}
