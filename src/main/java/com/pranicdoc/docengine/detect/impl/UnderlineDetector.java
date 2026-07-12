package com.pranicdoc.docengine.detect.impl;

import com.pranicdoc.docengine.core.EngineConfig;
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
 * Generalizes FormBoxDetector's underline heuristic from pdfWorker: dy small, dx large.
 * The candidate box is the <b>writable band above the line</b> (one handwriting row tall),
 * not the zero-height line itself — an underline field's value sits on top of the line, and
 * downstream consumers (pairing, placement, eval) need that area, not a degenerate box.
 */
public class UnderlineDetector implements FieldCandidateDetector {

    public static final String ID = "underline-detector";
    /** Height of the write-on-line area claimed above the detected line. */
    private static final double WRITE_BAND_HEIGHT_PTS = 12.0;

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
            if (!(vp instanceof LinePrimitive line)) {
                continue;
            }
            double dx = Math.abs(line.x1() - line.x0());
            double dy = Math.abs(line.y1() - line.y0());
            if (dy > cfg.maxUnderlineDeltaYPts() || dx < cfg.minUnderlineLengthPts()) {
                continue;
            }
            double confidence = scoreUnderline(dx, dy, cfg);
            double lineTop = Math.max(line.y0(), line.y1());
            BoundingBox writeBand = BoundingBox.of(
                Math.min(line.x0(), line.x1()), lineTop,
                Math.max(line.x0(), line.x1()), lineTop + WRITE_BAND_HEIGHT_PTS);
            candidates.add(new DetectionCandidate(ID, writeBand, CandidateType.UNDERLINE, confidence, line.page(), Map.of()));
        }
        return candidates;
    }

    private double scoreUnderline(double dx, double dy, EngineConfig cfg) {
        double lengthBonus = Math.min(0.15, (dx - cfg.minUnderlineLengthPts()) / 200.0);
        double straightnessPenalty = (dy / Math.max(cfg.maxUnderlineDeltaYPts(), 0.001)) * 0.1;
        return Math.max(0.5, Math.min(0.95, 0.8 + lengthBonus - straightnessPenalty));
    }
}
