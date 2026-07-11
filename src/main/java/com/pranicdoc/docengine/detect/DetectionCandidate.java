package com.pranicdoc.docengine.detect;

import com.pranicdoc.docengine.geometry.BoundingBox;

import java.util.Map;

public record DetectionCandidate(
    String detectorId,
    BoundingBox box,
    CandidateType type,
    double rawConfidence,
    int page,
    Map<String, Object> attributes,
    String scopeNodeId
) {
    /** Detectors construct candidates without knowing their own scope — DetectorRegistry stamps scopeNodeId afterward. */
    public DetectionCandidate(String detectorId, BoundingBox box, CandidateType type, double rawConfidence, int page, Map<String, Object> attributes) {
        this(detectorId, box, type, rawConfidence, page, attributes, null);
    }

    public DetectionCandidate withScopeNodeId(String scopeNodeId) {
        return new DetectionCandidate(detectorId, box, type, rawConfidence, page, attributes, scopeNodeId);
    }
}
