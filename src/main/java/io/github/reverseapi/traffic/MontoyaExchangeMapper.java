package io.github.reverseapi.traffic;

import burp.api.montoya.http.message.HttpHeader;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import io.github.reverseapi.model.CapturedExchange;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MontoyaExchangeMapper {
    private MontoyaExchangeMapper() { }

    public static CapturedExchange map(HttpRequest request, HttpResponse response) {
        URI uri = URI.create(request.url());
        int status = response == null ? 0 : response.statusCode();
        return new CapturedExchange(request.url(), uri.getScheme(), uri.getHost(), uri.getPort(), request.method(),
                request.pathWithoutQuery(), CapturedExchange.parseQuery(request.url()), headers(request.headers()),
                request.bodyToString(), status, response == null ? Map.of() : headers(response.headers()),
                response == null ? "" : response.bodyToString(), Instant.now());
    }

    private static Map<String, List<String>> headers(List<HttpHeader> headers) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (HttpHeader header : headers) result.computeIfAbsent(header.name(), ignored -> new ArrayList<>()).add(header.value());
        return result;
    }
}

