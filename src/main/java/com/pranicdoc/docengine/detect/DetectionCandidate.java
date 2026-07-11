package com.pranicdoc.docengine.detect;

import com.pranicdoc.docengine.geometry.BoundingBox;

import java.util.Map;

public record DetectionCandidate(
    String detectorId,
    BoundingBox box,
    CandidateType type,
    double rawConfidence,
    int page,
    Map<String, Object> attributes
) {
}
