package com.pranicdoc.docengine.detect.impl;

import com.pranicdoc.docengine.core.EngineConfig;
import com.pranicdoc.docengine.core.PipelineContext;
import com.pranicdoc.docengine.detect.CandidateType;
import com.pranicdoc.docengine.detect.DetectionCandidate;
import com.pranicdoc.docengine.geometry.BoundingBox;
import com.pranicdoc.docengine.layout.LayoutNode;
import com.pranicdoc.docengine.layout.LayoutNodeType;
import com.pranicdoc.docengine.primitives.model.LinePrimitive;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnderlineDetectorTest {

    @Test
    void flagsALongHorizontalLineAsAnUnderline() {
        LinePrimitive underline = new LinePrimitive(50, 700, 150, 700.2, 0, 1.0);
        LayoutNode scope = new LayoutNode("row-0", LayoutNodeType.ROW, new BoundingBox(50, 699, 150, 701), 0);
        scope.putAttribute("vectorPrimitives", List.of(underline));

        List<DetectionCandidate> candidates = new UnderlineDetector().detect(scope, new PipelineContext(EngineConfig.defaults()));

        assertEquals(1, candidates.size());
        assertEquals(CandidateType.UNDERLINE, candidates.get(0).type());
        assertTrue(candidates.get(0).rawConfidence() > 0.5);
    }

    @Test
    void ignoresAShortOrSlantedLine() {
        LinePrimitive tooShort = new LinePrimitive(50, 700, 60, 700, 0, 1.0);
        LinePrimitive tooSlanted = new LinePrimitive(50, 700, 150, 720, 0, 1.0);
        LayoutNode scope = new LayoutNode("row-0", LayoutNodeType.ROW, new BoundingBox(50, 699, 150, 721), 0);
        scope.putAttribute("vectorPrimitives", List.of(tooShort, tooSlanted));

        List<DetectionCandidate> candidates = new UnderlineDetector().detect(scope, new PipelineContext(EngineConfig.defaults()));

        assertTrue(candidates.isEmpty());
    }
}
