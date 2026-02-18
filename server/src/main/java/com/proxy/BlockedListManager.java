package com.proxy;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class BlockedListManager {

    private static final Set<String> blockedHosts =
            ConcurrentHashMap.newKeySet();

    static {
        blockedHosts.add("example.com");
        blockedHosts.add("blocked.com");
        blockedHosts.add("httpforever.com");
    }

    public static boolean isBlocked(String host) {
        return blockedHosts.contains(host.toLowerCase());
    }
}
