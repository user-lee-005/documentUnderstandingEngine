package com.pranicdoc.docengine.core;

import java.util.Map;

public record EngineConfig(
    double minRectangleWidthPts,
    double minRectangleHeightPts,
    double maxRectangleHeightPts,
    double minUnderlineLengthPts,
    double maxUnderlineDeltaYPts,
    double maxLabelToValueDistancePts,
    double lowConfidenceEscalationThreshold,
    Map<String, Double> detectorWeights
) {

    public static EngineConfig defaults() {
        return new EngineConfig(
            40.0,   // minRectangleWidthPts
            8.0,    // minRectangleHeightPts
            40.0,   // maxRectangleHeightPts
            30.0,   // minUnderlineLengthPts
            1.5,    // maxUnderlineDeltaYPts
            120.0,  // maxLabelToValueDistancePts
            0.6,    // lowConfidenceEscalationThreshold
            Map.of()
        );
    }

    public double weightFor(String detectorId) {
        return detectorWeights.getOrDefault(detectorId, 1.0);
    }
}
