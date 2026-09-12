package io.github.reverseapi.export;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
import io.github.reverseapi.detection.DetectionSettings;
import io.github.reverseapi.model.*;
import io.github.reverseapi.openapi.ExportExamples;
import io.github.reverseapi.openapi.ExportPrivacy;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** One retained, paired request/response per included operation. Never sends traffic. */
public final class PostmanGenerator {
    private final ObjectMapper json = new ObjectMapper();
    private final DetectionSettings settings;
    public PostmanGenerator(DetectionSettings settings) { this.settings = settings; }

    public String generate(List<ApiOperation> operations, boolean obfuscate) throws JsonProcessingException {
        List<ApiOperation> included = operations.stream().filter(ApiOperation::included).toList();
        ObjectNode root = json.createObjectNode();
        ObjectNode info = root.putObject("info");
        info.put("name", "Reverse API captured requests");
        info.put("schema", "https://schema.getpostman.com/json/collection/v2.1.0/collection.json");
        info.put("description", obfuscate ? "Example values obfuscated. Fill in values before sending."
                : "Captured values included, including credentials. One retained exchange per operation.");
        // Explicit headers carry authentication; avoid inheriting unrelated workspace auth.
        root.putObject("auth").put("type", "noauth");
        ArrayNode items = root.putArray("item");
        int index = 0;
        for (ApiOperation operation : included) {
            CapturedExchange sample = operation.latest();
            ObjectNode item = items.addObject();
            item.put("name", (++index) + " " + operation.method() + " " + operation.host() + " " + operation.normalizedPath());
            ObjectNode request = item.putObject("request");
            request.put("method", sample.method());
            if (!obfuscate) request.put("url", sample.url());
            else {
                String path = operation.normalizedPath().replaceAll("\\{([^}]+)}", ":$1");
                ObjectNode url = request.putObject("url");
                List<String> query = new ArrayList<>();
                sample.queryParameters().forEach((key, values) -> values.forEach(value -> query.add(URLEncoder.encode(key, StandardCharsets.UTF_8) + "=")));
                url.put("raw", sample.scheme() + "://" + sample.authority() + path + (query.isEmpty() ? "" : "?" + String.join("&", query)));
                url.put("protocol", sample.scheme()); url.put("host", sample.host());
                if (sample.port() > 0) url.put("port", String.valueOf(sample.port()));
                url.put("path", path.startsWith("/") ? path.substring(1) : path);
                ArrayNode params = url.putArray("query");
                sample.queryParameters().forEach((key, values) -> values.forEach(value -> params.addObject().put("key", key).put("value", "")));
                ArrayNode variables = url.putArray("variable");
                var matcher = java.util.regex.Pattern.compile("\\{([^}]+)}").matcher(operation.normalizedPath());
                Set<String> used = new HashSet<>();
                while (matcher.find()) if (used.add(matcher.group(1))) variables.addObject().put("key", matcher.group(1)).put("value", "");
            }
            ArrayNode headers = request.putArray("header");
            sample.requestHeaders().forEach((name, values) -> {
                // Let Postman calculate transport framing and destination headers.
                if (Set.of("host", "content-length", "connection", "transfer-encoding", "proxy-connection").contains(name.toLowerCase(Locale.ROOT))) return;
                values.forEach(value -> headers.addObject().put("key", name).put("value", obfuscate
                        ? (name.equalsIgnoreCase("Content-Type") ? sample.requestContentType() : "") : value));
            });
            if (!sample.requestBody().isEmpty()) {
                ObjectNode body = request.putObject("body"); body.put("mode", "raw");
                body.put("raw", obfuscate ? ExportExamples.obfuscatedBody(sample.requestContentType(), sample.requestBody(), settings) : sample.requestBody());
            }
            ArrayNode responses = item.putArray("response");
            if (sample.statusCode() > 0) {
                ObjectNode response = responses.addObject(); response.put("name", "Captured response");
                response.put("status", "HTTP " + sample.statusCode()); response.put("code", sample.statusCode());
                response.set("originalRequest", request.deepCopy());
                ArrayNode responseHeaders = response.putArray("header");
                sample.responseHeaders().forEach((name, values) -> values.forEach(value -> responseHeaders.addObject().put("key", name)
                        .put("value", obfuscate ? (name.equalsIgnoreCase("Content-Type") ? sample.responseContentType() : "") : value)));
                response.put("body", obfuscate ? ExportExamples.obfuscatedBody(sample.responseContentType(), sample.responseBody(), settings) : sample.responseBody());
            }
        }
        if (obfuscate) ExportPrivacy.verify(root, included, settings);
        return json.writerWithDefaultPrettyPrinter().writeValueAsString(root);
    }
}
