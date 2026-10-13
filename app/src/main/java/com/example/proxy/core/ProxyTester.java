package com.example.proxy.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;

/**
 * End-to-end self-test of a proxy: connects through it and fetches a tiny page.
 * A bare SOCKS "success" is not enough: some proxies answer before they have reached anything.
 */
public final class ProxyTester {
    public enum Result {
        OK,
        PROXY_NOT_RUNNING,
        PROXY_TIMEOUT,
        NOT_A_PROXY,
        LOGIN_REQUIRED,
        LOGIN_FAILED,
        NO_INTERNET_THROUGH_PROXY
    }

    private static final String[] PROBE_HOSTS = {"cp.cloudflare.com", "connectivitycheck.gstatic.com"};

    private ProxyTester() {
    }

    /** Blocking; call off the main thread. */
    public static Result test(ProxyEndpoint proxy, int timeoutMs) {
        Result last = Result.NO_INTERNET_THROUGH_PROXY;
        for (String host : PROBE_HOSTS) {
            try (Socket s = Socks5Client.connect(proxy, host, 80, timeoutMs)) {
                s.setSoTimeout(timeoutMs);
                OutputStream out = s.getOutputStream();
                String request = "GET /generate_204 HTTP/1.1\r\nHost: " + host
                        + "\r\nConnection: close\r\nUser-Agent: NetShield\r\n\r\n";
                out.write(request.getBytes(StandardCharsets.US_ASCII));
                out.flush();
                InputStream in = s.getInputStream();
                byte[] head = new byte[8];
                int n = 0;
                while (n < head.length) {
                    int r = in.read(head, n, head.length - n);
                    if (r < 0) {
                        break;
                    }
                    n += r;
                }
                if (n >= 5 && new String(head, 0, 5, StandardCharsets.US_ASCII).equals("HTTP/")) {
                    return Result.OK;
                }
                last = Result.NO_INTERNET_THROUGH_PROXY;
            } catch (Socks5Exception e) {
                switch (e.kind) {
                    case PROXY_UNREACHABLE:
                        return Result.PROXY_NOT_RUNNING;
                    case TIMEOUT:
                        return Result.PROXY_TIMEOUT;
                    case NOT_SOCKS5:
                        return Result.NOT_A_PROXY;
                    case LOGIN_REQUIRED:
                        return Result.LOGIN_REQUIRED;
                    case LOGIN_FAILED:
                        return Result.LOGIN_FAILED;
                    default:
                        last = Result.NO_INTERNET_THROUGH_PROXY;
                }
            } catch (SocketTimeoutException e) {
                last = Result.NO_INTERNET_THROUGH_PROXY;
            } catch (IOException e) {
                last = Result.NO_INTERNET_THROUGH_PROXY;
            }
        }
        return last;
    }
}
