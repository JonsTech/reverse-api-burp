package io.github.reverseapi.model;

import io.github.reverseapi.detection.DetectionResult;
import io.github.reverseapi.normalization.PathNormalizer;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class EndpointDeduplicator {
    public static final int MAX_OPERATIONS = 2000;
    public static final long MAX_RETAINED_BYTES = 32L * 1024 * 1024;
    private long retainedBytes;
    private long dropped;
    private final PathNormalizer normalizer;
    private final Map<String, ApiOperation> operations = new LinkedHashMap<>();

    public EndpointDeduplicator(PathNormalizer normalizer) { this.normalizer = normalizer; }

    public synchronized ApiOperation add(CapturedExchange exchange, DetectionResult detection, boolean manual) {
        String normalized = normalizer.normalize(exchange.path());
        String key = exchange.scheme().toLowerCase(java.util.Locale.ROOT) + "://" + exchange.authority().toLowerCase(java.util.Locale.ROOT) + "\n" + exchange.method().toUpperCase(java.util.Locale.ROOT) + "\n" + normalized;
        ApiOperation existing = operations.get(key);
        long cost = exchange.estimatedBytes();
        if ((existing == null && operations.size() >= MAX_OPERATIONS)
                || ((existing == null || existing.samples().size() < 25) && retainedBytes + cost > MAX_RETAINED_BYTES)) {
            dropped++; return null;
        }
        if (existing == null || existing.samples().size() < 25) retainedBytes += cost;
        if (existing != null) {
            existing.observe(exchange, detection);
            if (manual) existing.markApi();
            return existing;
        }
        ApiOperation created = new ApiOperation(exchange, normalized, detection, manual);
        operations.put(key, created);
        return created;
    }

    public synchronized List<ApiOperation> operations() { return List.copyOf(operations.values()); }
    public synchronized long dropped() { return dropped; }
    public synchronized void clear() { operations.clear(); retainedBytes = 0; dropped = 0; }
}

