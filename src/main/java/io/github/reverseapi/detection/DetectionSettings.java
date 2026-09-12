package io.github.reverseapi.detection;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

public final class DetectionSettings {
    private volatile int minimumConfidence = 25;
    private volatile boolean jsonContentTypes = true;
    private volatile boolean jsonBodies = true;
    private volatile boolean apiPaths = true;
    private volatile boolean graphQl = true;
    private volatile boolean xml = true;
    private volatile boolean xhr = true;
    private volatile boolean suppressStatic = true;
    private volatile boolean suppressAnalytics = true;
    private volatile boolean showLowConfidence;
    private volatile String allowedHostRegex = "";
    private volatile String forceIncludeUrlRegex = "";
    private volatile String excludeUrlRegex = "";
    private volatile String apiKeyHeaders = "";
    public String apiKeyHeaders() { return apiKeyHeaders; }
    public void apiKeyHeaders(String value) { apiKeyHeaders = clean(value); }
    private volatile String additionalJsonTypes = "";

    public int minimumConfidence() { return minimumConfidence; }
    public void minimumConfidence(int value) { minimumConfidence = Math.max(0, Math.min(100, value)); }
    public boolean jsonContentTypes() { return jsonContentTypes; }
    public void jsonContentTypes(boolean value) { jsonContentTypes = value; }
    public boolean jsonBodies() { return jsonBodies; }
    public void jsonBodies(boolean value) { jsonBodies = value; }
    public boolean apiPaths() { return apiPaths; }
    public void apiPaths(boolean value) { apiPaths = value; }
    public boolean graphQl() { return graphQl; }
    public void graphQl(boolean value) { graphQl = value; }
    public boolean xml() { return xml; }
    public void xml(boolean value) { xml = value; }
    public boolean xhr() { return xhr; }
    public void xhr(boolean value) { xhr = value; }
    public boolean suppressStatic() { return suppressStatic; }
    public void suppressStatic(boolean value) { suppressStatic = value; }
    public boolean suppressAnalytics() { return suppressAnalytics; }
    public void suppressAnalytics(boolean value) { suppressAnalytics = value; }
    public boolean showLowConfidence() { return showLowConfidence; }
    public void showLowConfidence(boolean value) { showLowConfidence = value; }
    public String allowedHostRegex() { return allowedHostRegex; }
    public void allowedHostRegex(String value) { allowedHostRegex = clean(value); }
    public String forceIncludeUrlRegex() { return forceIncludeUrlRegex; }
    public void forceIncludeUrlRegex(String value) { forceIncludeUrlRegex = clean(value); }
    public String excludeUrlRegex() { return excludeUrlRegex; }
    public void excludeUrlRegex(String value) { excludeUrlRegex = clean(value); }
    public String additionalJsonTypes() { return additionalJsonTypes; }
    public void additionalJsonTypes(String value) { additionalJsonTypes = clean(value); }

    public Set<String> additionalJsonTypeSet() {
        Set<String> result = new LinkedHashSet<>();
        Arrays.stream(additionalJsonTypes.split("[,\\r\\n]+"))
                .map(String::trim).map(value -> value.toLowerCase(Locale.ROOT)).filter(value -> !value.isBlank()).forEach(result::add);
        return result;
    }

    public void reset() {
        apiKeyHeaders = ""; minimumConfidence = 25; jsonContentTypes = true; jsonBodies = true; apiPaths = true; graphQl = true; xml = true; xhr = true;
        suppressStatic = true; suppressAnalytics = true; showLowConfidence = false;
        allowedHostRegex = ""; forceIncludeUrlRegex = ""; excludeUrlRegex = ""; additionalJsonTypes = "";
    }

    private static String clean(String value) { return value == null ? "" : value.trim(); }
}
