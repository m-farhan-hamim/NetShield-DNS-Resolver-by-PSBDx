#!/usr/bin/env python3
"""Linux TUN interop test for ProxyTunEngine.

A real TUN device (tun0, 10.0.0.2/24) is wired to the Java engine over a framed TCP "cable".
The kernel's own TCP stack is therefore the client talking to the engine, which relays to a fake
SOCKS5 proxy. Usage: interop_tests.py <dir with compiled classes>
"""
import concurrent.futures
import fcntl
import getpass
import os
import socket
import struct
import subprocess
import sys
import threading
import time

OUT = sys.argv[1]
TUN = "tun0"
CABLE = ("127.0.0.1", 39001)

PAT = bytes(((i * 31 + 7) & 0xFF) for i in range(256))
BIG = PAT * 600  # long enough for any slice up to 65536 bytes + 255


def expected(offset, n):
    s = offset % 256
    return BIG[s:s + n]


def sh(cmd):
    print("+", cmd, flush=True)
    subprocess.check_call(cmd, shell=True)


last_stats = {}
selftest = []
ready = threading.Event()


def start_java():
    proc = subprocess.Popen(["java", "-cp", OUT, "InteropMain"], stdout=subprocess.PIPE,
                            stderr=subprocess.STDOUT, text=True, bufsize=1)

    def reader():
        for line in proc.stdout:
            line = line.rstrip()
            if line.startswith("STATS"):
                last_stats.update(dict(kv.split("=") for kv in line.split()[1:]))
                continue
            if line == "READY":
                ready.set()
                continue
            if line.startswith("SELFTEST"):
                selftest.append(line)
            print("[java]", line, flush=True)

    threading.Thread(target=reader, daemon=True).start()
    return proc


def attach_tun():
    fd = os.open("/dev/net/tun", os.O_RDWR)
    TUNSETIFF = 0x400454CA
    IFF_TUN, IFF_NO_PI = 0x0001, 0x1000
    fcntl.ioctl(fd, TUNSETIFF, struct.pack("16sH", TUN.encode(), IFF_TUN | IFF_NO_PI))
    return fd


def pump(fd, cable):
    def tun_to_java():
        try:
            while True:
                pkt = os.read(fd, 70000)
                cable.sendall(struct.pack(">H", len(pkt)) + pkt)
        except OSError:
            pass

    def java_to_tun():
        f = cable.makefile("rb")
        try:
            while True:
                hdr = f.read(2)
                if len(hdr) < 2:
                    return
                n = struct.unpack(">H", hdr)[0]
                pkt = f.read(n)
                if len(pkt) < n:
                    return
                os.write(fd, pkt)
        except OSError:
            pass

    threading.Thread(target=tun_to_java, daemon=True).start()
    threading.Thread(target=java_to_tun, daemon=True).start()


# ---------------------------------------------------------------- helpers

def http_get(ip, port, path, timeout=90):
    s = socket.create_connection((ip, port), timeout=timeout)
    try:
        s.sendall(f"GET {path} HTTP/1.1\r\nHost: x\r\nConnection: close\r\n\r\n".encode())
        buf = b""
        while b"\r\n\r\n" not in buf:
            d = s.recv(65536)
            if not d:
                raise AssertionError("connection closed before the HTTP headers arrived")
            buf += d
        head, data = buf.split(b"\r\n\r\n", 1)
        hdrs = {}
        for line in head.decode().split("\r\n")[1:]:
            k, v = line.split(":", 1)
            hdrs[k.strip().lower()] = v.strip()
        length = int(hdrs["content-length"])
        verify = path.startswith("/size/")
        got, chunks = 0, []
        while True:
            if data:
                if verify:
                    assert data == expected(got, len(data)), f"data mismatch at offset {got}"
                else:
                    chunks.append(data)
                got += len(data)
            data = s.recv(65536)
            if not data:
                break
        assert got == length, f"body is {got} bytes, expected {length}"
        return hdrs, b"".join(chunks)
    finally:
        s.close()


