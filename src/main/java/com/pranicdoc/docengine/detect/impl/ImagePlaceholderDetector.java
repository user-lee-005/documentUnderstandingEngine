package com.pranicdoc.docengine.detect.impl;

import com.pranicdoc.docengine.core.EngineConfig;
import com.pranicdoc.docengine.core.PipelineContext;
import com.pranicdoc.docengine.detect.CandidateType;
import com.pranicdoc.docengine.detect.DetectionCandidate;
import com.pranicdoc.docengine.detect.FieldCandidateDetector;
import com.pranicdoc.docengine.layout.LayoutNode;
import com.pranicdoc.docengine.primitives.model.RectanglePrimitive;
import com.pranicdoc.docengine.primitives.model.VectorPrimitive;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Detects empty bordered regions sized/shaped like a photo or logo slot — an explicit,
 * non-overlapping size partition against RectangleDetector's text-field band: too tall for a
 * text field, and roughly square rather than a long thin strip.
 */
public class ImagePlaceholderDetector implements FieldCandidateDetector {

    public static final String ID = "image-placeholder-detector";
    private static final double MIN_ASPECT_RATIO = 0.4;
    /** Wide-but-tall note boxes (e.g. 372x136pt "HEALING DETAILS") are still empty bordered regions. */
    private static final double MAX_ASPECT_RATIO = 3.5;

    @Override
    public String detectorId() {
        return ID;
    }

    @Override
    public boolean isApplicable(PipelineContext ctx) {
        return true;
    }

    @Override
    public List<DetectionCandidate> detect(LayoutNode scope, PipelineContext ctx) {
        List<VectorPrimitive> primitives = scope.attribute("vectorPrimitives");
        if (primitives == null) {
            return List.of();
        }
        EngineConfig cfg = ctx.config();
        List<DetectionCandidate> candidates = new ArrayList<>();

        for (VectorPrimitive vp : primitives) {
            if (!(vp instanceof RectanglePrimitive rect) || rect.filled()) {
                continue;
            }
            double w = rect.box().width();
            double h = rect.box().height();
            if (h <= cfg.maxRectangleHeightPts()) {
                continue;
            }
            double aspect = w / h;
            if (aspect < MIN_ASPECT_RATIO || aspect > MAX_ASPECT_RATIO) {
                continue;
            }
            double squareness = 1.0 - Math.min(1.0, Math.abs(1.0 - aspect));
            double confidence = 0.5 + 0.3 * squareness;
            candidates.add(new DetectionCandidate(ID, rect.box(), CandidateType.IMAGE_PLACEHOLDER, confidence, rect.page(), Map.of()));
        }
        return candidates;
    }
}
