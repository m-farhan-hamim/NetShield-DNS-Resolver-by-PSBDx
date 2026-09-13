package com.example.vpn;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts summary fields (Endpoint host/port) from a WireGuard .conf file's
 * text for display purposes. Does not itself establish any connection - see
 * the VPN Servers screen for what's implemented today vs. what still needs
 * the official WireGuard tunnel library integrated.
 */
public final class WireGuardConfigParser {

    public static class ParsedResult {
        public final String server;
        public final int port;

        public ParsedResult(String server, int port) {
            this.server = server;
            this.port = port;
        }
    }

    private static final Pattern ENDPOINT_PATTERN =
            Pattern.compile("^\\s*Endpoint\\s*=\\s*([^:\\s]+):(\\d+)\\s*$",
                    Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
    private static final Pattern PRIVATE_KEY_PATTERN =
            Pattern.compile("^\\s*PrivateKey\\s*=", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
    private static final Pattern PUBLIC_KEY_PATTERN =
            Pattern.compile("^\\s*PublicKey\\s*=", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);

    private WireGuardConfigParser() {
    }

    public static ParsedResult parse(String configText) {
        if (configText == null) {
            return new ParsedResult("unknown", 51820);
        }
        Matcher matcher = ENDPOINT_PATTERN.matcher(configText);
        if (matcher.find()) {
            String host = matcher.group(1);
            int port = 51820;
            try {
                port = Integer.parseInt(matcher.group(2));
            } catch (NumberFormatException ignored) {
            }
            return new ParsedResult(host, port);
        }
        return new ParsedResult("unknown", 51820);
    }

    /** True if the text at least looks like a WireGuard config ([Interface] with keys, plus an Endpoint). */
    public static boolean looksValid(String configText) {
        if (configText == null) return false;
        boolean hasKeys = PRIVATE_KEY_PATTERN.matcher(configText).find()
                || PUBLIC_KEY_PATTERN.matcher(configText).find();
        return hasKeys && ENDPOINT_PATTERN.matcher(configText).find();
    }
}
