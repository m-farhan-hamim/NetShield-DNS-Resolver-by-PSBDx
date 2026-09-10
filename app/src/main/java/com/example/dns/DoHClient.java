package com.example.dns;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import javax.net.ssl.HttpsURLConnection;

public class DoHClient {
    private static final int TIMEOUT_MS = 5000;

    public static byte[] query(String dohUrl, byte[] queryPacket, int length) {
        HttpsURLConnection conn = null;
        try {
            URL url = new URL(dohUrl);
            conn = (HttpsURLConnection) url.openConnection();
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
                return baos.toByteArray();
            }
        } catch (Exception ignored) {
        } finally {
            if (conn != null) {
                try {
                    conn.disconnect();
                } catch (Exception ignored) {
                }
            }
        }
        return null;
    }
}
