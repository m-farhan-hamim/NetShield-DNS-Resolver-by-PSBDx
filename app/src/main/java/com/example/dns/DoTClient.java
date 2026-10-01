package com.example.dns;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

public class DoTClient {
    private static final int TIMEOUT_MS = 5000;

    public static byte[] query(String host, int port, byte[] queryPacket, int length) {
        Socket socket = null;
        try {
            SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
            Socket plain = new Socket();
            plain.connect(new InetSocketAddress(host, port), TIMEOUT_MS);
            plain.setSoTimeout(TIMEOUT_MS);
            // Layer TLS over the connected socket so SNI is sent for `host`.
            SSLSocket ssl = (SSLSocket) factory.createSocket(plain, host, port, true);
            socket = ssl;
            ssl.startHandshake();
            // Chain validation alone does not bind the cert to the server name: verify it.
            if (!HttpsURLConnection.getDefaultHostnameVerifier().verify(host, ssl.getSession())) {
                return null;
            }

            DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
            // Write 2-byte length prefix (RFC 7858)
            dos.writeShort(length);
            dos.write(queryPacket, 0, length);
            dos.flush();

            DataInputStream dis = new DataInputStream(socket.getInputStream());
            int responseLength = dis.readUnsignedShort();
            if (responseLength > 0 && responseLength < 65535) {
                byte[] response = new byte[responseLength];
                dis.readFully(response);
                return response;
            }
        } catch (Exception ignored) {
        } finally {
            if (socket != null) {
                try {
                    socket.close();
                } catch (Exception ignored) {
                }
            }
        }
        return null;
    }
}
