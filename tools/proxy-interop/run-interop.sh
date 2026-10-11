#!/usr/bin/env bash
# Builds the pure-Java proxy core plus the test harness and runs the Linux TUN interop test.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
OUT="$(mktemp -d)"
javac -Xlint:all -d "$OUT" \
  "$ROOT"/app/src/main/java/com/example/proxy/core/*.java \
  "$ROOT"/app/src/main/java/com/example/dns/IpPacketUtils.java \
  "$ROOT"/tools/proxy-interop/java/*.java
python3 "$ROOT"/tools/proxy-interop/interop_tests.py "$OUT"
