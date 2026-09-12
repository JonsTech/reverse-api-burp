package io.github.reverseapi.traffic;

import burp.api.montoya.proxy.Proxy;
import burp.api.montoya.proxy.ProxyHttpRequestResponse;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/** Imports only the Proxy entries that pre-date this extension instance. */
public final class ProxyHistoryImporter {
    private final Proxy proxy;
    private final TrafficCaptureHandler capture;
    private final ZonedDateTime extensionLoadedAt;
    private final Set<Integer> importedIds = ConcurrentHashMap.newKeySet();
    private final java.util.concurrent.atomic.AtomicLong generation = new java.util.concurrent.atomic.AtomicLong();

    public ProxyHistoryImporter(Proxy proxy, TrafficCaptureHandler capture, ZonedDateTime extensionLoadedAt) {
        this.proxy = Objects.requireNonNull(proxy);
        this.capture = Objects.requireNonNull(capture);
        this.extensionLoadedAt = Objects.requireNonNull(extensionLoadedAt);
    }

    public CompletableFuture<ImportResult> importEarlierHistory() {
        long startedInGeneration = generation.get();
        List<ProxyHttpRequestResponse> earlier = proxy.history().stream()
                .filter(item -> item.time().isBefore(extensionLoadedAt))
                .filter(item -> !importedIds.contains(item.id()))
                .toList();
        return capture.importHistory(earlier).thenApply(result -> {
            if (generation.get() == startedInGeneration) importedIds.addAll(result.importedIds());
            return result;
        });
    }

    public void reset() { generation.incrementAndGet(); importedIds.clear(); }

    public record ImportResult(int scanned, int eligible, int skippedWithoutResponse, int skippedOutOfScope,
                               Set<Integer> importedIds) { }
}
