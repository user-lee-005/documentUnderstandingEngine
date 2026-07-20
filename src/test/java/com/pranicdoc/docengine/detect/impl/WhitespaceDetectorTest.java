package com.pranicdoc.docengine.detect.impl;

import com.pranicdoc.docengine.core.EngineConfig;
import com.pranicdoc.docengine.core.PipelineContext;
import com.pranicdoc.docengine.detect.CandidateType;
import com.pranicdoc.docengine.detect.DetectionCandidate;
import com.pranicdoc.docengine.geometry.BoundingBox;
import com.pranicdoc.docengine.layout.LayoutNode;
import com.pranicdoc.docengine.layout.LayoutNodeType;
import com.pranicdoc.docengine.primitives.model.TextLine;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WhitespaceDetectorTest {

    @Test
    void flagsAWideInteriorGapBetweenTwoItems() {
        TextLine label = new TextLine("Name:", new BoundingBox(50, 700, 90, 712), 0, List.of());
        TextLine nextLabel = new TextLine("DOB:", new BoundingBox(200, 700, 240, 712), 0, List.of());
        LayoutNode row = new LayoutNode("row-0", LayoutNodeType.ROW, new BoundingBox(50, 700, 240, 712), 0);
        row.putAttribute("textLines", List.of(label, nextLabel));
        row.putAttribute("vectorPrimitives", List.of());

        List<DetectionCandidate> candidates = new WhitespaceDetector().detect(row, new PipelineContext(EngineConfig.defaults()));

        assertEquals(1, candidates.size());
        assertEquals(CandidateType.WHITESPACE, candidates.get(0).type());
        assertEquals(90, candidates.get(0).box().x0(), 0.01);
        assertEquals(200, candidates.get(0).box().x1(), 0.01);
    }

    @Test
    void ignoresASingleItemRow() {
        TextLine label = new TextLine("Name:", new BoundingBox(50, 700, 90, 712), 0, List.of());
        LayoutNode row = new LayoutNode("row-0", LayoutNodeType.ROW, new BoundingBox(50, 700, 90, 712), 0);
        row.putAttribute("textLines", List.of(label));
        row.putAttribute("vectorPrimitives", List.of());

        List<DetectionCandidate> candidates = new WhitespaceDetector().detect(row, new PipelineContext(EngineConfig.defaults()));

        assertTrue(candidates.isEmpty());
    }
}
