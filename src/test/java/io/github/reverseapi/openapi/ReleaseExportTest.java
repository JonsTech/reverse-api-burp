package io.github.reverseapi.openapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.reverseapi.TestExchange;
import io.github.reverseapi.detection.*;
import io.github.reverseapi.model.*;
import io.github.reverseapi.normalization.PathNormalizer;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class ReleaseExportTest {
    private final ObjectMapper json = new ObjectMapper();
    private ApiOperation operation(CapturedExchange exchange) {
        return new ApiOperation(exchange, new PathNormalizer().normalize(exchange.path()), new DetectionResult(true, false, 80, "test"), false);
    }
    private CapturedExchange headers(CapturedExchange base, Map<String, List<String>> headers) {
        Map<String, List<String>> merged = new LinkedHashMap<>(base.requestHeaders()); merged.putAll(headers);
        return new CapturedExchange(base.url(), base.scheme(), base.host(), base.port(), base.method(), base.path(),
                base.queryParameters(), merged, base.requestBody(), base.statusCode(), base.responseHeaders(), base.responseBody(), base.capturedAt());
    }
    @Test void omitsAllCredentialValuesInBothFormats() throws Exception {
        var sample = headers(TestExchange.exchange("POST", "/api/login?api_key=query-secret", "application/json",
                "{\"password\":\"body-password\",\"nested\":{\"access_token\":\"nested-secret\"},\"ordinary\":\"also-private\"}",
                200, "application/json", "{\"session\":\"response-secret\"}"),
                Map.of("Authorization", List.of("Basic dXNlcjpwYXNz"), "Cookie", List.of("sid=cookie-secret"),
                        "X-Auth-Token", List.of("header-secret"), "X-Custom", List.of("unrecognized-secret")));
        var generated = new OpenApiGenerator().generate(List.of(operation(sample)));
        for (String text : List.of(generated.json(), generated.yaml())) assertThat(text).doesNotContain(
                "query-secret", "body-password", "nested-secret", "also-private", "response-secret", "dXNlcjpwYXNz", "cookie-secret", "header-secret", "unrecognized-secret");
    }
    @Test void refusesCredentialReflectedIntoPropertyNameOrPath() {
        var sample = headers(TestExchange.exchange("GET", "/api/header-secret", "", "", 200, "application/json", "{}"),
                Map.of("X-Auth-Token", List.of("header-secret")));
        assertThatThrownBy(() -> new OpenApiGenerator().generate(List.of(operation(sample)))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("credential");
        var body = TestExchange.exchange("GET", "/api", "", "", 200, "application/json", "{\"password\":\"reflected-secret\",\"reflected-secret\":true}");
        assertThatThrownBy(() -> new OpenApiGenerator().generate(List.of(operation(body)))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void supportsMultipleNamedApiKeysWithoutSchemeOverwrite() throws Exception {
        var settings = new DetectionSettings(); settings.apiKeyHeaders("Vendor-Credential");
        var sample = headers(TestExchange.exchange("GET", "/api", "", "", 200, "", ""),
                Map.of("X-Auth-Token", List.of("token-one"), "Vendor-Credential", List.of("token-two"), "API-Version", List.of("2026")));
        var root = json.readTree(new OpenApiGenerator(settings).generate(List.of(operation(sample))).json());
        assertThat(root.at("/components/securitySchemes").size()).isEqualTo(2);
        assertThat(root.at("/paths/~1api/get/x-observed-security").size()).isEqualTo(2);
        assertThat(root.at("/paths/~1api/get/parameters").toString()).contains("API-Version").doesNotContain("X-Auth-Token", "Vendor-Credential");
    }
    @Test void rejectsCollisionsInsteadOfOverwriting() {
        var first = operation(TestExchange.exchange("GET", "/api/a", "", "", 200, "", ""));
        var second = operation(TestExchange.exchange("GET", "/api/b", "", "", 200, "", "")); second.normalizedPath("/api/a");
        assertThatThrownBy(() -> new OpenApiGenerator().generate(List.of(first, second)))
                .hasMessageContaining("same OpenAPI path and method", "GET https://api.example.test/api/a", "Exclude one row");
    }
    @Test void crossServerCollisionDirectsUserToGenerationFilter() {
        var first = operation(TestExchange.exchange("GET", "/api/me", "", "", 200, "", ""));
        var base = TestExchange.exchange("GET", "/api/me", "", "", 200, "", "");
        var dev = new CapturedExchange("https://dev.example.test/api/me", "https", "dev.example.test", 443,
                base.method(), base.path(), base.queryParameters(), base.requestHeaders(), base.requestBody(),
                base.statusCode(), base.responseHeaders(), base.responseBody(), base.capturedAt());
        assertThatThrownBy(() -> new OpenApiGenerator().generate(List.of(first, operation(dev))))
                .hasMessageContaining("api.example.test", "dev.example.test", "single server in Generate for");
    }
    @Test void rejectsEquivalentTemplatesAndInvalidMethods() {
        var first = operation(TestExchange.exchange("GET", "/api/1", "", "", 200, "", ""));
        var second = operation(TestExchange.exchange("POST", "/api/2", "", "", 200, "", "")); second.normalizedPath("/api/{other}");
        assertThatThrownBy(() -> new OpenApiGenerator().generate(List.of(first, second)))
                .hasMessageContaining("Equivalent path templates", "/api/{id}", "/api/{other}", "same parameter names");
        var connect = operation(TestExchange.exchange("CONNECT", "/api", "", "", 0, "", ""));
        assertThatThrownBy(() -> new OpenApiGenerator().generate(List.of(connect))).hasMessageContaining("Unsupported");
    }
    @Test void rejectsMalformedAndRepeatedPathParameters() {
        var op = operation(TestExchange.exchange("GET", "/api", "", "", 200, "", ""));
        for (String path : List.of("/api/{", "/api?token=x", "/api/{id}/{id}")) {
            op.normalizedPath(path);
            assertThatThrownBy(() -> new OpenApiGenerator().generate(List.of(op))).isInstanceOf(IllegalArgumentException.class);
        }
    }
    @Test void missingResponseUsesDefaultAndOpaqueBodiesStayUnconstrained() throws Exception {
        var op = operation(TestExchange.exchange("POST", "/api", "multipart/form-data", "opaque-private-content", 0, "", ""));
        var root = json.readTree(new OpenApiGenerator().generate(List.of(op)).json());
        assertThat(root.at("/paths/~1api/post/responses/default/description").asText()).isEqualTo("No response observed");
        assertThat(root.at("/paths/~1api/post/requestBody/content/multipart~1form-data/schema").isEmpty()).isTrue();
        assertThat(root.toString()).doesNotContain("opaque-private-content", "\"0\":");
    }
    @Test void malformedFormDoesNotAbortExport() throws Exception {
        var op = operation(TestExchange.exchange("POST", "/api", "application/x-www-form-urlencoded", "bad=%zz&password=form-secret&ok=1", 200, "", ""));
        var output = new OpenApiGenerator().generate(List.of(op));
        assertThat(output.json()).contains("password", "ok").doesNotContain("form-secret");
    }
    @Test void mediaTypesHaveSeparateComponentSchemasAndLocalServers() throws Exception {
        var op = operation(TestExchange.exchange("POST", "/api", "application/json", "{\"a\":1}", 200, "application/json", "{\"a\":1}"));
        op.observe(TestExchange.exchange("POST", "/api", "application/problem+json", "{\"b\":true}", 200, "application/problem+json", "{\"b\":true}"), new DetectionResult(true, false, 80, "test"));
        var root = json.readTree(new OpenApiGenerator().generate(List.of(op)).json());
        assertThat(root.at("/components/schemas").size()).isEqualTo(4);
        assertThat(root.at("/paths/~1api/post/servers/0/url").asText()).isEqualTo("https://api.example.test");
        root.findValues("$ref").forEach(ref -> assertThat(root.at(ref.asText().substring(1)).isMissingNode()).isFalse());
    }
    @Test void xmlExternalEntitiesAreNeverReadOrExported() throws Exception {
        var op = operation(TestExchange.exchange("POST", "/soap", "application/soap+xml", "<!DOCTYPE x [<!ENTITY xxe SYSTEM 'file:///private'>]><x>&xxe;</x>", 200, "text/xml", "<secret>xml-secret</secret>"));
        assertThat(new OpenApiGenerator().generate(List.of(op)).json()).doesNotContain("file:///private", "xxe", "xml-secret");
    }
    @Test void yamlAndJsonRepresentTheSameDocument() throws Exception {
        var output = new OpenApiGenerator().generate(List.of(operation(TestExchange.exchange("GET", "/api/123", "", "", 200, "application/json", "{\"enabled\":true}"))));
        var yaml = new ObjectMapper(new com.fasterxml.jackson.dataformat.yaml.YAMLFactory());
        assertThat(yaml.readTree(output.yaml())).isEqualTo(json.readTree(output.json()));
    }
}
