package com.example.dns;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.Socket;

/**
 * Plain DNS over TCP (RFC 1035 / 7766). Used instead of UDP while a proxy is in use, because a
 * SOCKS5 proxy carries TCP: this lets even "plain" lookups travel through the proxy.
 */
public class DnsTcpClient {
    private static final int TIMEOUT_MS = 6000;

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

    private static byte[] tryQuery(String server, byte[] queryPacket, int length) {
        Socket socket = null;
        try {
            socket = UpstreamNet.connect(server, 53, TIMEOUT_MS);
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            out.writeShort(length);
            out.write(queryPacket, 0, length);
            out.flush();
            DataInputStream in = new DataInputStream(socket.getInputStream());
            int responseLength = in.readUnsignedShort();
            if (responseLength > 0) {
                byte[] response = new byte[responseLength];
                in.readFully(response);
                return response;
            }
        } catch (Exception ignored) {
            // fall through to null
        } finally {
            if (socket != null) {
                try {
                    socket.close();
                } catch (Exception ignored) {
                    // nothing to do
                }
            }
        }
        return null;
    }
}
