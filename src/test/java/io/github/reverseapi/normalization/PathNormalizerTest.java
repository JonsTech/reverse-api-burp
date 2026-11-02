package io.github.reverseapi.normalization;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class PathNormalizerTest {
    private final PathNormalizer normalizer = new PathNormalizer();

    @Test void normalizesIntegerIdentifiers() {
        assertThat(normalizer.normalize("/users/123")).isEqualTo("/users/{id}");
        assertThat(normalizer.normalize("/users/456")).isEqualTo("/users/{id}");
    }
    @Test void normalizesUuidIdentifiers() {
        assertThat(normalizer.normalize("/orders/550e8400-e29b-41d4-a716-446655440000")).isEqualTo("/orders/{id}");
    }
    @Test void preservesApiVersionAndNames() {
        assertThat(normalizer.normalize("/api/v1/users")).isEqualTo("/api/v1/users");
    }
    @Test void groupsDateRangesWithoutChangingLiteralFlagsOrVersions() {
        assertThat(normalizer.normalize("/api/v1/123/2026-01-01%2000:00:00/2026-11-03T10%3A40%3A59Z/false/null/true"))
                .isEqualTo("/api/v1/{id}/{date}/{date2}/false/null/true");
        assertThat(normalizer.normalize("/api/2026-11-02/2026-11-02T12:00:00+01:00"))
                .isEqualTo("/api/{date}/{date2}");
        assertThat(normalizer.normalize("/api/2026-11-report/%zz"))
                .isEqualTo("/api/2026-11-report/%zz");
    }
}

