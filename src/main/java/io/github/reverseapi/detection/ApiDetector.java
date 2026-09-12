package io.github.reverseapi.detection;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.reverseapi.model.CapturedExchange;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class ApiDetector {
    private static final ObjectMapper JSON = io.github.reverseapi.schema.JsonSupport.mapper();
    private static final Set<String> STATIC_EXTENSIONS = Set.of(
            "css", "js", "mjs", "map", "png", "jpg", "jpeg", "gif", "svg", "ico", "webp", "avif",
            "woff", "woff2", "ttf", "otf", "eot", "mp3", "wav", "ogg", "mp4", "webm", "mov", "avi");
    private static final Pattern API_PATH = Pattern.compile("(?i)(^|/)(api|rest|graphql|v[0-9]+)(/|$)");
    private static final Pattern NOISE = Pattern.compile("(?i)(google-analytics|doubleclick|segment\\.io|/beacon|/analytics|/telemetry|/collect(?:/|$))");
    private final DetectionSettings settings;

    public ApiDetector() { this(new DetectionSettings()); }
    public ApiDetector(DetectionSettings settings) { this.settings = settings; }

    public DetectionResult detect(CapturedExchange exchange) {
        String path = exchange.path().toLowerCase(Locale.ROOT);
        String responseType = exchange.responseContentType();
        String requestType = exchange.requestContentType();
        String accept = exchange.requestHeader("Accept").toLowerCase(Locale.ROOT);

        if (matches(settings.excludeUrlRegex(), exchange.url())) return DetectionResult.ignored("Matched excluded URL pattern");
        if (!settings.allowedHostRegex().isBlank() && !matches(settings.allowedHostRegex(), exchange.authority())) {
            return DetectionResult.ignored("Host did not match allowed-host pattern");
        }
        if (matches(settings.forceIncludeUrlRegex(), exchange.url())) {
            return new DetectionResult(true, false, 100, "Matched forced-include URL pattern", true);
        }
        if (settings.suppressStatic() && isStatic(path, responseType)) return DetectionResult.ignored("Static asset/media");
        if (settings.suppressAnalytics() && NOISE.matcher(path).find()) return DetectionResult.ignored("Analytics/beacon pattern");

        int score = 0;
        StringBuilder reasons = new StringBuilder();
        if (settings.xml() && (requestType.equals("application/soap+xml") || responseType.equals("application/soap+xml")
                || !exchange.requestHeader("SOAPAction").isBlank())) { score += 40; add(reasons, "SOAP metadata (XML not parsed)"); }
        else if (settings.xml() && (requestType.equals("application/xml") || responseType.equals("application/xml")
                || requestType.endsWith("+xml") || responseType.endsWith("+xml"))) { score += 25; add(reasons, "XML media type (structure not inferred)"); }
        if (settings.jsonContentTypes() && isJsonType(responseType)) { score += 40; add(reasons, "JSON response"); }
        if (settings.jsonContentTypes() && isJsonType(requestType)) { score += 35; add(reasons, "JSON request"); }
        if (settings.jsonContentTypes() && (accept.contains("application/json") || accept.contains("+json"))) { score += 15; add(reasons, "JSON Accept"); }
        if (settings.jsonBodies() && looksLikeJson(exchange.requestBody())) { score += 20; add(reasons, "JSON request body"); }
        if (settings.jsonBodies() && looksLikeJson(exchange.responseBody())) { score += 25; add(reasons, "JSON response body"); }
        if (settings.apiPaths() && API_PATH.matcher(path).find()) { score += 25; add(reasons, "API-like path"); }
        if (settings.graphQl() && (path.contains("/graphql") || looksGraphQl(exchange))) { score += 40; add(reasons, "GraphQL"); }
        String requestedWith = exchange.requestHeader("X-Requested-With");
        if (settings.xhr() && requestedWith.equalsIgnoreCase("XMLHttpRequest")) { score += 10; add(reasons, "XMLHttpRequest"); }
        if (Set.of("POST", "PUT", "PATCH", "DELETE").contains(exchange.method().toUpperCase(Locale.ROOT))) {
            score += 5;
        }
        score = Math.min(score, 100);
        boolean meetsThreshold = score >= settings.minimumConfidence();
        boolean visible = meetsThreshold || settings.showLowConfidence();
        String reason = reasons.isEmpty() ? "No strong API indicators" : reasons.toString();
        if (!meetsThreshold && visible) reason = "Below inclusion threshold; " + reason;
        return new DetectionResult(visible, false, score, reason, meetsThreshold);
    }

    private boolean isJsonType(String type) {
        return type.equals("application/json") || type.endsWith("+json") || type.equals("application/problem+json")
                || settings.additionalJsonTypeSet().contains(type);
    }

    private static boolean looksLikeJson(String body) {
        if (body == null || body.isBlank()) return false;
        String trimmed = body.trim();
        if (!(trimmed.startsWith("{") || trimmed.startsWith("["))) return false;
        try { JSON.readTree(trimmed); return true; } catch (Exception ignored) { return false; }
    }

    private static boolean looksGraphQl(CapturedExchange exchange) {
        String body = exchange.requestBody().trim();
        return exchange.requestContentType().equals("application/graphql")
                || body.startsWith("query ") || body.startsWith("mutation ")
                || (body.startsWith("{") && body.contains("\"query\""));
    }

    private static boolean isStatic(String path, String responseType) {
        String clean = path.split("\\?", 2)[0];
        int dot = clean.lastIndexOf('.');
        if (dot >= 0 && STATIC_EXTENSIONS.contains(clean.substring(dot + 1))) return true;
        return responseType.startsWith("image/") || responseType.startsWith("font/")
                || responseType.startsWith("audio/") || responseType.startsWith("video/")
                || responseType.equals("text/css") || responseType.contains("javascript");
    }

    private static void add(StringBuilder target, String reason) {
        if (!target.isEmpty()) target.append(", ");
        target.append(reason);
    }

    private static boolean matches(String regex, String value) {
        if (regex == null || regex.isBlank()) return false;
        try { return com.google.re2j.Pattern.compile(regex, com.google.re2j.Pattern.CASE_INSENSITIVE).matcher(value).find(); }
        catch (RuntimeException ignored) { return false; }
    }
}
