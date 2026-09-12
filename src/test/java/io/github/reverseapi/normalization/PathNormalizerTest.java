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
}

