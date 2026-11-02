package io.github.reverseapi.traffic;

import burp.api.montoya.core.ByteArray;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import burp.api.montoya.proxy.Proxy;
import burp.api.montoya.proxy.ProxyHttpRequestResponse;
import io.github.reverseapi.model.CaptureStore;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class ProxyHistoryImporterTest {
    @Test void importsHistoryBeforeAndAfterLoadingEvenWhenLiveCaptureIsPaused() throws Exception {
        var store = new CaptureStore();
        var capture = new TrafficCaptureHandler(store);
        var loadedAt = ZonedDateTime.now();
        var history = List.of(entry(1, "/api/older", loadedAt.minusDays(1), false, new AtomicBoolean(true)),
                entry(2, "/api/newer", loadedAt.plusDays(1), false, new AtomicBoolean(true)));
        try {
            assertThat(capture.captureEnabled()).isTrue();
            assertThat(capture.onlyInScope()).isFalse();
            capture.captureEnabled(false);
            var result = importer(capture, history).importHistory().get(5, TimeUnit.SECONDS);
            assertThat(result.scanned()).isEqualTo(2);
            assertThat(result.eligible()).isEqualTo(2);
            assertThat(store.operations()).extracting(op -> op.normalizedPath())
                    .containsExactlyInAnyOrder("/api/older", "/api/newer");
        } finally { capture.close(); }
    }

    @Test void retriesScopeSkippedEntriesAfterScopeFilterIsDisabled() throws Exception {
        var store = new CaptureStore();
        var capture = new TrafficCaptureHandler(store);
        var history = List.of(entry(1, "/api/items", ZonedDateTime.now().plusDays(1), false, new AtomicBoolean(true)));
        try {
            var importer = importer(capture, history);
            capture.onlyInScope(true);
            var skipped = importer.importHistory().get(5, TimeUnit.SECONDS);
            assertThat(skipped.skippedOutOfScope()).isEqualTo(1);
            assertThat(store.operations()).isEmpty();
            capture.onlyInScope(false);
            assertThat(importer.importHistory().get(5, TimeUnit.SECONDS).eligible()).isEqualTo(1);
            assertThat(store.operations()).hasSize(1);
        } finally { capture.close(); }
    }

    @Test void retriesMissingResponsesAndDoesNotRepeatSuccessfulImportsUntilClear() throws Exception {
        var store = new CaptureStore();
        var capture = new TrafficCaptureHandler(store);
        var hasResponse = new AtomicBoolean(false);
        try {
            var importer = importer(capture, List.of(entry(1, "/api/items", ZonedDateTime.now(), true, hasResponse)));
            assertThat(importer.importHistory().get(5, TimeUnit.SECONDS).skippedWithoutResponse()).isEqualTo(1);
            hasResponse.set(true);
            assertThat(importer.importHistory().get(5, TimeUnit.SECONDS).eligible()).isEqualTo(1);
            assertThat(importer.importHistory().get(5, TimeUnit.SECONDS).scanned()).isZero();
            assertThat(store.operations().get(0).observations()).isEqualTo(1);
            importer.reset();
            capture.clear();
            assertThat(importer.importHistory().get(5, TimeUnit.SECONDS).eligible()).isEqualTo(1);
            assertThat(store.operations()).hasSize(1);
        } finally { capture.close(); }
    }

    private ProxyHistoryImporter importer(TrafficCaptureHandler capture, List<ProxyHttpRequestResponse> history) {
        return new ProxyHistoryImporter(stub(Proxy.class, (proxy, method, args) -> {
            if (method.getName().equals("history")) return history;
            throw new UnsupportedOperationException(method.getName());
        }), capture);
    }

    private ProxyHttpRequestResponse entry(int id, String path, ZonedDateTime time, boolean inScope,
                                           AtomicBoolean hasResponse) {
        ByteArray bytes = stub(ByteArray.class, (proxy, method, args) -> {
            if (method.getName().equals("length")) return 2;
            throw new UnsupportedOperationException(method.getName());
        });
        HttpRequest request = stub(HttpRequest.class, (proxy, method, args) -> switch (method.getName()) {
            case "bodyOffset" -> 0;
            case "body" -> bytes;
            case "bodyToString" -> "";
            case "url" -> "https://example.test" + path;
            case "pathWithoutQuery" -> path;
            case "method" -> "GET";
            case "headers" -> List.of();
            case "isInScope" -> inScope;
            default -> throw new UnsupportedOperationException(method.getName());
        });
        HttpResponse response = stub(HttpResponse.class, (proxy, method, args) -> switch (method.getName()) {
            case "bodyOffset" -> 0;
            case "body" -> bytes;
            case "bodyToString" -> "{}";
            case "headers" -> List.of();
            case "statusCode" -> (short) 200;
            default -> throw new UnsupportedOperationException(method.getName());
        });
        return stub(ProxyHttpRequestResponse.class, (proxy, method, args) -> switch (method.getName()) {
            case "id" -> id;
            case "time" -> time;
            case "hasResponse" -> hasResponse.get();
            case "finalRequest" -> request;
            case "response" -> response;
            default -> throw new UnsupportedOperationException(method.getName());
        });
    }

    private <T> T stub(Class<T> type, InvocationHandler handler) {
        return type.cast(java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{type}, handler));
    }
}