def dns_query(name, qtype, server="10.0.0.1", timeout=5):
    q = struct.pack(">HHHHHH", 0x1234, 0x0100, 1, 0, 0, 0)
    q += b"".join(bytes([len(label)]) + label.encode() for label in name.split(".")) + b"\x00"
    q += struct.pack(">HH", qtype, 1)
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    s.settimeout(timeout)
    try:
        s.sendto(q, (server, 53))
        data, _ = s.recvfrom(4096)
        return data
    finally:
        s.close()


def a_records(resp):
    qd, an = struct.unpack(">HH", resp[4:8])
    pos = 12
    for _ in range(qd):
        while resp[pos] != 0:
            pos += resp[pos] + 1
        pos += 5
    ips = []
    for _ in range(an):
        if resp[pos] & 0xC0 == 0xC0:
            pos += 2
        else:
            while resp[pos] != 0:
                pos += resp[pos] + 1
            pos += 1
        rtype, _cls, _ttl, rdlen = struct.unpack(">HHIH", resp[pos:pos + 10])
        pos += 10
        if rtype == 1 and rdlen == 4:
            ips.append(socket.inet_ntoa(resp[pos:pos + 4]))
        pos += rdlen
    return ips


# ---------------------------------------------------------------- tests

def t_selftest():
    assert selftest and selftest[0] == "SELFTEST OK", selftest


def t_small_http():
    hdrs, _ = http_get("10.0.0.5", 8080, "/size/1000")
    assert hdrs["content-length"] == "1000"


def t_big_download():
    n = 50_000_000
    t0 = time.time()
    http_get("10.0.0.5", 8080, f"/size/{n}", timeout=120)
    dt = time.time() - t0
    print(f"    50 MB in {dt:.1f}s = {n * 8 / dt / 1e6:.0f} Mbit/s", flush=True)
    assert dt < 120


def t_parallel():
    with concurrent.futures.ThreadPoolExecutor(30) as ex:
        futs = [ex.submit(http_get, f"10.0.0.{20 + i}", 8080, "/size/500000") for i in range(30)]
        for f in futs:
            f.result(timeout=120)


def t_echo_half_close():
    s = socket.create_connection(("10.0.0.6", 7), timeout=60)
    payload = os.urandom(5 * 1024 * 1024)
    received = bytearray()

    def rx():
        while True:
            d = s.recv(65536)
            if not d:
                break
            received.extend(d)

    th = threading.Thread(target=rx)
    th.start()
    s.sendall(payload)
    s.shutdown(socket.SHUT_WR)  # we are done sending; the echo must still arrive in full
    th.join(90)
    assert not th.is_alive(), "echo did not finish"
    assert bytes(received) == payload, f"echo mismatch: {len(received)} of {len(payload)} bytes"
    s.close()


def t_server_speaks_first():
    s = socket.create_connection(("10.0.0.7", 9001), timeout=30)
    got = b""
    while True:
        d = s.recv(65536)
        if not d:
            break
        got += d
    s.close()
    assert len(got) == 100000, len(got)
    assert got == expected(0, 100000) or got == b"".join(expected(i, min(65536, 100000 - i)) for i in range(0, 100000, 65536))


def t_target_refused():
    t0 = time.time()
    try:
        socket.create_connection(("10.0.0.8", 9998), timeout=15).close()
    except ConnectionRefusedError:
        pass
    else:
        raise AssertionError("connection should have been refused")
    assert time.time() - t0 < 8, "refusal took too long"


def t_immediate_close():
    s = socket.create_connection(("10.0.0.9", 9999), timeout=15)
    s.settimeout(10)
    assert s.recv(10) == b""
    s.close()


def t_slow_upload_backpressure():
    s = socket.create_connection(("10.0.0.10", 9100), timeout=120)
    total = 3 * 1024 * 1024
    s.sendall(os.urandom(total))
    s.shutdown(socket.SHUT_WR)
    s.settimeout(120)
    data = b""
    while len(data) < 8:
        d = s.recv(8 - len(data))
        if not d:
            break
        data += d
    s.close()
    assert len(data) == 8 and struct.unpack(">Q", data)[0] == total, data


