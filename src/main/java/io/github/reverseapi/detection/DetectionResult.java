package io.github.reverseapi.detection;

public record DetectionResult(boolean api, boolean ignoredStatic, int confidence, String reason, boolean includedByDefault) {
    public DetectionResult(boolean api, boolean ignoredStatic, int confidence, String reason) {
        this(api, ignoredStatic, confidence, reason, api);
    }

    public static DetectionResult ignored(String reason) {
        return new DetectionResult(false, true, 0, reason, false);
    }
}

