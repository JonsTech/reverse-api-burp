package io.github.reverseapi.model;

import io.github.reverseapi.detection.DetectionResult;

import java.util.ArrayList;
import java.util.List;

public final class ApiOperation {
    public enum ReviewState { AUTO, MANUAL_API, IGNORED }
    private final String host;
    private final String method;
    private final String originalExamplePath;
    private final List<CapturedExchange> samples = new ArrayList<>();
    private String normalizedPath;
    private boolean included;
    private ReviewState reviewState = ReviewState.AUTO;
    private int observations;
    private int confidence;
    private String reason;

    public ApiOperation(CapturedExchange first, String normalizedPath, DetectionResult detection, boolean manual) {
        this.host = first.authority();
        this.method = first.method().toUpperCase(java.util.Locale.ROOT);
        this.originalExamplePath = first.path();
        this.normalizedPath = normalizedPath;
        this.included = manual || detection.includedByDefault();
        this.reviewState = manual ? ReviewState.MANUAL_API : ReviewState.AUTO;
        observe(first, detection);
    }

    public synchronized void observe(CapturedExchange exchange, DetectionResult detection) {
        if (observations < Integer.MAX_VALUE) observations++;
        if (reviewState == ReviewState.AUTO && detection.includedByDefault()) included = true;
        confidence = Math.max(confidence, detection.confidence());
        if (reason == null || detection.confidence() >= confidence) reason = detection.reason();
        if (samples.size() < 25) samples.add(exchange);
    }

    public synchronized ApiOperation snapshot() {
        ApiOperation copy = new ApiOperation(samples.get(0), normalizedPath,
                new DetectionResult(true, false, confidence, reason), false);
        copy.samples.clear(); copy.samples.addAll(samples);
        copy.included = included; copy.reviewState = reviewState; copy.observations = observations;
        return copy;
    }

    public String host() { return host; }
    public String method() { return method; }
    public String originalExamplePath() { return originalExamplePath; }
    public synchronized String normalizedPath() { return normalizedPath; }
    public synchronized void normalizedPath(String path) { normalizedPath = path; }
    public synchronized boolean included() { return included && reviewState != ReviewState.IGNORED; }
    public synchronized void included(boolean value) { included = value; reviewState = value ? ReviewState.MANUAL_API : ReviewState.IGNORED; }
    public synchronized ReviewState reviewState() { return reviewState; }
    public synchronized void markApi() { reviewState = ReviewState.MANUAL_API; included = true; }
    public synchronized void ignore() { reviewState = ReviewState.IGNORED; included = false; }
    public synchronized int observations() { return observations; }
    public synchronized int confidence() { return confidence; }
    public synchronized String reason() { return reviewState == ReviewState.MANUAL_API ? "Manually marked API" : reviewState == ReviewState.IGNORED ? "Manually ignored" : reason; }
    public synchronized List<CapturedExchange> samples() { return List.copyOf(samples); }
    public synchronized CapturedExchange latest() { return samples.get(samples.size() - 1); }
}
