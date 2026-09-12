package io.github.reverseapi.model;
import io.github.reverseapi.TestExchange;
import io.github.reverseapi.detection.DetectionResult;
import io.github.reverseapi.normalization.PathNormalizer;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class ReleaseStoreTest {
    private final DetectionResult detection = new DetectionResult(true, false, 80, "test");
    @Test void exclusionSurvivesLaterTrafficAndSnapshotsAreIndependent() {
        var sample = TestExchange.exchange("GET", "/api", "", "", 200, "", "");
        var op = new ApiOperation(sample, "/api", detection, false); op.included(false);
        var snapshot = op.snapshot(); op.observe(sample, detection);
        assertThat(op.included()).isFalse(); op.markApi(); assertThat(snapshot.included()).isFalse();
        assertThat(snapshot.observations()).isEqualTo(1);
    }
    @Test void sampleLimitAndObservationCountAreIndependent() {
        var sample = TestExchange.exchange("GET", "/api", "", "", 200, "", "");
        var op = new ApiOperation(sample, "/api", detection, false);
        for (int i=0;i<100;i++) op.observe(sample,detection);
        assertThat(op.samples()).hasSize(25); assertThat(op.observations()).isEqualTo(101);
    }
    @Test void operationLimitAndClearAreBounded() {
        var store = new EndpointDeduplicator(new PathNormalizer());
        for (int i=0;i<2100;i++) store.add(TestExchange.exchange("GET", "/api/item-"+i, "", "", 200, "", ""),detection,false);
        assertThat(store.operations()).hasSize(2000); assertThat(store.dropped()).isEqualTo(100);
        store.clear(); assertThat(store.operations()).isEmpty(); assertThat(store.dropped()).isZero();
    }
    @Test void retainedBodyBudgetBoundsDistinctOperations() {
        var store = new EndpointDeduplicator(new PathNormalizer());
        for (int i=0;i<200;i++) store.add(TestExchange.exchange("GET", "/api/item-"+i, "", "", 200, "text/plain", "x".repeat(200000)),detection,false);
        assertThat(store.operations().size()).isLessThan(100); assertThat(store.dropped()).isPositive();
    }
    @Test void nestedIdentifiersAreDistinctAndDoubleSlashesRemainLiteral() {
        var normalizer = new PathNormalizer();
        assertThat(normalizer.normalize("/users/1/orders/2")).isEqualTo("/users/{id}/orders/{id2}");
        assertThat(normalizer.normalize("/api//users")).isEqualTo("/api//users");
    }
}
