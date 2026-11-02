package io.github.reverseapi.traffic;

import burp.api.montoya.proxy.Proxy;
import burp.api.montoya.proxy.ProxyHttpRequestResponse;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/** Imports available Proxy history, including traffic missed by live capture. */
public final class ProxyHistoryImporter {
    private final Proxy proxy;
    private final TrafficCaptureHandler capture;
    private final Set<Integer> importedIds = ConcurrentHashMap.newKeySet();
    private final java.util.concurrent.atomic.AtomicLong generation = new java.util.concurrent.atomic.AtomicLong();

    public ProxyHistoryImporter(Proxy proxy, TrafficCaptureHandler capture) {
        this.proxy = Objects.requireNonNull(proxy);
        this.capture = Objects.requireNonNull(capture);
    }

    public CompletableFuture<ImportResult> importHistory() {
        long startedInGeneration = generation.get();
        List<ProxyHttpRequestResponse> pending = proxy.history().stream()
                .filter(item -> !importedIds.contains(item.id()))
                .toList();
        return capture.importHistory(pending).thenApply(result -> {
            if (generation.get() == startedInGeneration) importedIds.addAll(result.importedIds());
            return result;
        });
    }

    public void reset() { generation.incrementAndGet(); importedIds.clear(); }

    public record ImportResult(int scanned, int eligible, int skippedWithoutResponse, int skippedOutOfScope,
                               Set<Integer> importedIds) { }
}
