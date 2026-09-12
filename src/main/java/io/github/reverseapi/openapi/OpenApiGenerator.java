package io.github.reverseapi.openapi;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.github.reverseapi.model.ApiOperation;
import io.github.reverseapi.model.CapturedExchange;
import io.github.reverseapi.schema.JsonSchemaInferer;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class OpenApiGenerator {
    private static final Set<String> EXCLUDED_HEADERS = Set.of(
            "user-agent", "content-length", "connection", "host", "cookie", "authorization",
            "accept-encoding", "accept-language", "origin", "referer", "cache-control", "pragma");
    private static final Pattern PATH_PARAMETER = Pattern.compile("\\{([^}]+)}");
    private final io.github.reverseapi.detection.DetectionSettings settings;
    public OpenApiGenerator() { this(new io.github.reverseapi.detection.DetectionSettings()); }
    public OpenApiGenerator(io.github.reverseapi.detection.DetectionSettings settings) { this.settings = settings; }
    private boolean apiKeyHeader(String name) {
        return name.matches("(?i)(?:(?:x-)?api[-_]?key|(?:ocp-apim-)?subscription-key|x-auth-token|x-access-token|x-csrf-token|x-xsrf-token)")
                || java.util.Arrays.stream(settings.apiKeyHeaders().split(",")).anyMatch(h -> h.trim().equalsIgnoreCase(name));
    }
    private final ObjectMapper json = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private final ObjectMapper yaml = new ObjectMapper(new YAMLFactory());
    private final JsonSchemaInferer schemas = new JsonSchemaInferer(json);

    public GeneratedOpenApi generate(List<ApiOperation> allOperations) throws JsonProcessingException {
        return generate(allOperations, true);
    }

    public GeneratedOpenApi generate(List<ApiOperation> allOperations, boolean obfuscateExamples) throws JsonProcessingException {
        List<ApiOperation> operations = allOperations.stream().filter(ApiOperation::included)
                .sorted(Comparator.comparing((ApiOperation operation) -> operation.normalizedPath())
                        .thenComparing(operation -> operation.method())).toList();
        Map<String, ApiOperation> slots = new LinkedHashMap<>();
        Map<String, ApiOperation> templates = new LinkedHashMap<>();
        for (ApiOperation operation : operations) {
            String path = operation.normalizedPath();
            if (!path.startsWith("/") || path.matches(".*[?#\\s].*")
                    || path.replaceAll("\\{[A-Za-z_][A-Za-z0-9_]*}", "").matches(".*[{}].*"))
                throw new IllegalArgumentException("Invalid normalized path; use /paths/{parameter} without query or fragment");
            if (!Set.of("GET", "PUT", "POST", "DELETE", "OPTIONS", "HEAD", "PATCH", "TRACE").contains(operation.method()))
                throw new IllegalArgumentException("Unsupported HTTP method in included operation");
            String shape = path.replaceAll("\\{[^}]+}", "{}");
            ApiOperation previousTemplate = templates.putIfAbsent(shape, operation);
            if (previousTemplate != null && !previousTemplate.normalizedPath().equals(path)) {
                throw new IllegalArgumentException("Equivalent path templates use different parameter names: "
                        + endpoint(previousTemplate) + " conflicts with " + endpoint(operation)
                        + (sameServer(previousTemplate, operation)
                        ? ". Use the same parameter names in both Normalized path values; if their methods are also the same, exclude one row."
                        : ". Select a single server in Generate for, or use the same parameter names in both Normalized path values."));
            }
            ApiOperation previousSlot = slots.putIfAbsent(operation.method() + " " + path, operation);
            if (previousSlot != null) {
                throw new IllegalArgumentException("The same OpenAPI path and method is included more than once: "
                        + endpoint(previousSlot) + " conflicts with " + endpoint(operation)
                        + (sameServer(previousSlot, operation)
                        ? ". Exclude one row or change one Normalized path."
                        : ". Select a single server in Generate for, or exclude one row."));
            }
        }
        ObjectNode root = json.createObjectNode();
        root.put("openapi", "3.1.0");
        ObjectNode info = root.putObject("info");
        info.put("title", "Reverse API captured API"); info.put("version", "1.0.0");
        ArrayNode servers = root.putArray("servers");
        Set<String> serverUrls = new LinkedHashSet<>();
        for (ApiOperation operation : operations) {
            CapturedExchange sample = operation.latest();
            serverUrls.add(sample.scheme() + "://" + sample.authority());
        }
        serverUrls.forEach(url -> servers.addObject().put("url", url));
        ObjectNode paths = root.putObject("paths");
        ObjectNode components = root.putObject("components");
        ObjectNode componentSchemas = components.putObject("schemas");
        ObjectNode securitySchemes = components.putObject("securitySchemes");
        Set<String> operationIds = new LinkedHashSet<>();

        for (ApiOperation operation : operations) {
            JsonNode existingPath = paths.get(operation.normalizedPath());
            ObjectNode pathItem = existingPath instanceof ObjectNode object ? object : paths.putObject(operation.normalizedPath());
            ObjectNode op = pathItem.putObject(operation.method().toLowerCase(Locale.ROOT));
            String operationId = uniqueOperationId(operation, operationIds);
            op.put("operationId", operationId);
            op.putArray("servers").addObject().put("url", operation.latest().scheme() + "://" + operation.latest().authority());
            op.put("description", "Inferred from observed traffic; authentication and field requirements are not verified.");
            op.put("summary", operation.method() + " " + operation.normalizedPath());
            ArrayNode parameters = op.putArray("parameters");
            addPathParameters(parameters, operation);
            addQueryParameters(parameters, operation);
            addHeaderParameters(parameters, operation);
            addRequestBody(op, componentSchemas, operation, operationId);
            addResponses(op, componentSchemas, operation, operationId);
            addSecurity(op, securitySchemes, operation);
            if (parameters.isEmpty()) op.remove("parameters");
        }
        if (componentSchemas.isEmpty()) components.remove("schemas");
        if (securitySchemes.isEmpty()) components.remove("securitySchemes");
        if (components.isEmpty()) root.remove("components");
        ExportExamples.add(root, operations, obfuscateExamples, settings);
        if (obfuscateExamples) ExportPrivacy.verify(root, operations, settings);
        return new GeneratedOpenApi(yaml.writeValueAsString(root), json.writeValueAsString(root));
    }

    private void addPathParameters(ArrayNode parameters, ApiOperation operation) {
        Matcher matcher = PATH_PARAMETER.matcher(operation.normalizedPath());
        Set<String> names = new LinkedHashSet<>();
        while (matcher.find()) {
            String name = matcher.group(1);
            if (!names.add(name)) throw new IllegalArgumentException("Path parameter names must be unique within a path");
            ObjectNode p = parameters.addObject();
            p.put("name", name); p.put("in", "path"); p.put("required", true);
            String exampleSegment = correspondingSegment(operation.normalizedPath(), operation.originalExamplePath(), matcher.start());
            p.putObject("schema").put("type", exampleSegment.matches("[0-9]+") ? "integer" : "string");
        }
    }

    private static String correspondingSegment(String normalized, String original, int bracePosition) {
        int segment = normalized.substring(0, bracePosition).split("/", -1).length - 1;
        String[] actual = original.split("\\?", 2)[0].split("/", -1);
        return segment < actual.length ? actual[segment] : "";
    }

    private void addQueryParameters(ArrayNode parameters, ApiOperation operation) {
        Map<String, List<String>> values = new TreeMap<>();
        operation.samples().forEach(s -> s.queryParameters().forEach((k, v) -> values.computeIfAbsent(k, ignored -> new ArrayList<>()).addAll(v)));
        values.forEach((name, observed) -> {
            ObjectNode p = parameters.addObject(); p.put("name", name); p.put("in", "query"); p.put("required", false);
            p.set("schema", scalarSchema(observed));
        });
    }

    private void addHeaderParameters(ArrayNode parameters, ApiOperation operation) {
        Map<String, List<String>> useful = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        operation.samples().forEach(sample -> sample.requestHeaders().forEach((name, values) -> {
            String lower = name.toLowerCase(Locale.ROOT);
            if (!apiKeyHeader(name) && !EXCLUDED_HEADERS.contains(lower) && !lower.startsWith("sec-ch-") && !lower.startsWith("proxy-")
                    && (lower.startsWith("x-") || lower.equals("idempotency-key") || lower.equals("api-version"))) {
                useful.computeIfAbsent(name, ignored -> new ArrayList<>()).addAll(values);
            }
        }));
        useful.forEach((name, values) -> {
            ObjectNode p = parameters.addObject(); p.put("name", name); p.put("in", "header"); p.put("required", false);
            p.set("schema", scalarSchema(values));
        });
    }

    private ObjectNode scalarSchema(List<String> values) {
        ObjectNode schema = json.createObjectNode();
        boolean integers = !values.isEmpty() && values.stream().allMatch(v -> v.matches("-?[0-9]+"));
        boolean numbers = !values.isEmpty() && values.stream().allMatch(v -> v.matches("-?[0-9]+(?:\\.[0-9]+)?"));
        boolean booleans = !values.isEmpty() && values.stream().allMatch(v -> v.equalsIgnoreCase("true") || v.equalsIgnoreCase("false"));
        schema.put("type", integers ? "integer" : numbers ? "number" : booleans ? "boolean" : "string");
        return schema;
    }

    private void addRequestBody(ObjectNode op, ObjectNode componentSchemas, ApiOperation operation, String operationId) {
        Map<String, List<String>> bodies = new LinkedHashMap<>();
        for (CapturedExchange sample : operation.samples()) {
            if (!sample.requestBody().isBlank()) bodies.computeIfAbsent(defaultType(sample.requestContentType()), ignored -> new ArrayList<>()).add(sample.requestBody());
        }
        if (bodies.isEmpty()) return;
        ObjectNode requestBody = op.putObject("requestBody"); requestBody.put("required", false);
        ObjectNode content = requestBody.putObject("content");
        bodies.forEach((type, payloads) -> {
            ObjectNode media = content.putObject(type);
            if (isJson(type)) {
                ObjectNode inferred = schemas.inferAll(payloads);
                if (inferred != null) {
                    String name = schemaName(operationId + "Request_" + java.util.HexFormat.of().formatHex(type.getBytes(StandardCharsets.UTF_8))); componentSchemas.set(name, inferred);
                    media.putObject("schema").put("$ref", "#/components/schemas/" + name);
                }
            } else if (type.equals("application/x-www-form-urlencoded")) {
                ObjectNode schema = media.putObject("schema"); schema.put("type", "object"); ObjectNode props = schema.putObject("properties");
                for (String payload : payloads) parseForm(payload).forEach((k, v) -> props.set(k, scalarSchema(v)));
            } else media.putObject("schema");
        });
    }

    private void addResponses(ObjectNode op, ObjectNode componentSchemas, ApiOperation operation, String operationId) {
        ObjectNode responses = op.putObject("responses");
        Map<Integer, List<CapturedExchange>> byStatus = new TreeMap<>();
        operation.samples().forEach(s -> byStatus.computeIfAbsent(s.statusCode(), ignored -> new ArrayList<>()).add(s));
        byStatus.forEach((status, samples) -> {
            ObjectNode response = responses.putObject(status >= 100 && status <= 599 ? String.valueOf(status) : "default"); response.put("description", status == 0 ? "No response observed" : statusDescription(status));
            Map<String, List<String>> bodies = new LinkedHashMap<>();
            samples.forEach(s -> { if (!s.responseBody().isBlank()) bodies.computeIfAbsent(defaultType(s.responseContentType()), ignored -> new ArrayList<>()).add(s.responseBody()); });
            if (!bodies.isEmpty()) {
                ObjectNode content = response.putObject("content");
                bodies.forEach((type, payloads) -> {
                    ObjectNode media = content.putObject(type);
                    if (isJson(type)) {
                        ObjectNode inferred = schemas.inferAll(payloads);
                        if (inferred != null) {
                            String name = schemaName(operationId + "Response" + status + "_" + java.util.HexFormat.of().formatHex(type.getBytes(StandardCharsets.UTF_8))); componentSchemas.set(name, inferred);
                            media.putObject("schema").put("$ref", "#/components/schemas/" + name);
                        }
                    } else media.putObject("schema");
                });
            }
        });
        if (responses.isEmpty()) responses.putObject("default").put("description", "Response");
    }

    private void addSecurity(ObjectNode op, ObjectNode schemes, ApiOperation operation) {
        Set<String> detected = new LinkedHashSet<>();
        for (CapturedExchange sample : operation.samples()) {
            String auth = sample.requestHeader("Authorization");
            if (auth.regionMatches(true, 0, "Bearer ", 0, 7)) {
                ObjectNode scheme = schemes.putObject("bearerAuth"); scheme.put("type", "http"); scheme.put("scheme", "bearer"); detected.add("bearerAuth");
            } else if (auth.regionMatches(true, 0, "Basic ", 0, 6)) {
                ObjectNode scheme = schemes.putObject("basicAuth"); scheme.put("type", "http"); scheme.put("scheme", "basic"); detected.add("basicAuth");
            }
            sample.requestHeaders().keySet().stream().filter(this::apiKeyHeader)
                    .forEach(header -> {
                        String name = "apiKey_" + java.util.HexFormat.of().formatHex(header.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
                        ObjectNode scheme = schemes.putObject(name); scheme.put("type", "apiKey");
                        scheme.put("in", "header"); scheme.put("name", header); detected.add(name);
                    });
            // A Cookie header alone does not establish which cookie authenticates a session.
        }
        if (!detected.isEmpty()) {
            ArrayNode security = op.putArray("x-observed-security");
            for (String name : detected) security.addObject().putArray(name);
        }
    }

    private static String uniqueOperationId(ApiOperation operation, Set<String> used) {
        String base = (operation.method().toLowerCase(Locale.ROOT) + "_" + operation.normalizedPath())
                .replaceAll("\\{([^}]+)}", "$1").replaceAll("[^A-Za-z0-9]+", "_").replaceAll("^_|_$", "");
        if (base.isBlank()) base = "operation";
        String candidate = base; int suffix = 2;
        while (!used.add(candidate)) candidate = base + "_" + suffix++;
        return candidate;
    }

    private static String endpoint(ApiOperation operation) {
        CapturedExchange sample = operation.latest();
        return operation.method() + " " + sample.scheme() + "://" + sample.authority() + operation.normalizedPath();
    }

    private static boolean sameServer(ApiOperation first, ApiOperation second) {
        CapturedExchange a = first.latest(), b = second.latest();
        return a.scheme().equalsIgnoreCase(b.scheme()) && a.authority().equalsIgnoreCase(b.authority());
    }

    private static String schemaName(String value) { return value.replaceAll("[^A-Za-z0-9._-]", "_"); }
    private static String defaultType(String type) { return type == null || type.isBlank() ? "application/octet-stream" : type; }
    private boolean isJson(String type) { return type.equals("application/json") || type.endsWith("+json") || settings.additionalJsonTypeSet().contains(type); }
    private static String statusDescription(int status) {
        return switch (status) { case 200 -> "OK"; case 201 -> "Created"; case 204 -> "No Content"; case 400 -> "Bad Request"; case 401 -> "Unauthorized"; case 403 -> "Forbidden"; case 404 -> "Not Found"; case 500 -> "Internal Server Error"; default -> "HTTP " + status + " response"; };
    }

    private static Map<String, List<String>> parseForm(String body) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (String pair : body.split("&")) {
            String[] bits = pair.split("=", 2);
            String key; String value;
            try {
            key = java.net.URLDecoder.decode(bits[0], StandardCharsets.UTF_8);
            value = bits.length > 1 ? java.net.URLDecoder.decode(bits[1], StandardCharsets.UTF_8) : "";
            } catch (IllegalArgumentException malformed) { continue; }
            result.computeIfAbsent(key, ignored -> new ArrayList<>()).add(value);
        }
        return result;
    }
}
