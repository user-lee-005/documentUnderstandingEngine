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
 * Generalizes FormBoxDetector's HIGH-confidence rectangle heuristic from pdfWorker
 * (stroked rectangle, height in a text-line-ish band, minimum width) into a standalone
 * detector operating on already-extracted RectanglePrimitive candidates.
 */
public class RectangleDetector implements FieldCandidateDetector {

    public static final String ID = "rectangle-detector";

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
            if (!(vp instanceof RectanglePrimitive rect)) {
                continue;
            }
            double w = rect.box().width();
            double h = rect.box().height();
            if (w < cfg.minRectangleWidthPts() || h < cfg.minRectangleHeightPts() || h > cfg.maxRectangleHeightPts()) {
                continue;
            }
            double confidence = scoreRectangle(h, cfg);
            candidates.add(new DetectionCandidate(
                ID, rect.box(), CandidateType.RECTANGLE, confidence, rect.page(), Map.of("filled", rect.filled())
            ));
        }
        return candidates;
    }

    private double scoreRectangle(double heightPts, EngineConfig cfg) {
        double idealHeight = (cfg.minRectangleHeightPts() + cfg.maxRectangleHeightPts()) / 2.0;
        double deviation = Math.abs(heightPts - idealHeight) / idealHeight;
        return Math.max(0.5, 1.0 - Math.min(0.5, deviation));
    }
}
