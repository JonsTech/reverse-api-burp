package io.github.reverseapi.model;

import java.net.URI;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public record CapturedExchange(
        String url, String scheme, String host, int port, String method, String path,
        Map<String, List<String>> queryParameters, Map<String, List<String>> requestHeaders,
        String requestBody, int statusCode, Map<String, List<String>> responseHeaders,
        String responseBody, Instant capturedAt) {

    public CapturedExchange {
        queryParameters = immutableCopy(queryParameters);
        requestHeaders = immutableCopy(requestHeaders);
        responseHeaders = immutableCopy(responseHeaders);
        requestBody = requestBody == null ? "" : requestBody;
        responseBody = responseBody == null ? "" : responseBody;
    }

    private static Map<String, List<String>> immutableCopy(Map<String, List<String>> source) {
        Map<String, List<String>> copy = new LinkedHashMap<>();
        if (source != null) source.forEach((k, v) -> copy.put(k, List.copyOf(v)));
        return Collections.unmodifiableMap(copy);
    }

    public long estimatedBytes() {
        long chars = url.length() + path.length() + requestBody.length() + responseBody.length();
        for (var headers : List.of(queryParameters, requestHeaders, responseHeaders)) {
            for (var entry : headers.entrySet()) {
                chars += entry.getKey().length() + 64;
                for (String value : entry.getValue()) chars += value.length() + 32;
            }
        }
        return 1024 + chars * 2;
    }

    public String requestHeader(String name) { return firstHeader(requestHeaders, name); }
    public String responseHeader(String name) { return firstHeader(responseHeaders, name); }
    public String requestContentType() { return mediaType(requestHeader("Content-Type")); }
    public String responseContentType() { return mediaType(responseHeader("Content-Type")); }

    private static String firstHeader(Map<String, List<String>> headers, String name) {
        return headers.entrySet().stream()
                .filter(e -> e.getKey().equalsIgnoreCase(name))
                .flatMap(e -> e.getValue().stream()).findFirst().orElse("");
    }

    private static String mediaType(String value) {
        int semicolon = value.indexOf(';');
        return (semicolon < 0 ? value : value.substring(0, semicolon)).trim().toLowerCase(Locale.ROOT);
    }

    public String authority() {
        boolean defaultPort = ("https".equalsIgnoreCase(scheme) && port == 443)
                || ("http".equalsIgnoreCase(scheme) && port == 80) || port <= 0;
        return defaultPort ? host : host + ":" + port;
    }

    public static Map<String, List<String>> parseQuery(String url) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        try {
            String raw = URI.create(url).getRawQuery();
            if (raw == null || raw.isBlank()) return result;
            for (String pair : raw.split("&")) {
                String[] pieces = pair.split("=", 2);
                String name = java.net.URLDecoder.decode(pieces[0], java.nio.charset.StandardCharsets.UTF_8);
                String value = pieces.length > 1
                        ? java.net.URLDecoder.decode(pieces[1], java.nio.charset.StandardCharsets.UTF_8) : "";
                result.computeIfAbsent(name, ignored -> new java.util.ArrayList<>()).add(value);
            }
        } catch (RuntimeException ignored) { }
        return result;
    }
}

