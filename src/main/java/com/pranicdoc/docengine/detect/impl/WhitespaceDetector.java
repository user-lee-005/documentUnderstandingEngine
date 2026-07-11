package com.pranicdoc.docengine.detect.impl;

import com.pranicdoc.docengine.core.EngineConfig;
import com.pranicdoc.docengine.core.PipelineContext;
import com.pranicdoc.docengine.detect.CandidateType;
import com.pranicdoc.docengine.detect.DetectionCandidate;
import com.pranicdoc.docengine.detect.FieldCandidateDetector;
import com.pranicdoc.docengine.geometry.BoundingBox;
import com.pranicdoc.docengine.layout.LayoutNode;
import com.pranicdoc.docengine.primitives.model.TextLine;
import com.pranicdoc.docengine.primitives.model.VectorPrimitive;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Finds empty-field candidates: gaps strictly between two items in the same row scope
 * (e.g. a label immediately followed by more content, with unruled blank space in between).
 * Deliberately only interior gaps — a row's own bounding box is tight to its content, so a
 * "trailing gap to the row's right edge" isn't a meaningful boundary here.
 */
public class WhitespaceDetector implements FieldCandidateDetector {

    public static final String ID = "whitespace-detector";

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
        List<TextLine> lines = attrOrEmpty(scope, "textLines");
        List<VectorPrimitive> vectors = attrOrEmpty(scope, "vectorPrimitives");
        if (lines.size() + vectors.size() < 2) {
            return List.of();
        }

        List<BoundingBox> boxes = new ArrayList<>();
        lines.forEach(l -> boxes.add(l.box()));
        vectors.forEach(v -> boxes.add(v.box()));
        boxes.sort(Comparator.comparingDouble(BoundingBox::x0));

        EngineConfig cfg = ctx.config();
        List<DetectionCandidate> candidates = new ArrayList<>();
        double rowY0 = scope.box().y0();
        double rowY1 = scope.box().y1();

        for (int i = 0; i + 1 < boxes.size(); i++) {
            double gapStart = boxes.get(i).x1();
            double gapEnd = boxes.get(i + 1).x0();
            double gapWidth = gapEnd - gapStart;
            if (gapWidth >= cfg.minRectangleWidthPts()) {
                BoundingBox gapBox = new BoundingBox(gapStart, rowY0, gapEnd, rowY1);
                candidates.add(new DetectionCandidate(ID, gapBox, CandidateType.WHITESPACE, scoreGap(gapWidth, cfg), scope.page(), Map.of()));
            }
        }
        return candidates;
    }

    private double scoreGap(double gapWidth, EngineConfig cfg) {
        double normalized = Math.min(1.0, gapWidth / (cfg.minRectangleWidthPts() * 3));
        return 0.4 + 0.3 * normalized;
    }

    private <T> List<T> attrOrEmpty(LayoutNode node, String key) {
        List<T> value = node.attribute(key);
        return value == null ? List.of() : value;
    }
}
