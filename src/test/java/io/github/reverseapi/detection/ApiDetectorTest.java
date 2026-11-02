package io.github.reverseapi.detection;

import io.github.reverseapi.TestExchange;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiDetectorTest {
    private final ApiDetector detector = new ApiDetector();

    @Test void classifiesJsonResponseAsApi() {
        var result = detector.detect(TestExchange.exchange("GET", "/customers", "", "", 200, "application/json", "{\"id\":1}"));
        assertThat(result.api()).isTrue(); assertThat(result.reason()).contains("JSON response");
    }

    @Test void recognizesApiPathsWithEncodedDatesAndJsonArrayResponses() {
        var result = detector.detect(TestExchange.exchange("GET",
                "/api/clientdetail/performance/12345/0/2026-01-01%2000:00:00/2026-11-03%2010:40:59/false/null/true",
                "application/json", "", 200, "application/json",
                "[{\"openingValue\":0.0,\"iso\":\"GBP\",\"values\":[]}]"));
        assertThat(result.api()).isTrue();
        assertThat(result.includedByDefault()).isTrue();
        assertThat(result.reason()).contains("API-like path", "JSON response", "JSON response body");
    }

    @Test void ignoresStaticAssets() {
        var result = detector.detect(TestExchange.exchange("GET", "/assets/app.js", "", "", 200, "application/javascript", ""));
        assertThat(result.api()).isFalse(); assertThat(result.ignoredStatic()).isTrue();
    }

    @Test void recognizesGraphQl() {
        assertThat(detector.detect(TestExchange.exchange("POST", "/graphql", "application/json", "{\"query\":\"{ viewer { id } }\"}", 200, "application/json", "{}" )).api()).isTrue();
    }

    @Test void configurableThresholdCanExposeExcludedLowConfidenceCandidates() {
        DetectionSettings settings = new DetectionSettings(); settings.minimumConfidence(90); settings.showLowConfidence(true);
        var result = new ApiDetector(settings).detect(TestExchange.exchange("GET", "/api/items", "", "", 200, "", ""));
        assertThat(result.api()).isTrue(); assertThat(result.includedByDefault()).isFalse(); assertThat(result.reason()).contains("Below inclusion threshold");
    }

    @Test void customUrlExclusionWinsOverPositiveSignals() {
        DetectionSettings settings = new DetectionSettings(); settings.excludeUrlRegex("/internal/");
        var result = new ApiDetector(settings).detect(TestExchange.exchange("GET", "/internal/api/users", "", "", 200, "application/json", "{}"));
        assertThat(result.api()).isFalse(); assertThat(result.reason()).contains("excluded URL");
    }
    @Test void recognizesSoapMetadataWithoutParsingXml() {
        var result = detector.detect(TestExchange.exchange("POST", "/service", "application/soap+xml", "<!DOCTYPE x SYSTEM 'https://invalid.test/external'><x/>", 200, "", ""));
        assertThat(result.api()).isTrue(); assertThat(result.reason()).contains("SOAP");
    }
    @Test void xmlDetectionCanBeDisabled() {
        var settings = new DetectionSettings(); settings.xml(false);
        var result = new ApiDetector(settings).detect(TestExchange.exchange("GET", "/service", "", "", 200, "application/xml", "<result/>") );
        assertThat(result.api()).isFalse();
    }
    @Test void nestedQuantifiersCannotStallCapture() {
        var settings = new DetectionSettings(); settings.excludeUrlRegex("(a+)+$");
        org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(java.time.Duration.ofSeconds(2), () -> {
            assertThat(new ApiDetector(settings).detect(TestExchange.exchange("GET", "/api/" + "a".repeat(20000) + "!", "", "", 200, "application/json", "{}" )).api()).isTrue();
        });
    }
}
