package com.example.proxy.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the tunnel's route list. Android before 13 cannot "exclude" a route, so to keep the
 * local network (router, printers, casting) outside the tunnel we add routes for everything
 * EXCEPT the private ranges.
 */
public final class RouteMath {
    private RouteMath() {
    }

    public static final class Route {
        public final int address;
        public final int prefix;

        public Route(int address, int prefix) {
            this.address = address;
            this.prefix = prefix;
        }

        public String addressString() {
            return ((address >>> 24) & 0xFF) + "." + ((address >>> 16) & 0xFF) + "."
                    + ((address >>> 8) & 0xFF) + "." + (address & 0xFF);
        }

        @Override
        public String toString() {
            return addressString() + "/" + prefix;
        }
    }

    public static Route route(int a, int b, int c, int d, int prefix) {
        return new Route((a << 24) | (b << 16) | (c << 8) | d, prefix);
    }

    /** Ranges that must stay direct: loopback, link-local, private networks, multicast/reserved. */
    public static Route[] localNetworks() {
        return new Route[]{
                route(10, 0, 0, 0, 8),
                route(127, 0, 0, 0, 8),
                route(169, 254, 0, 0, 16),
                route(172, 16, 0, 0, 12),
                route(192, 168, 0, 0, 16),
                route(224, 0, 0, 0, 4),
                route(240, 0, 0, 0, 4)
        };
    }

    /** Minimal set of routes covering all IPv4 addresses except the given ranges. */
    public static List<Route> allExcept(Route... excluded) {
        List<Route> out = new ArrayList<>();
        split(0, 0, excluded, out);
        return out;
    }

    private static void split(int addr, int prefix, Route[] ex, List<Route> out) {
        boolean covered = false;
        boolean overlaps = false;
        for (Route e : ex) {
            if (e.prefix <= prefix && sameBits(e.address, addr, e.prefix)) {
                covered = true;
            } else if (sameBits(e.address, addr, Math.min(e.prefix, prefix))) {
                overlaps = true;
            }
        }
        if (covered) {
            return;
        }
        if (!overlaps) {
            out.add(new Route(addr, prefix));
            return;
        }
        int next = prefix + 1;
        split(addr, next, ex, out);
        split(addr | (1 << (31 - prefix)), next, ex, out);
    }

    private static boolean sameBits(int a, int b, int bits) {
        if (bits == 0) {
            return true;
        }
        int mask = -1 << (32 - bits);
        return (a & mask) == (b & mask);
    }
}
