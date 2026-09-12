package io.github.reverseapi.openapi;

import io.github.reverseapi.TestExchange;
import io.github.reverseapi.detection.DetectionResult;
import io.github.reverseapi.model.EndpointDeduplicator;
import io.github.reverseapi.normalization.PathNormalizer;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiGeneratorTest {
    @Test void generatesOpenApi31WithoutCapturedSecrets() throws Exception {
        var base = TestExchange.exchange("POST", "/api/users/123?verbose=true", "application/json", "{\"name\":\"Ada\"}",
                201, "application/json", "{\"id\":123}");
        var exchange = new io.github.reverseapi.model.CapturedExchange(base.url(), base.scheme(), base.host(), base.port(), base.method(),
                base.path(), base.queryParameters(), Map.of("Content-Type", List.of("application/json"),
                "Authorization", List.of("Bearer super-secret-token")), base.requestBody(), base.statusCode(),
                base.responseHeaders(), base.responseBody(), Instant.now());
        EndpointDeduplicator deduplicator = new EndpointDeduplicator(new PathNormalizer());
        deduplicator.add(exchange, new DetectionResult(true, false, 100, "test"), false);

        GeneratedOpenApi generated = new OpenApiGenerator().generate(deduplicator.operations());

        assertThat(generated.json()).contains("\"openapi\" : \"3.1.0\"", "/api/users/{id}", "bearerAuth", "verbose");
        assertThat(generated.json()).doesNotContain("super-secret-token");
    }
}

