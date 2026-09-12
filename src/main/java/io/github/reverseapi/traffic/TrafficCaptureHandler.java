package io.github.reverseapi.traffic;

import burp.api.montoya.core.ToolType;
import burp.api.montoya.http.handler.HttpHandler;
import burp.api.montoya.http.handler.HttpRequestToBeSent;
import burp.api.montoya.http.handler.HttpResponseReceived;
import burp.api.montoya.http.handler.RequestToBeSentAction;
import burp.api.montoya.http.handler.ResponseReceivedAction;
import io.github.reverseapi.model.CaptureStore;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.CompletableFuture;

public final class TrafficCaptureHandler implements HttpHandler {
    private final CaptureStore store;
    private final java.util.concurrent.ThreadPoolExecutor worker = new java.util.concurrent.ThreadPoolExecutor(
            1, 1, 0, java.util.concurrent.TimeUnit.SECONDS, new java.util.concurrent.ArrayBlockingQueue<>(32),
            task -> { Thread thread = new Thread(task, "ReverseAPI capture"); thread.setDaemon(true); return thread; },
            new java.util.concurrent.ThreadPoolExecutor.AbortPolicy());
    private final java.util.concurrent.atomic.AtomicLong dropped = new java.util.concurrent.atomic.AtomicLong();
    private final java.util.concurrent.atomic.AtomicLong epoch = new java.util.concurrent.atomic.AtomicLong();

    public void close() { captureEnabled.set(false); epoch.incrementAndGet(); worker.shutdownNow(); }
    public long dropped() { return dropped.get() + store.dropped(); }
    public void clear() { epoch.incrementAndGet(); worker.getQueue().clear(); synchronized (store) { store.clear(); } dropped.set(0); }
    public void capture(burp.api.montoya.http.message.requests.HttpRequest request,
                        burp.api.montoya.http.message.responses.HttpResponse response, boolean manual, boolean ignore) {
        try {
            // Never queue arbitrarily large Burp messages or block a callback on analysis.
            if ((long) (request.bodyOffset() + (long) request.body().length()) + (response == null ? 0 : (response.bodyOffset() + (long) response.body().length())) > 256 * 1024) {
                dropped.incrementAndGet(); return;
            }
            long generation = epoch.get();
            worker.execute(() -> {
                try {
                    var exchange = MontoyaExchangeMapper.map(request, response);
                    synchronized (store) {
                        if (generation != epoch.get()) return;
                        store.capture(exchange, manual);
                        if (ignore) {
                            String path = new io.github.reverseapi.normalization.PathNormalizer().normalize(exchange.path());
                            store.operations().stream().filter(op -> op.host().equals(exchange.authority())
                                    && op.method().equalsIgnoreCase(exchange.method())
                                    && new io.github.reverseapi.normalization.PathNormalizer().normalize(op.originalExamplePath()).equals(path))
                                    .forEach(io.github.reverseapi.model.ApiOperation::ignore);
                            store.changed();
                        }
                    }
                } catch (RuntimeException | StackOverflowError failure) { dropped.incrementAndGet(); }
            });
        } catch (RuntimeException failure) { dropped.incrementAndGet(); }
    }

    public CompletableFuture<ProxyHistoryImporter.ImportResult> importHistory(
            java.util.List<burp.api.montoya.proxy.ProxyHttpRequestResponse> history) {
        CompletableFuture<ProxyHistoryImporter.ImportResult> result = new CompletableFuture<>();
        long generation = epoch.get();
        try {
            // Use one worker task so a large history does not overflow the live-capture queue.
            worker.execute(() -> {
                int eligible = 0, withoutResponse = 0, outOfScope = 0;
                java.util.Set<Integer> importedIds = new java.util.HashSet<>();
                try {
                    for (var item : history) {
                        if (generation != epoch.get()) break;
                        if (!item.hasResponse()) { withoutResponse++; continue; }
                        var historyRequest = item.finalRequest();
                        if (onlyInScope.get() && !historyRequest.isInScope()) { outOfScope++; continue; }
                        if (captureNow(historyRequest, item.response(), false, false, generation)) {
                            eligible++;
                            importedIds.add(item.id());
                        }
                    }
                    result.complete(new ProxyHistoryImporter.ImportResult(history.size(), eligible, withoutResponse,
                            outOfScope, java.util.Set.copyOf(importedIds)));
                } catch (RuntimeException | StackOverflowError failure) {
                    result.completeExceptionally(failure);
                }
            });
        } catch (RuntimeException failure) {
            result.completeExceptionally(failure);
        }
        return result;
    }

    private boolean captureNow(burp.api.montoya.http.message.requests.HttpRequest request,
                               burp.api.montoya.http.message.responses.HttpResponse response,
                               boolean manual, boolean ignore, long generation) {
        try {
            if ((long) (request.bodyOffset() + (long) request.body().length())
                    + (response == null ? 0 : (response.bodyOffset() + (long) response.body().length())) > 256 * 1024) {
                dropped.incrementAndGet(); return false;
            }
            var exchange = MontoyaExchangeMapper.map(request, response);
            synchronized (store) {
                if (generation != epoch.get()) return false;
                store.capture(exchange, manual);
                if (ignore) {
                    String path = new io.github.reverseapi.normalization.PathNormalizer().normalize(exchange.path());
                    store.operations().stream().filter(op -> op.host().equals(exchange.authority())
                                    && op.method().equalsIgnoreCase(exchange.method())
                                    && new io.github.reverseapi.normalization.PathNormalizer().normalize(op.originalExamplePath()).equals(path))
                            .forEach(io.github.reverseapi.model.ApiOperation::ignore);
                    store.changed();
                }
            }
            return true;
        } catch (RuntimeException | StackOverflowError failure) {
            dropped.incrementAndGet(); return false;
        }
    }
    private final AtomicBoolean captureEnabled = new AtomicBoolean(true);
    private final AtomicBoolean onlyInScope = new AtomicBoolean(true);

    public TrafficCaptureHandler(CaptureStore store) { this.store = store; }

    @Override
    public RequestToBeSentAction handleHttpRequestToBeSent(HttpRequestToBeSent request) {
        return RequestToBeSentAction.continueWith(request);
    }

    @Override
    public ResponseReceivedAction handleHttpResponseReceived(HttpResponseReceived response) {
        try {
            if (captureEnabled.get() && response.toolSource().isFromTool(ToolType.PROXY)) {
                var request = response.initiatingRequest();
                if (!onlyInScope.get() || request.isInScope()) capture(request, response, false, false);
            }
        } catch (RuntimeException failure) { dropped.incrementAndGet(); }
        return ResponseReceivedAction.continueWith(response);
    }

    public boolean captureEnabled() { return captureEnabled.get(); }
    public void captureEnabled(boolean enabled) { captureEnabled.set(enabled); }
    public boolean onlyInScope() { return onlyInScope.get(); }
    public void onlyInScope(boolean value) { onlyInScope.set(value); }
}

