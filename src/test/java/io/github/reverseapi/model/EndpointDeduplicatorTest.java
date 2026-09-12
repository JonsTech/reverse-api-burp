package io.github.reverseapi.model;

import io.github.reverseapi.TestExchange;
import io.github.reverseapi.detection.DetectionResult;
import io.github.reverseapi.normalization.PathNormalizer;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class EndpointDeduplicatorTest {
    @Test void groupsByHostMethodAndNormalizedPath() {
        EndpointDeduplicator deduplicator = new EndpointDeduplicator(new PathNormalizer());
        DetectionResult detection = new DetectionResult(true, false, 80, "test");
        deduplicator.add(TestExchange.exchange("GET", "/users/123", "", "", 200, "application/json", "{}"), detection, false);
        deduplicator.add(TestExchange.exchange("GET", "/users/456", "", "", 200, "application/json", "{}"), detection, false);
        deduplicator.add(TestExchange.exchange("DELETE", "/users/456", "", "", 204, "", ""), detection, false);
        assertThat(deduplicator.operations()).hasSize(2);
        assertThat(deduplicator.operations().get(0).observations()).isEqualTo(2);
    }
}
