package io.github.reverseapi;

import io.github.reverseapi.model.CapturedExchange;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class TestExchange {
    private TestExchange() { }
    public static CapturedExchange exchange(String method, String path, String requestType, String requestBody,
                                            int status, String responseType, String responseBody) {
        String url = "https://api.example.test" + path;
        return new CapturedExchange(url, "https", "api.example.test", 443, method, path.split("\\?", 2)[0],
                CapturedExchange.parseQuery(url), headers(requestType), requestBody, status, headers(responseType), responseBody, Instant.now());
    }
    private static Map<String, List<String>> headers(String type) { return type.isBlank() ? Map.of() : Map.of("Content-Type", List.of(type)); }
}

