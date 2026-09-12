package io.github.reverseapi.traffic;

import burp.api.montoya.core.ByteArray;
import burp.api.montoya.http.message.requests.HttpRequest;
import io.github.reverseapi.model.CaptureStore;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

class CaptureWorkerTest {
    private HttpRequest request(String path, int size, Runnable bodyRead) {
        ByteArray bytes = (ByteArray) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{ByteArray.class},
                (proxy, method, args) -> method.getName().equals("length") ? size : null);
        return (HttpRequest) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{HttpRequest.class},
                (proxy, method, args) -> switch(method.getName()) {
                    case "bodyOffset" -> 0;
                    case "body" -> bytes;
                    case "url" -> "https://example.test" + path;
                    case "pathWithoutQuery" -> path;
                    case "method" -> "GET";
                    case "headers" -> List.of();
                    case "bodyToString" -> { bodyRead.run(); yield ""; }
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }
    @Test void queueIsBoundedAndClearDiscardsInflightGeneration() throws Exception {
        var store = new CaptureStore(); var handler = new TrafficCaptureHandler(store);
        var started = new CountDownLatch(1); var release = new CountDownLatch(1);
        try {
            handler.capture(request("/old", 10, () -> {
                started.countDown();
                try { if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException(); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            }), null, true, false);
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            for (int i=0;i<40;i++) handler.capture(request("/queued",10,()->{}),null,true,false);
            assertThat(handler.dropped()).isEqualTo(8);
            handler.clear(); release.countDown();
            var captured = new CountDownLatch(1); store.addListener(captured::countDown);
            handler.capture(request("/new",10,()->{}),null,true,false);
            assertThat(captured.await(5,TimeUnit.SECONDS)).isTrue();
            assertThat(store.operations()).hasSize(1);
            assertThat(store.operations().get(0).normalizedPath()).isEqualTo("/new");
        } finally { release.countDown(); handler.close(); }
    }
    @Test void oversizedAndMalformedMessagesDoNotPreventLaterCapture() throws Exception {
        var store = new CaptureStore(); var handler = new TrafficCaptureHandler(store);
        try {
            handler.capture(request("/huge",300000,()->{ throw new AssertionError("must not decode body"); }),null,true,false);
            handler.capture(request("/bad",10,()->{ throw new IllegalArgumentException("malformed"); }),null,true,false);
            var captured = new CountDownLatch(1); store.addListener(captured::countDown);
            handler.capture(request("/good",10,()->{}),null,true,false);
            assertThat(captured.await(5,TimeUnit.SECONDS)).isTrue();
            assertThat(handler.dropped()).isEqualTo(2);
            assertThat(store.operations()).hasSize(1);
        } finally { handler.close(); }
    }
}
