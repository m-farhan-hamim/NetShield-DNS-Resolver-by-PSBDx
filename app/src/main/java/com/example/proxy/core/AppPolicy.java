package com.example.proxy.core;

import java.util.Collection;
import java.util.Set;

/**
 * The one rule behind "which apps use the proxy", kept free of Android classes so it can be tested
 * on a normal JVM. Both modes work on the package names that share one Android user id.
 */
public final class AppPolicy {

    private AppPolicy() {
    }

    /**
     * @param includeMode true = only the selected apps use the proxy; false = every app except the selected ones
     * @param selected    the ticked packages for the active mode
     * @param packages    all packages that share the connection owner's user id
     * @return true to send the connection through the proxy, false to connect directly
     */
    public static boolean useProxy(boolean includeMode, Set<String> selected, Collection<String> packages) {
        if (selected.isEmpty()) {
            // Nothing ticked: exclude mode means nobody is excluded, and an empty include list is
            // refused by the screen. If one still gets here, fail closed and keep using the proxy.
            return true;
        }
        boolean listed = false;
        for (String pkg : packages) {
            if (selected.contains(pkg)) {
                listed = true;
                break;
            }
        }
        return includeMode ? listed : !listed;
    }
}
