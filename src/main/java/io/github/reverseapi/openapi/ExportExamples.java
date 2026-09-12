package io.github.reverseapi.openapi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
import io.github.reverseapi.detection.DetectionSettings;
import io.github.reverseapi.model.ApiOperation;
import io.github.reverseapi.model.CapturedExchange;
import io.github.reverseapi.schema.JsonSupport;
import java.util.*;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/** Examples are export-only copies: no transformation touches captured samples. */
public final class ExportExamples {
    private static final ObjectMapper JSON = JsonSupport.mapper();
    private ExportExamples() { }

    static void add(ObjectNode root, List<ApiOperation> operations, boolean obfuscate, DetectionSettings settings) {
        root.put("x-reverseapi-examples", obfuscate ? "obfuscated" : "captured-values-including-credentials");
        for (ApiOperation operation : operations) {
            ObjectNode op = (ObjectNode) root.path("paths").path(operation.normalizedPath()).path(operation.method().toLowerCase(Locale.ROOT));
            CapturedExchange sample = operation.latest();
            for (JsonNode parameter : op.path("parameters")) {
                String name = parameter.path("name").asText();
                if (parameter.path("in").asText().equals("query") && !sample.queryParameters().containsKey(name)) continue;
                String value = switch (parameter.path("in").asText()) {
                    case "query" -> sample.queryParameters().getOrDefault(name, List.of("")).get(0);
                    case "header" -> sample.requestHeader(name);
                    case "path" -> pathValue(operation.normalizedPath(), sample.path(), name);
                    default -> "";
                };
                String type = parameter.path("schema").path("type").asText();
                if (value.isEmpty() && !obfuscate && !type.equals("string")) continue;
                ((ObjectNode) parameter).set("example", scalar(value, type, obfuscate));
            }
            for (CapturedExchange observed : operation.samples()) {
                bodyExample(op.path("requestBody").path("content"), observed.requestContentType(), observed.requestBody(), obfuscate, settings);
                String status = observed.statusCode() >= 100 && observed.statusCode() <= 599 ? String.valueOf(observed.statusCode()) : "default";
                bodyExample(op.path("responses").path(status).path("content"), observed.responseContentType(), observed.responseBody(), obfuscate, settings);
            }
            if (!obfuscate) {
                // OAS ignores Authorization header parameters. Preserve the captured exchange in
                // an extension rather than pretending an importer will replay authentication.
                ObjectNode captured = op.putObject("x-captured-exchange");
                captured.put("url", sample.url()); captured.put("method", sample.method());
                captured.set("requestHeaders", JSON.valueToTree(sample.requestHeaders()));
                captured.put("requestBody", sample.requestBody());
                captured.put("status", sample.statusCode());
                captured.set("responseHeaders", JSON.valueToTree(sample.responseHeaders()));
                captured.put("responseBody", sample.responseBody());
            }
        }
    }

    private static String pathValue(String template, String path, String name) {
        String[] expected = template.split("/", -1), actual = path.split("/", -1);
        for (int i = 0; i < Math.min(expected.length, actual.length); i++) {
            if (expected[i].equals("{" + name + "}")) return actual[i];
        }
        return "";
    }

    private static JsonNode scalar(String value, String type, boolean obfuscate) {
        try {
            return switch (type) {
                case "integer" -> BigIntegerNode.valueOf(obfuscate ? java.math.BigInteger.ZERO : new java.math.BigInteger(value));
                case "number" -> DecimalNode.valueOf(obfuscate ? java.math.BigDecimal.ZERO : new java.math.BigDecimal(value));
                case "boolean" -> BooleanNode.valueOf(!obfuscate && Boolean.parseBoolean(value));
                default -> TextNode.valueOf(obfuscate ? "" : value);
            };
        } catch (NumberFormatException ignored) { return TextNode.valueOf(obfuscate ? "" : value); }
    }

    private static void bodyExample(JsonNode content, String type, String body, boolean obfuscate, DetectionSettings settings) {
        if (body.isBlank()) return;
        if (type.isBlank()) type = "application/octet-stream";
        if (!(content.path(type) instanceof ObjectNode media)) return;
        JsonNode example;
        if (type.equals("application/json") || type.endsWith("+json") || settings.additionalJsonTypeSet().contains(type)) {
            try { example = JSON.readTree(body); }
            catch (Exception malformed) { example = TextNode.valueOf(body); }
        } else if (type.equals("application/x-www-form-urlencoded")) {
            ObjectNode form = JSON.createObjectNode();
            for (String pair : body.split("&")) {
                String[] parts = pair.split("=", 2);
                try {
                    String name = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
                    String value = parts.length == 2 ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : "";
                    form.set(name, scalar(value, media.path("schema").path("properties").path(name).path("type").asText(), obfuscate));
                }
                catch (IllegalArgumentException malformed) { }
            }
            example = form;
        } else example = TextNode.valueOf(body);
        media.set("example", obfuscate ? blank(example) : example);
    }

    public static String obfuscatedBody(String type, String body, DetectionSettings settings) throws com.fasterxml.jackson.core.JsonProcessingException {
        if (body.isBlank()) return body;
        if (type.equals("application/x-www-form-urlencoded")) {
            List<String> fields = new ArrayList<>();
            for (String pair : body.split("&")) {
                try { fields.add(java.net.URLEncoder.encode(URLDecoder.decode(pair.split("=", 2)[0], StandardCharsets.UTF_8), StandardCharsets.UTF_8) + "="); }
                catch (IllegalArgumentException malformed) { }
            }
            return String.join("&", fields);
        }
        ObjectNode content = JSON.createObjectNode();
        content.putObject(type.isBlank() ? "application/octet-stream" : type);
        bodyExample(content, type, body, true, settings);
        JsonNode example = content.elements().next().path("example");
        return example.isTextual() ? example.asText() : JSON.writeValueAsString(example);
    }

    private static JsonNode blank(JsonNode value) {
        if (value == null || value.isNull()) return NullNode.instance;
        if (value.isObject()) {
            ObjectNode result = JSON.createObjectNode();
            value.fields().forEachRemaining(field -> result.set(field.getKey(), blank(field.getValue())));
            return result;
        }
        if (value.isArray()) {
            ArrayNode result = JSON.createArrayNode(); value.forEach(child -> result.add(blank(child))); return result;
        }
        if (value.isBoolean()) return BooleanNode.FALSE;
        if (value.isNumber()) return IntNode.valueOf(0);
        return TextNode.valueOf("");
    }
}
