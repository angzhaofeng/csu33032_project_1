package com.proxy;

import java.util.concurrent.atomic.AtomicLong;

public class ProxyStats {

    private static final AtomicLong cacheHits = new AtomicLong();
    private static final AtomicLong networkFetches = new AtomicLong();
    private static final AtomicLong totalCacheHitTimeMs = new AtomicLong();
    private static final AtomicLong totalNetworkFetchTimeMs = new AtomicLong();

    private ProxyStats() {
    }

    public static void recordCacheHit(long durationMs) {
        cacheHits.incrementAndGet();
        totalCacheHitTimeMs.addAndGet(Math.max(0, durationMs));
    }

    public static void recordNetworkFetch(long durationMs) {
        networkFetches.incrementAndGet();
        totalNetworkFetchTimeMs.addAndGet(Math.max(0, durationMs));
    }

    public static String buildReport() {
        long hits = cacheHits.get();
        long fetches = networkFetches.get();
        long cacheTime = totalCacheHitTimeMs.get();
        long networkTime = totalNetworkFetchTimeMs.get();

        double avgCache = hits == 0 ? 0.0 : (double) cacheTime / hits;
        double avgNetwork = fetches == 0 ? 0.0 : (double) networkTime / fetches;
        double improvementPercent = avgNetwork <= 0.0
                ? 0.0
                : ((avgNetwork - avgCache) / avgNetwork) * 100.0;

        StringBuilder report = new StringBuilder();
        report.append("Proxy Timing Stats:\n");
        report.append("- Cache hits: ").append(hits).append('\n');
        report.append("- Network fetches: ").append(fetches).append('\n');
        report.append("- Avg cache response time: ").append(String.format("%.2f", avgCache)).append(" ms\n");
        report.append("- Avg network response time: ").append(String.format("%.2f", avgNetwork)).append(" ms\n");
        report.append("- Estimated avg speed-up from cache: ").append(String.format("%.2f", improvementPercent)).append("%");
        return report.toString();
    }
}
