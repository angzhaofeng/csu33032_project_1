package com.proxy;

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
}
