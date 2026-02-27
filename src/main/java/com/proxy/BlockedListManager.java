package com.proxy;

import java.net.URL;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;
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
        String normalized = normalizeHost(host);
        if (normalized == null) {
            return false;
        }

        for (String blockedHost : blockedHosts) {
            if (normalized.equals(blockedHost) || normalized.endsWith("." + blockedHost)) {
                return true;
            }
        }
        return false;
    }

    public static boolean block(String host) {
        String normalized = normalizeHost(host);
        return normalized != null && blockedHosts.add(normalized);
    }

    public static boolean unblock(String host) {
        String normalized = normalizeHost(host);
        return normalized != null && blockedHosts.remove(normalized);
    }

    public static Set<String> getBlockedHosts() {
        return Collections.unmodifiableSet(new TreeSet<>(blockedHosts));
    }

    private static String normalizeHost(String host) {
        if (host == null) {
            return null;
        }

        String normalized = host.trim().toLowerCase();
        if (normalized.isEmpty()) {
            return null;
        }

        if (normalized.startsWith("http://") || normalized.startsWith("https://")) {
            try {
                URL parsed = new URL(normalized);
                normalized = parsed.getHost();
            } catch (Exception ignored) {
            }
        }

        int slashIndex = normalized.indexOf('/');
        if (slashIndex >= 0) {
            normalized = normalized.substring(0, slashIndex);
        }

        int colonIndex = normalized.indexOf(':');
        if (colonIndex >= 0) {
            normalized = normalized.substring(0, colonIndex);
        }

        while (normalized.endsWith(".")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }

        if (normalized.isEmpty()) {
            return null;
        }

        return normalized;
    }

}