def t_dns_a_and_aaaa():
    resp = dns_query("example.com", 1)
    assert a_records(resp) == ["93.184.216.34"], a_records(resp)
    aaaa = dns_query("example.com", 28)
    assert struct.unpack(">H", aaaa[6:8])[0] == 0 and aaaa[3] & 0x0F == 0, "AAAA should be NOERROR with no answers"


def t_domain_reaches_proxy():
    assert a_records(dns_query("example.test", 1)) == ["10.0.0.77"]
    _, body = http_get("10.0.0.77", 8080, "/whoami")
    assert body == b"host=example.test", body
    _, body = http_get("10.0.0.5", 8080, "/whoami")
    assert body == b"host=10.0.0.5", body


def t_other_udp_is_dropped():
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    s.settimeout(1.5)
    s.sendto(b"hello", ("10.0.0.5", 9999))
    try:
        s.recvfrom(100)
    except socket.timeout:
        return
    finally:
        s.close()
    raise AssertionError("unexpected UDP reply")


def t_many_short_connections():
    for _ in range(150):
        http_get("10.0.0.11", 8080, "/size/100", timeout=20)


def t_no_leaked_sessions():
    deadline = time.time() + 60
    while time.time() < deadline:
        if int(last_stats.get("active", "-1")) == 0:
            return
        time.sleep(1)
    raise AssertionError(f"sessions still open after 60s: {last_stats}")


TESTS = [
    ("self-test (routes, DNS helpers, SOCKS5 errors)", t_selftest),
    ("small HTTP download", t_small_http),
    ("50 MB download, byte-exact", t_big_download),
    ("30 parallel downloads", t_parallel),
    ("5 MB echo with half-close", t_echo_half_close),
    ("server speaks first, then closes", t_server_speaks_first),
    ("proxy refuses target -> app sees refusal fast", t_target_refused),
    ("proxy closes immediately -> clean EOF", t_immediate_close),
    ("3 MB upload to a slow server (flow control)", t_slow_upload_backpressure),
    ("DNS: A answered, AAAA is NODATA", t_dns_a_and_aaaa),
    ("domain name (not IP) is sent to the proxy", t_domain_reaches_proxy),
    ("non-DNS UDP is dropped", t_other_udp_is_dropped),
    ("150 short connections in a row", t_many_short_connections),
    ("no sessions left open", t_no_leaked_sessions),
]


def main():
    user = os.environ.get("USER") or getpass.getuser()
    sh(f"sudo ip tuntap add dev {TUN} mode tun user {user}")
    sh(f"sudo ip addr add 10.0.0.2/24 dev {TUN}")
    sh(f"sudo sysctl -qw net.ipv6.conf.{TUN}.disable_ipv6=1 || true")
    sh("sudo sysctl -qw net.ipv4.conf.all.rp_filter=0 net.ipv4.conf.default.rp_filter=0")
    sh(f"sudo sysctl -qw net.ipv4.conf.{TUN}.rp_filter=0 || true")
    fd = attach_tun()
    java = start_java()
    failures = []
    try:
        assert ready.wait(60), "the Java engine did not start"
        cable = socket.create_connection(CABLE)
        cable.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
        pump(fd, cable)
        sh(f"sudo ip link set {TUN} up")
        time.sleep(1)
        for name, fn in TESTS:
            t0 = time.time()
            try:
                fn()
                print(f"PASS  {name}  ({time.time() - t0:.1f}s)", flush=True)
            except Exception as e:  # noqa: BLE001 - report every failure, keep going
                failures.append(name)
                print(f"FAIL  {name}: {type(e).__name__}: {e}", flush=True)
        print("final stats:", last_stats, flush=True)
    finally:
        try:
            java.terminate()
        except Exception:  # noqa: BLE001
            pass
        subprocess.call(f"sudo ip tuntap del dev {TUN} mode tun", shell=True)
    if failures:
        print(f"\n{len(failures)} FAILED: {failures}")
        sys.exit(1)
    print(f"\nall {len(TESTS)} checks passed")


if __name__ == "__main__":
    main()
