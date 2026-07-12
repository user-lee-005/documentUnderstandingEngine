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
    double columnGutterMinWidthPts,
    double columnGutterBinWidthPts,
    double rowGroupingGapTolerancePts,
    double minSectionBreakGapPts,
    double sectionBreakGapMultiplier,
    int repeatedBlockMinRunLength,
    double headerFooterBandFraction,
    double headerFooterMinRepeatFraction,
    Map<String, Double> detectorWeights
) {

    public static EngineConfig defaults() {
        return new EngineConfig(
            40.0,   // minRectangleWidthPts
            8.0,    // minRectangleHeightPts
            110.0,  // maxRectangleHeightPts — multi-line note boxes reach ~100pt; page frames stay excluded
            30.0,   // minUnderlineLengthPts
            1.5,    // maxUnderlineDeltaYPts
            120.0,  // maxLabelToValueDistancePts
            0.6,    // lowConfidenceEscalationThreshold
            24.0,   // columnGutterMinWidthPts
            4.0,    // columnGutterBinWidthPts
            2.0,    // rowGroupingGapTolerancePts
            10.0,   // minSectionBreakGapPts
            1.8,    // sectionBreakGapMultiplier
            3,      // repeatedBlockMinRunLength
            0.12,   // headerFooterBandFraction
            0.5,    // headerFooterMinRepeatFraction
            Map.of(
                // Explicit, drawn geometry is trusted most; inferred/clustered candidates less so.
                "rectangle-detector", 1.0,
                "label-detector", 1.0,
                "underline-detector", 0.9,
                "checkbox-detector", 0.85,
                "table-detector", 0.85,
                "image-placeholder-detector", 0.8,
                "caption-band-detector", 0.65,
                "whitespace-detector", 0.6
            )
        );
    }

    public double weightFor(String detectorId) {
        return detectorWeights.getOrDefault(detectorId, 1.0);
    }
}
