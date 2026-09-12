package io.github.reverseapi.openapi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.reverseapi.detection.DetectionSettings;
import io.github.reverseapi.model.ApiOperation;
import io.github.reverseapi.model.CapturedExchange;
import java.util.*;

/** Defense against known credentials reappearing in paths, property names or other metadata. */
public final class ExportPrivacy {
    private static final ObjectMapper JSON = io.github.reverseapi.schema.JsonSupport.mapper();
    private ExportPrivacy() { }
    private static boolean sensitive(String name) {
        if (name.equalsIgnoreCase("pass") || name.equalsIgnoreCase("pwd")) return true;
        return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "")
                .matches(".*(password|passwd|secret|token|authorization|apikey|session|credential|subscriptionkey).*");
    }
    public static void verify(JsonNode document, List<ApiOperation> operations, DetectionSettings settings) {
        Set<String> secrets = new LinkedHashSet<>();
        for (ApiOperation operation : operations) for (CapturedExchange sample : operation.samples()) {
            for (var headers : List.of(sample.requestHeaders(), sample.responseHeaders(), sample.queryParameters())) {
                headers.forEach((name, values) -> {
                    if (sensitive(name) || Arrays.stream(settings.apiKeyHeaders().split(",")).anyMatch(h -> h.trim().equalsIgnoreCase(name)))
                        values.forEach(value -> { add(secrets, value); if (name.equalsIgnoreCase("Authorization")) {
                            int space = value.indexOf(' '); if (space >= 0) add(secrets, value.substring(space + 1));
                        }});
                    if (name.equalsIgnoreCase("Cookie") || name.equalsIgnoreCase("Set-Cookie")) {
                        for (String value : values) for (String part : value.split(";")) {
                            int equals = part.indexOf('='); if (equals >= 0) add(secrets, part.substring(equals + 1).trim());
                        }
                    }
                });
            }
            for (String body : List.of(sample.requestBody(), sample.responseBody())) {
                JsonNode parsed = null;
                try { parsed = JSON.readTree(body); } catch (Exception ignored) { }
                if (parsed != null) collect(parsed, secrets, 0);
            }
            if (sample.requestContentType().equals("application/x-www-form-urlencoded")) {
                CapturedExchange.parseQuery("https://local.invalid/?" + sample.requestBody())
                        .forEach((name, values) -> { if (sensitive(name)) values.forEach(value -> add(secrets, value)); });
            }
        }
        inspect(document, secrets);
    }
    private static void add(Set<String> secrets, String value) {
        if (!value.isBlank()) secrets.add(value);
        if (secrets.size() > 4096) throw new IllegalArgumentException("Too many credential values to safely check; reduce included captures");
    }
    private static void collect(JsonNode node, Set<String> secrets, int depth) {
        if (depth > 32) return;
        if (node.isObject()) node.fields().forEachRemaining(e -> {
            if (sensitive(e.getKey()) && e.getValue().isValueNode() && !e.getValue().isNull()) add(secrets, e.getValue().asText());
            collect(e.getValue(), secrets, depth + 1);
        });
        else if (node.isArray()) node.forEach(child -> collect(child, secrets, depth + 1));
    }
    private static void inspect(JsonNode node, Set<String> secrets) {
        if (node.isObject()) node.fields().forEachRemaining(e -> { check(e.getKey(), secrets); inspect(e.getValue(), secrets); });
        else if (node.isArray()) node.forEach(child -> inspect(child, secrets));
        else if (node.isTextual()) check(node.asText(), secrets);
    }
    private static void check(String text, Set<String> secrets) {
        for (String secret : secrets) {
            if (text.equals(secret) || (secret.length() >= 4 && text.contains(secret)))
                throw new IllegalArgumentException("A captured credential appears in exported metadata; exclude the operation or normalize its path");
        }
    }
}
