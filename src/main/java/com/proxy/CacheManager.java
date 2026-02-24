package com.proxy;

import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

public class CacheManager {

    private static final ConcurrentHashMap<String, byte[]> cache =
            new ConcurrentHashMap<>();

    public static void put(String key, byte[] response) {
        cache.put(key, response);
    }

    public static byte[] get(String key) {
        return cache.get(key);
    }

    public static boolean contains(String key) {
        return cache.containsKey(key);
    }

    public static int size() {
        return cache.size();
    }

    public static void clear() {
        cache.clear();
    }

    public static Set<String> keys() {
        return Collections.unmodifiableSet(new TreeSet<>(cache.keySet()));
    }

    public static boolean removeByUrl(String url) {
        if (url == null) {
            return false;
        }
        String trimmedUrl = url.trim();
        if (trimmedUrl.isEmpty()) {
            return false;
        }

        boolean removed = false;
        for (String key : cache.keySet()) {
            int separatorIndex = key.indexOf(':');
            if (separatorIndex == -1 || separatorIndex == key.length() - 1) {
                continue;
            }
            String cachedUrl = key.substring(separatorIndex + 1);
            if (cachedUrl.equals(trimmedUrl)) {
                removed |= cache.remove(key) != null;
            }
        }
        return removed;
    }
}
