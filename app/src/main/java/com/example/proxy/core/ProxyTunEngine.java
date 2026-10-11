package com.example.proxy.core;

import com.example.dns.IpPacketUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.security.SecureRandom;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Sends everything that comes out of the VPN tunnel through a local SOCKS5 proxy.
 *
 * Packets read from the tunnel are fed to {@link #onPacket}. The engine plays the remote server
 * towards the app's own TCP stack (a small user-space TCP): it completes the handshake, relays the
 * byte stream through a SOCKS5 connection to the proxy, and sends the proxy's data back as TCP
 * segments written to the {@link PacketSink}. DNS (UDP port 53) is answered by the {@link DnsHook}.
 * Other UDP, ICMP and IPv6 are dropped on purpose (apps fall back to TCP/IPv4).
 *
 * Pure Java, no Android classes, so it can be tested on a normal JVM against a real TCP stack.
 */
public final class ProxyTunEngine {

    public interface PacketSink {
        /** Must be thread-safe; one call = one whole IP packet. */
        void write(byte[] packet, int offset, int length) throws IOException;
    }

    public interface DnsHook {
        /** @return the DNS response, or null to send no answer. */
        byte[] resolve(byte[] query, int length, String clientIp, int sourcePort, String destIp, int destPort);
    }

    public interface Listener {
        /** Called once when several connection attempts in a row fail because the proxy itself is not working. */
        void onProxyDown(Socks5Exception.Kind kind);

        /** Called when connections work again after {@link #onProxyDown}. */
        void onProxyUp();
    }

    private static final int FIN = 0x01;
    private static final int SYN = 0x02;
    private static final int RST = 0x04;
    private static final int PSH = 0x08;
    private static final int ACK = 0x10;

    private static final int CONNECTING = 0;
    private static final int SYNACK_SENT = 1;
    private static final int ESTABLISHED = 2;
    private static final int CLOSED = 3;

    private static final int RCV_WINDOW = 65535;   // no window scaling is negotiated
    private static final int OUR_MSS = 1460;
    private static final int MAX_SESSIONS = 400;
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final long IDLE_TIMEOUT_MS = 30L * 60_000L;
    private static final long CLOSE_LINGER_MS = 120_000L;
    private static final long FIN_WAIT_LINGER_MS = 60_000L;
    private static final long RTO_INITIAL_MS = 400L;
    private static final long RTO_MAX_MS = 8_000L;
    private static final int DOWN_AFTER_FAILURES = 3;

    private static final Object FIN_MARKER = new Object();
    private static final Object CLOSE_MARKER = new Object();

    private final ProxyEndpoint proxy;
    private final PacketSink sink;
    private final DnsHook dns;
    private final Listener listener;
    private final boolean dropIpv6Answers;

    private final DnsNameCache names = new DnsNameCache();
    private final Map<SessionKey, TcpSession> sessions = new ConcurrentHashMap<>();
    private final ExecutorService workers;
    private final ExecutorService dnsPool;
    private final ScheduledExecutorService timer;
    private final SecureRandom random = new SecureRandom();
    private final AtomicInteger ipId = new AtomicInteger(1);
    private volatile boolean closed;

    private final AtomicLong totalConnections = new AtomicLong();
    private final AtomicLong failedConnections = new AtomicLong();
    private final AtomicLong bytesUp = new AtomicLong();
    private final AtomicLong bytesDown = new AtomicLong();
    private final AtomicInteger failStreak = new AtomicInteger();
    private volatile boolean proxyDown;

    /**
     * @param dropIpv6Answers answer AAAA queries with "no such record" so apps stay on IPv4
     *                        (IPv6 is not carried by this tunnel)
     */
    public ProxyTunEngine(ProxyEndpoint proxy, PacketSink sink, DnsHook dns, Listener listener,
                          boolean dropIpv6Answers) {
        this.proxy = proxy;
        this.sink = sink;
        this.dns = dns;
        this.listener = listener;
        this.dropIpv6Answers = dropIpv6Answers;
        this.workers = new ThreadPoolExecutor(0, 1500, 30, TimeUnit.SECONDS,
                new SynchronousQueue<Runnable>(), daemonFactory("proxy-worker"));
        this.dnsPool = new ThreadPoolExecutor(2, 8, 30, TimeUnit.SECONDS,
                new ArrayBlockingQueue<Runnable>(256), daemonFactory("proxy-dns"),
                new ThreadPoolExecutor.DiscardPolicy());
        this.timer = Executors.newSingleThreadScheduledExecutor(daemonFactory("proxy-timer"));
        this.timer.scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                tickAll();
            }
        }, 200, 200, TimeUnit.MILLISECONDS);
    }

    public int activeSessions() {
        return sessions.size();
    }

    public long totalConnections() {
        return totalConnections.get();
    }

    public long failedConnections() {
        return failedConnections.get();
    }

    public long bytesUp() {
        return bytesUp.get();
    }

    public long bytesDown() {
        return bytesDown.get();
    }

    public void close() {
        closed = true;
        timer.shutdownNow();
        dnsPool.shutdownNow();
        workers.shutdownNow();
        for (TcpSession s : sessions.values()) {
            synchronized (s) {
                s.closeNow(false);
            }
        }
        sessions.clear();
    }

    // ------------------------------------------------------------------ packets in

    /** Feed one IP packet read from the tunnel. The buffer may be reused after this returns. */
    public void onPacket(byte[] p, int len) {
        if (closed || len < 20) {
            return;
        }
        if (((p[0] >> 4) & 0xF) != 4) {
            return; // IPv6 is captured by the tunnel and dropped on purpose
        }
        int ihl = (p[0] & 0xF) * 4;
        int totalLen = u16(p, 2);
        if (ihl < 20 || totalLen < ihl || totalLen > len) {
            return;
        }
        if ((u16(p, 6) & 0x3FFF) != 0) {
            return; // fragments are not supported
        }
        int proto = p[9] & 0xFF;
        if (proto == 6) {
            onTcp(p, totalLen, ihl);
        } else if (proto == 17) {
            onUdp(p, totalLen);
        }
    }

    private void onTcp(byte[] p, int len, int ihl) {
        int t = ihl;
        if (len < t + 20) {
            return;
        }
        int srcIp = u32(p, 12);
        int dstIp = u32(p, 16);
        int srcPort = u16(p, t);
        int dstPort = u16(p, t + 2);
        int seq = u32(p, t + 4);
        int ack = u32(p, t + 8);
        int dataOff = ((p[t + 12] >> 4) & 0xF) * 4;
        int flags = p[t + 13] & 0x3F;
        int window = u16(p, t + 14);
        if (dataOff < 20 || t + dataOff > len) {
            return;
        }
        int payOff = t + dataOff;
        int payLen = len - payOff;
        SessionKey key = new SessionKey(srcIp, srcPort, dstIp, dstPort);
        TcpSession s = sessions.get(key);

        if ((flags & SYN) != 0 && (flags & ACK) == 0) {
            if (s != null) {
                s.onSegment(flags, seq, ack, window, p, payOff, 0);
                return;
            }
            if (sessions.size() >= MAX_SESSIONS || dstIp == 0 || dstIp == -1
                    || ((dstIp >>> 28) == 0xE)) {
                // too many connections, blocked (0.0.0.0) or multicast/broadcast: refuse right away
                emit(buildTcp(dstIp, srcIp, dstPort, srcPort, 0, seq + 1, RST | ACK, 0, null, 0, 0, 0));
                return;
            }
            TcpSession created = new TcpSession(key, srcIp, srcPort, dstIp, dstPort, seq,
                    parseMss(p, t + 20, t + dataOff), window);
            sessions.put(key, created);
            totalConnections.incrementAndGet();
            try {
                workers.execute(created);
            } catch (RejectedExecutionException e) {
                sessions.remove(key);
            }
            return;
        }
        if (s == null) {
            sendRstForUnknown(srcIp, dstIp, srcPort, dstPort, flags, seq, ack, payLen);
            return;
        }
        s.onSegment(flags, seq, ack, window, p, payOff, payLen);
    }

    private void sendRstForUnknown(int srcIp, int dstIp, int srcPort, int dstPort,
                                   int flags, int seq, int ack, int payLen) {
        if ((flags & RST) != 0) {
            return;
        }
        if ((flags & ACK) != 0) {
            emit(buildTcp(dstIp, srcIp, dstPort, srcPort, ack, 0, RST, 0, null, 0, 0, 0));
        } else {
            int a = seq + payLen + ((flags & SYN) != 0 ? 1 : 0) + ((flags & FIN) != 0 ? 1 : 0);
            emit(buildTcp(dstIp, srcIp, dstPort, srcPort, 0, a, RST | ACK, 0, null, 0, 0, 0));
        }
    }

    private void onUdp(byte[] p, int len) {
        final IpPacketUtils.UdpPacketInfo info = IpPacketUtils.parseUdpPacket(p, len);
        if (info == null || info.destPort != 53 || dns == null) {
            return; // all other UDP is dropped (apps fall back to TCP)
        }
        final byte[] query = Arrays.copyOfRange(p, info.payloadOffset, info.payloadOffset + info.payloadLength);
        try {
            dnsPool.execute(new Runnable() {
                @Override
                public void run() {
                    handleDns(info, query);
                }
            });
        } catch (RejectedExecutionException ignored) {
            // shutting down
        }
    }

    private void handleDns(IpPacketUtils.UdpPacketInfo info, byte[] query) {
        try {
            int type = DnsTools.queryType(query, query.length);
            byte[] response;
            if (type == DnsTools.TYPE_AAAA && dropIpv6Answers) {
                response = DnsTools.emptyAnswer(query, query.length);
            } else {
                response = dns.resolve(query, query.length, ipString(ipOf(info.sourceIp)),
                        info.sourcePort, ipString(ipOf(info.destIp)), info.destPort);
                if (response != null && type == DnsTools.TYPE_A) {
                    names.remember(DnsTools.queryName(query, query.length),
                            DnsTools.answerIpv4(response, response.length));
                }
            }
            if (response != null && !closed) {
                emit(IpPacketUtils.buildUdpResponsePacket(info, response));
            }
        } catch (RuntimeException ignored) {
            // a bad packet must never take the tunnel down
        }
    }

    // ------------------------------------------------------------------ health

    private void noteFailure(Socks5Exception.Kind kind) {
        failedConnections.incrementAndGet();
        if (kind == Socks5Exception.Kind.PROXY_REFUSED_TARGET) {
            return; // the proxy works; this one destination does not
        }
        if (failStreak.incrementAndGet() >= DOWN_AFTER_FAILURES && !proxyDown) {
            proxyDown = true;
            if (listener != null) {
                listener.onProxyDown(kind);
            }
        }
    }

    private void noteSuccess() {
        failStreak.set(0);
        if (proxyDown) {
            proxyDown = false;
            if (listener != null) {
                listener.onProxyUp();
            }
        }
    }

    private void tickAll() {
        long now = now();
        for (TcpSession s : sessions.values()) {
            try {
                s.tick(now);
            } catch (RuntimeException ignored) {
                // keep the timer alive
            }
        }
    }

    // ------------------------------------------------------------------ sessions

    private static final class SessionKey {
        final int srcIp, srcPort, dstIp, dstPort;

        SessionKey(int srcIp, int srcPort, int dstIp, int dstPort) {
            this.srcIp = srcIp;
            this.srcPort = srcPort;
            this.dstIp = dstIp;
            this.dstPort = dstPort;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof SessionKey)) {
                return false;
            }
            SessionKey k = (SessionKey) o;
            return srcIp == k.srcIp && srcPort == k.srcPort && dstIp == k.dstIp && dstPort == k.dstPort;
        }

        @Override
        public int hashCode() {
            return ((srcIp * 31 + srcPort) * 31 + dstIp) * 31 + dstPort;
        }
    }

    /** One segment we sent and have not seen acknowledged yet. */
    private static final class Seg {
        final int seq;
        final byte[] data;
        final boolean fin;
        long sentAt;
        int retries;

        Seg(int seq, byte[] data, boolean fin) {
            this.seq = seq;
            this.data = data;
            this.fin = fin;
        }
    }

    private final class TcpSession implements Runnable {
        final SessionKey key;
        final int cIp, cPort, sIp, sPort; // the app (client) side and the server it wants
        final int ourIsn;
        final int clientMss;
        int state = CONNECTING;
        int rcvNxt;
        int sndUna;
        int sndNxt;
        int clientWindow;
        Socket sock;
        OutputStream sockOut;
        final LinkedBlockingQueue<Object> writeQueue = new LinkedBlockingQueue<>();
        final AtomicInteger queuedBytes = new AtomicInteger();
        final ArrayDeque<Seg> retx = new ArrayDeque<>();
        boolean clientFinSeen;
        boolean ourFinSent;
        boolean ourFinAcked;
        int finSeq;
        long lastActivity = now();
        long closeDeadline;
        long rto = RTO_INITIAL_MS;
        long synAckAt;
        int synAckTries;
        int lastAdvertisedWindow = RCV_WINDOW;

        TcpSession(SessionKey key, int cIp, int cPort, int sIp, int sPort, int clientIsn, int mss, int window) {
            this.key = key;
            this.cIp = cIp;
            this.cPort = cPort;
            this.sIp = sIp;
            this.sPort = sPort;
            this.ourIsn = random.nextInt();
            this.rcvNxt = clientIsn + 1;
            this.clientMss = Math.max(88, Math.min(mss, OUR_MSS));
            this.clientWindow = window;
        }

        /** Runs on a worker thread: connect through the proxy, then relay proxy -> app. */
        @Override
        public void run() {
            String host = names.lookup(sIp);
            if (host == null) {
                host = ipString(sIp);
            }
            Socket s;
            try {
                s = Socks5Client.connect(proxy, host, sPort, CONNECT_TIMEOUT_MS);
            } catch (Socks5Exception e) {
                connectFailed(e.kind);
                return;
            } catch (IOException e) {
                connectFailed(Socks5Exception.Kind.PROTOCOL);
                return;
            }
            noteSuccess();
            synchronized (this) {
                if (state == CLOSED) {
                    closeQuietly(s);
                    return;
                }
                sock = s;
                try {
                    sockOut = s.getOutputStream();
                } catch (IOException e) {
                    closeNow(true);
                    return;
                }
                state = SYNACK_SENT;
                sndUna = ourIsn;
                sndNxt = ourIsn + 1;
                sendSynAck();
            }
            try {
                workers.execute(new Runnable() {
                    @Override
                    public void run() {
                        writerLoop();
                    }
                });
            } catch (RejectedExecutionException e) {
                synchronized (this) {
                    closeNow(false);
                }
                return;
            }
            readerLoop();
        }

        private void connectFailed(Socks5Exception.Kind kind) {
            noteFailure(kind);
            synchronized (this) {
                if (state != CLOSED) {
                    state = CLOSED;
                    sessions.remove(key);
                    // refuse the app's connection right away instead of letting it hang
                    emit(buildTcp(sIp, cIp, sPort, cPort, 0, rcvNxt, RST | ACK, 0, null, 0, 0, 0));
                }
            }
        }

        /** proxy -> app: read from the proxy, send as TCP segments within the app's window. */
        private void readerLoop() {
            byte[] buf = new byte[OUR_MSS];
            try {
                InputStream in = sock.getInputStream();
                synchronized (this) {
                    while (state == SYNACK_SENT) {
                        wait(100);
                    }
                    if (state == CLOSED) {
                        return;
                    }
                }
                while (true) {
                    int allowed;
                    synchronized (this) {
                        while (true) {
                            if (state == CLOSED) {
                                return;
                            }
                            int inflight = sndNxt - sndUna;
                            allowed = Math.min(clientMss, clientWindow - inflight);
                            if (allowed > 0) {
                                break;
                            }
                            wait(100);
                        }
                    }
                    int n = in.read(buf, 0, Math.min(allowed, buf.length));
                    if (n < 0) {
                        synchronized (this) {
                            sendFin();
                        }
                        return;
                    }
                    if (n == 0) {
                        continue;
                    }
                    synchronized (this) {
                        if (state == CLOSED) {
                            return;
                        }
                        sendData(buf, n);
                    }
                }
            } catch (IOException e) {
                synchronized (this) {
                    closeNow(true);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        /** app -> proxy: drain what the app sent to the proxy socket. */
        private void writerLoop() {
            try {
                while (true) {
                    Object o = writeQueue.take();
                    if (o == CLOSE_MARKER) {
                        return;
                    }
                    if (o == FIN_MARKER) {
                        try {
                            sock.shutdownOutput();
                        } catch (IOException ignored) {
                            // already closed
                        }
                        return;
                    }
                    byte[] b = (byte[]) o;
                    sockOut.write(b);
                    sockOut.flush();
                    synchronized (this) {
                        queuedBytes.addAndGet(-b.length);
                        if (state != CLOSED && lastAdvertisedWindow < 4 * OUR_MSS) {
                            sendAck(); // the app was told the window was nearly full: reopen it
                        }
                    }
                }
            } catch (IOException e) {
                synchronized (this) {
                    closeNow(true);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        synchronized void onDuplicateSyn() {
            if (state == SYNACK_SENT) {
                sendSynAck();
            }
        }

        synchronized void onSegment(int flags, int seq, int ack, int window, byte[] p, int payOff, int payLen) {
            if (state == CLOSED) {
                return;
            }
            lastActivity = now();
            if ((flags & RST) != 0) {
                closeNow(false);
                return;
            }
            if (state == CONNECTING) {
                return;
            }
            if ((flags & SYN) != 0) {
                if (state == SYNACK_SENT) {
                    sendSynAck();
                }
                return;
            }
            if ((flags & ACK) != 0) {
                if (state == SYNACK_SENT) {
                    if (ack != ourIsn + 1) {
                        return;
                    }
                    state = ESTABLISHED;
                    sndUna = ack;
                    notifyAll();
                }
                processAck(ack, window);
                if (state == CLOSED) {
                    return;
                }
            } else if (state == SYNACK_SENT) {
                return;
            }
            if (payLen > 0 || (flags & FIN) != 0) {
                processIncoming(flags, seq, p, payOff, payLen);
            }
        }

        private void processAck(int ack, int window) {
            clientWindow = window;
            if (lt(sndUna, ack) && le(ack, sndNxt)) {
                sndUna = ack;
                rto = RTO_INITIAL_MS;
                while (!retx.isEmpty()) {
                    Seg g = retx.peekFirst();
                    int end = g.seq + g.data.length + (g.fin ? 1 : 0);
                    if (le(end, ack)) {
                        retx.pollFirst();
                    } else {
                        break;
                    }
                }
                if (ourFinSent && !ourFinAcked && le(finSeq + 1, ack)) {
                    ourFinAcked = true;
                    if (!clientFinSeen) {
                        closeDeadline = now() + FIN_WAIT_LINGER_MS;
                    }
                }
            }
            notifyAll();
            if (clientFinSeen && ourFinAcked) {
                closeNow(false);
            }
        }

        private void processIncoming(int flags, int seq, byte[] p, int payOff, int payLen) {
            boolean fin = (flags & FIN) != 0;
            int skip = rcvNxt - seq;
            if (skip < 0) {
                sendAck(); // a gap: ask again for the missing bytes
                return;
            }
            if (skip > payLen || (skip == payLen && !fin)) {
                sendAck(); // everything in it was already received
                return;
            }
            int newLen = payLen - skip;
            if (newLen > 0) {
                int space = RCV_WINDOW - queuedBytes.get();
                if (newLen > space) {
                    newLen = Math.max(0, space);
                    fin = false;
                }
                if (newLen > 0) {
                    byte[] chunk = Arrays.copyOfRange(p, payOff + skip, payOff + skip + newLen);
                    queuedBytes.addAndGet(newLen);
                    writeQueue.add(chunk);
                    rcvNxt += newLen;
                    bytesUp.addAndGet(newLen);
                }
            }
            if (fin && seq + payLen == rcvNxt && !clientFinSeen) {
                clientFinSeen = true;
                rcvNxt++;
                writeQueue.add(FIN_MARKER);
                closeDeadline = now() + CLOSE_LINGER_MS;
            }
            sendAck();
            if (clientFinSeen && ourFinAcked) {
                closeNow(false);
            }
        }

        synchronized void tick(long now) {
            if (state == CLOSED) {
                return;
            }
            if (state == SYNACK_SENT) {
                if (now - synAckAt >= 1000L * synAckTries) {
                    if (synAckTries >= 5) {
                        closeNow(true);
                    } else {
                        sendSynAck();
                    }
                }
                return;
            }
            if (state == CONNECTING) {
                return;
            }
            if (now - lastActivity > IDLE_TIMEOUT_MS) {
                closeNow(true);
                return;
            }
            if (closeDeadline != 0 && now > closeDeadline) {
                closeNow(false);
                return;
            }
            Seg g = retx.peekFirst();
            if (g != null && now - g.sentAt >= rto) {
                if (++g.retries > 10) {
                    closeNow(true);
                    return;
                }
                g.sentAt = now;
                rto = Math.min(rto * 2, RTO_MAX_MS);
                transmit(g);
            }
        }

        // -- everything below must be called with the session lock held

        void sendSynAck() {
            synAckAt = now();
            synAckTries++;
            send(this, SYN | ACK, ourIsn, rcvNxt, null, 0, 0, true);
        }

        void sendAck() {
            send(this, ACK, sndNxt, rcvNxt, null, 0, 0, false);
        }

        void sendData(byte[] buf, int len) {
            Seg g = new Seg(sndNxt, Arrays.copyOf(buf, len), false);
            g.sentAt = now();
            retx.addLast(g);
            sndNxt += len;
            bytesDown.addAndGet(len);
            transmit(g);
        }

        void sendFin() {
            if (ourFinSent || state == CLOSED) {
                return;
            }
            finSeq = sndNxt;
            Seg g = new Seg(finSeq, new byte[0], true);
            g.sentAt = now();
            retx.addLast(g);
            sndNxt++;
            ourFinSent = true;
            transmit(g);
        }

        void transmit(Seg g) {
            send(this, g.fin ? (FIN | ACK) : (PSH | ACK), g.seq, rcvNxt, g.data, 0, g.data.length, false);
        }

        int advertisedWindow() {
            int w = Math.max(0, RCV_WINDOW - queuedBytes.get());
            lastAdvertisedWindow = w;
            return w;
        }

        void closeNow(boolean sendRst) {
            if (state == CLOSED) {
                return;
            }
            int previous = state;
            state = CLOSED;
            sessions.remove(key);
            writeQueue.clear();
            writeQueue.add(CLOSE_MARKER);
            Socket s = sock;
            if (s != null) {
                closeQuietly(s);
            }
            notifyAll();
            if (sendRst && previous != CONNECTING) {
                send(this, RST | ACK, sndNxt, rcvNxt, null, 0, 0, false);
            }
        }
    }

    // ------------------------------------------------------------------ packet building

    private void send(TcpSession s, int flags, int seq, int ack, byte[] data, int off, int len, boolean mssOption) {
        emit(buildTcp(s.sIp, s.cIp, s.sPort, s.cPort, seq, ack, flags, s.advertisedWindow(),
                data, off, len, mssOption ? OUR_MSS : 0));
    }

    private void emit(byte[] packet) {
        try {
            sink.write(packet, 0, packet.length);
        } catch (IOException ignored) {
            // the tunnel is gone; the service is shutting down
        }
    }

    private byte[] buildTcp(int srcIp, int dstIp, int srcPort, int dstPort, int seq, int ack, int flags,
                            int window, byte[] data, int off, int len, int mss) {
        int tcpHdr = 20 + (mss > 0 ? 4 : 0);
        int total = 20 + tcpHdr + len;
        byte[] b = new byte[total];
        b[0] = 0x45;
        put16(b, 2, total);
        put16(b, 4, ipId.incrementAndGet() & 0xFFFF);
        b[6] = 0x40; // don't fragment
        b[8] = 64;
        b[9] = 6;
        put32(b, 12, srcIp);
        put32(b, 16, dstIp);
        put16(b, 10, checksum(b, 0, 20, 0));
        int t = 20;
        put16(b, t, srcPort);
        put16(b, t + 2, dstPort);
        put32(b, t + 4, seq);
        put32(b, t + 8, ack);
        b[t + 12] = (byte) ((tcpHdr / 4) << 4);
        b[t + 13] = (byte) flags;
        put16(b, t + 14, window);
        if (mss > 0) {
            b[t + 20] = 2;
            b[t + 21] = 4;
            put16(b, t + 22, mss);
        }
        if (len > 0) {
            System.arraycopy(data, off, b, t + tcpHdr, len);
        }
        long pseudo = (srcIp >>> 16) + (srcIp & 0xFFFF) + (dstIp >>> 16) + (dstIp & 0xFFFF) + 6 + (tcpHdr + len);
        put16(b, t + 16, checksum(b, t, tcpHdr + len, pseudo));
        return b;
    }

    // ------------------------------------------------------------------ small helpers

    private static int checksum(byte[] b, int off, int len, long initial) {
        long sum = initial;
        int end = off + len - 1;
        int i = off;
        for (; i < end; i += 2) {
            sum += ((b[i] & 0xFF) << 8) | (b[i + 1] & 0xFF);
        }
        if (i == end) {
            sum += (b[i] & 0xFF) << 8;
        }
        while ((sum >> 16) != 0) {
            sum = (sum & 0xFFFF) + (sum >> 16);
        }
        return (int) (~sum & 0xFFFF);
    }

    private static int parseMss(byte[] p, int start, int end) {
        int i = start;
        while (i < end) {
            int kind = p[i] & 0xFF;
            if (kind == 0) {
                break;
            }
            if (kind == 1) {
                i++;
                continue;
            }
            if (i + 1 >= end) {
                break;
            }
            int l = p[i + 1] & 0xFF;
            if (l < 2) {
                break;
            }
            if (kind == 2 && l == 4 && i + 3 < end) {
                return u16(p, i + 2);
            }
            i += l;
        }
        return 536;
    }

    static boolean lt(int a, int b) {
        return (a - b) < 0;
    }

    static boolean le(int a, int b) {
        return (a - b) <= 0;
    }

    static int u16(byte[] b, int i) {
        return ((b[i] & 0xFF) << 8) | (b[i + 1] & 0xFF);
    }

    static int u32(byte[] b, int i) {
        return ((b[i] & 0xFF) << 24) | ((b[i + 1] & 0xFF) << 16) | ((b[i + 2] & 0xFF) << 8) | (b[i + 3] & 0xFF);
    }

    static void put16(byte[] b, int i, int v) {
        b[i] = (byte) (v >> 8);
        b[i + 1] = (byte) v;
    }

    static void put32(byte[] b, int i, int v) {
        b[i] = (byte) (v >> 24);
        b[i + 1] = (byte) (v >> 16);
        b[i + 2] = (byte) (v >> 8);
        b[i + 3] = (byte) v;
    }

    private static int ipOf(byte[] a) {
        return u32(a, 0);
    }

    static String ipString(int ip) {
        return ((ip >>> 24) & 0xFF) + "." + ((ip >>> 16) & 0xFF) + "." + ((ip >>> 8) & 0xFF) + "." + (ip & 0xFF);
    }

    private static long now() {
        return System.nanoTime() / 1_000_000L;
    }

    private static void closeQuietly(Socket s) {
        try {
            s.close();
        } catch (IOException ignored) {
            // nothing to do
        }
    }

    private static ThreadFactory daemonFactory(final String name) {
        final AtomicInteger n = new AtomicInteger();
        return new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(null, r, name + "-" + n.incrementAndGet(), 256 * 1024);
                t.setDaemon(true);
                return t;
            }
        };
    }
}
