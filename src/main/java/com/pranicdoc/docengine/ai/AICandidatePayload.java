package com.pranicdoc.docengine.ai;

import com.pranicdoc.docengine.detect.DetectionCandidate;
import com.pranicdoc.docengine.layout.LayoutNode;

import java.util.List;

/**
 * Structured-first by construction: candidates + local context are always present;
 * croppedRegionImage is opt-in per-field, and only the cropped ambiguous region —
 * never the whole page or document.
 */
public record AICandidatePayload(
    List<DetectionCandidate> ambiguousCandidates,
    LayoutNode localContext,
    String ocrTextIfAny,
    byte[] croppedRegionImage
) {
}
