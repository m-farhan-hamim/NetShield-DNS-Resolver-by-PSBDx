package com.example.dns;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class DoHClient {
    private static final int TIMEOUT_MS = 5000;

    public static byte[] query(String dohUrl, byte[] queryPacket, int length) {
        HttpURLConnection conn = null;
        boolean ok = false;
        try {
            URL url = new URL(dohUrl);
            conn = UpstreamNet.openHttps(url);
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setDoOutput(true);
            conn.setDoInput(true);
            conn.setUseCaches(false);

            conn.setRequestProperty("Content-Type", "application/dns-message");
            conn.setRequestProperty("Accept", "application/dns-message");
            conn.setRequestProperty("User-Agent", "LocalDNS-Resolver/1.0");

            OutputStream os = conn.getOutputStream();
            os.write(queryPacket, 0, length);
            os.flush();
            os.close();

            int responseCode = conn.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                InputStream is = conn.getInputStream();
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buffer = new byte[1024];
                int n;
                while ((n = is.read(buffer)) != -1) {
                    baos.write(buffer, 0, n);
                }
                is.close();
                ok = true;
                return baos.toByteArray();
            }
        } catch (Exception ignored) {
        } finally {
            // On success the body was fully read, so keep the connection alive for reuse
            // (avoids a fresh TCP+TLS handshake per lookup). Only tear down failed ones.
            if (conn != null && !ok) {
                try {
                    conn.disconnect();
                } catch (Exception ignored) {
                }
            }
        }
        return null;
    }
}
