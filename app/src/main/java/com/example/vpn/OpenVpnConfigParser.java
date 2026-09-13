package com.example.vpn;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts summary fields (remote host/port/protocol) from an .ovpn file's
 * text for display purposes. Does not validate the config is a working
 * OpenVPN profile, and does not itself establish any connection - see the
 * VPN Servers screen for what's implemented today vs. what still needs the
 * official OpenVPN engine integrated.
 */
public final class OpenVpnConfigParser {

    public static class ParsedResult {
        public final String server;
        public final int port;
        public final String protocol;

        public ParsedResult(String server, int port, String protocol) {
            this.server = server;
            this.port = port;
            this.protocol = protocol;
        }
    }

    private static final Pattern REMOTE_PATTERN =
            Pattern.compile("^\\s*remote\\s+(\\S+)(?:\\s+(\\d+))?(?:\\s+(tcp|udp))?\\s*$",
                    Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
    private static final Pattern PROTO_PATTERN =
            Pattern.compile("^\\s*proto\\s+(tcp|udp)\\S*\\s*$", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);

    private OpenVpnConfigParser() {
    }

    public static ParsedResult parse(String configText) {
        if (configText == null) {
            return new ParsedResult("unknown", 1194, "udp");
        }

        String server = "unknown";
        int port = 1194;
        String protocol = null;

        Matcher remoteMatcher = REMOTE_PATTERN.matcher(configText);
        if (remoteMatcher.find()) {
            server = remoteMatcher.group(1);
            if (remoteMatcher.group(2) != null) {
                try {
                    port = Integer.parseInt(remoteMatcher.group(2));
                } catch (NumberFormatException ignored) {
                }
            }
            if (remoteMatcher.group(3) != null) {
                protocol = remoteMatcher.group(3).toLowerCase(Locale.ROOT);
            }
        }

        if (protocol == null) {
            Matcher protoMatcher = PROTO_PATTERN.matcher(configText);
            protocol = protoMatcher.find() ? protoMatcher.group(1).toLowerCase(Locale.ROOT) : "udp";
        }

        return new ParsedResult(server, port, protocol);
    }

    /** True if the text at least looks like an OpenVPN config (has a "remote" directive). */
    public static boolean looksValid(String configText) {
        return configText != null && REMOTE_PATTERN.matcher(configText).find();
    }
}
