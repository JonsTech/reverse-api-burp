package io.github.reverseapi.model;

import io.github.reverseapi.detection.ApiDetector;
import io.github.reverseapi.detection.DetectionResult;
import io.github.reverseapi.detection.DetectionSettings;
import io.github.reverseapi.normalization.PathNormalizer;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class CaptureStore {
    private final java.util.concurrent.atomic.AtomicLong oversized = new java.util.concurrent.atomic.AtomicLong();
    private final DetectionSettings settings = new DetectionSettings();
    private final ApiDetector detector = new ApiDetector(settings);
    private final EndpointDeduplicator deduplicator = new EndpointDeduplicator(new PathNormalizer());
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

    public synchronized void capture(CapturedExchange exchange, boolean manual) {
        if (exchange.estimatedBytes() > 512 * 1024) { oversized.incrementAndGet(); return; }
        DetectionResult detection = detector.detect(exchange);
        if ((!detection.api() || detection.ignoredStatic()) && !manual) return;
        deduplicator.add(exchange, detection, manual);
        listeners.forEach(Runnable::run);
    }

    public List<ApiOperation> operations() { return deduplicator.operations(); }
    public DetectionSettings settings() { return settings; }
    public void addListener(Runnable listener) { listeners.add(listener); }
    public long dropped() { return deduplicator.dropped() + oversized.get(); }
    public synchronized void clear() { deduplicator.clear(); oversized.set(0); listeners.forEach(Runnable::run); }
    public void changed() { listeners.forEach(Runnable::run); }
}
