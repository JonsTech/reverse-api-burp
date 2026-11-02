package io.github.reverseapi.openapi;

import io.github.reverseapi.TestExchange;
import io.github.reverseapi.detection.DetectionResult;
import io.github.reverseapi.detection.DetectionSettings;
import io.github.reverseapi.export.PostmanGenerator;
import io.github.reverseapi.model.ApiOperation;
import io.github.reverseapi.model.CapturedExchange;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class PrivacyRegressionTest {
    private ApiOperation operation(String path, String body, Map<String, List<String>> responseHeaders) {
        var base = TestExchange.exchange("POST", path, "application/json", body, 200, "application/json", "{}");
        var sample = new CapturedExchange(base.url(), base.scheme(), base.host(), base.port(), base.method(), base.path(),
                base.queryParameters(), base.requestHeaders(), base.requestBody(), base.statusCode(), responseHeaders,
                base.responseBody(), base.capturedAt());
        return new ApiOperation(sample, path, new DetectionResult(true, false, 100, "test"), false);
    }

    @Test void cookieAttributesAndCredentialMetadataDoNotBlockObfuscatedExports() throws Exception {
        var op = operation("/api/true/false/string/3600", """
                {"passwordRequired":true,"hasPassword":false,"password":false,
                 "passwordType":"string","tokenType":"string","sessionTimeout":3600,
                 "accessToken":"actual-token-value","passwordPolicy":{"minimumLength":8}}
                """, Map.of("Set-Cookie", List.of(
                        "sid=actual-session-value; Path=/api; Domain=api.example.test; SameSite=None; Secure; HttpOnly")));
        var generated = new OpenApiGenerator().generate(List.of(op), true);
        for (String output : List.of(generated.json(), generated.yaml(),
                new PostmanGenerator(new DetectionSettings()).generate(List.of(op), true))) {
            assertThat(output).doesNotContain("actual-token-value", "actual-session-value");
        }
        assertThat(op.latest().requestBody()).contains("actual-token-value");
    }

    @Test void stillRejectsActualCookieCredentialsInPaths() {
        var op = operation("/api/session-secret", "{}", Map.of("Set-Cookie", List.of("sid=session-secret; Path=/api")));
        assertThatThrownBy(() -> new OpenApiGenerator().generate(List.of(op), true)).hasMessageContaining("credential");
        assertThatThrownBy(() -> new PostmanGenerator(new DetectionSettings()).generate(List.of(op), true))
                .hasMessageContaining("credential");
    }

    @Test void credentialArraysAndNumericCredentialsRemainProtected() {
        for (String body : List.of("{\"credentials\":[\"private-key\"]}", "{\"password\":12345}")) {
            var op = operation(body.contains("12345") ? "/api/12345" : "/api/private-key", body, Map.of());
            assertThatThrownBy(() -> new OpenApiGenerator().generate(List.of(op), true)).hasMessageContaining("credential");
        }
    }

    @Test void absentBearerTokensDoNotTreatNullPathSegmentsAsCredentials() throws Exception {
        for (String token : List.of("null", "undefined")) {
            var base = TestExchange.exchange("GET", "/api/client/null/undefined", "", "", 200, "application/json", "{}");
            var sample = new CapturedExchange(base.url(), base.scheme(), base.host(), base.port(), base.method(), base.path(),
                    base.queryParameters(), Map.of("Authorization", List.of("Bearer " + token)), base.requestBody(),
                    base.statusCode(), base.responseHeaders(), base.responseBody(), base.capturedAt());
            var op = new ApiOperation(sample, base.path(), new DetectionResult(true, false, 100, "test"), false);
            assertThat(new OpenApiGenerator().generate(List.of(op), true).json()).contains("/api/client/null/undefined");
            assertThat(new PostmanGenerator(new DetectionSettings()).generate(List.of(op), true))
                    .doesNotContain("Bearer " + token);
        }
    }
}
