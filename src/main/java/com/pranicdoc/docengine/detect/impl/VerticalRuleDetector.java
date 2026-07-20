package com.pranicdoc.docengine.detect.impl;

import com.pranicdoc.docengine.core.PipelineContext;
import com.pranicdoc.docengine.detect.CandidateType;
import com.pranicdoc.docengine.detect.DetectionCandidate;
import com.pranicdoc.docengine.detect.FieldCandidateDetector;
import com.pranicdoc.docengine.geometry.BoundingBox;
import com.pranicdoc.docengine.layout.LayoutNode;
import com.pranicdoc.docengine.primitives.model.LinePrimitive;
import com.pranicdoc.docengine.primitives.model.VectorPrimitive;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Mirror of {@link UnderlineDetector} for the vertical case: a drawn column divider (dx small,
 * dy large) that splits a row into a label side and a value side — e.g. a rule between
 * "Client's Name:" and the space it should be written in. Stage 6 uses this to snap a colon
 * caption's writing band to start after the rule instead of immediately after the caption text,
 * on forms where that gap is intentional (design column), while forms with no such rule keep
 * the near-label default.
 */
public class VerticalRuleDetector implements FieldCandidateDetector {

    public static final String ID = "vertical-rule-detector";
    private static final double MAX_DELTA_X_PTS = 1.5;
    private static final double MIN_LENGTH_PTS = 15.0;

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
        List<DetectionCandidate> candidates = new ArrayList<>();
        for (VectorPrimitive vp : primitives) {
            if (!(vp instanceof LinePrimitive line)) {
                continue;
            }
            double dx = Math.abs(line.x1() - line.x0());
            double dy = Math.abs(line.y1() - line.y0());
            if (dx > MAX_DELTA_X_PTS || dy < MIN_LENGTH_PTS) {
                continue;
            }
            double x = (line.x0() + line.x1()) / 2.0;
            BoundingBox box = BoundingBox.of(x, Math.min(line.y0(), line.y1()), x, Math.max(line.y0(), line.y1()));
            candidates.add(new DetectionCandidate(ID, box, CandidateType.VLINE, 0.9, line.page(), Map.of()));
        }
        return candidates;
    }
}
