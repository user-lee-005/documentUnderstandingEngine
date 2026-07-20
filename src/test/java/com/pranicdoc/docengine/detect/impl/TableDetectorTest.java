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

class TableDetectorTest {

    @Test
    void detectsTwoConsistentColumnsAcrossARepeatingRowGroup() {
        LayoutNode section = new LayoutNode("section-0", LayoutNodeType.SECTION, new BoundingBox(0, 0, 400, 800), 0);

        for (int i = 0; i < 3; i++) {
            double y0 = 700 - i * 20;
            LayoutNode row = new LayoutNode("row-" + i, LayoutNodeType.ROW, new BoundingBox(0, y0, 400, y0 + 12), 0);
            TextLine colA = new TextLine("A" + i, new BoundingBox(50, y0, 90, y0 + 12), 0, List.of());
            TextLine colB = new TextLine("B" + i, new BoundingBox(200, y0, 240, y0 + 12), 0, List.of());
            row.putAttribute("textLines", List.of(colA, colB));
            row.putAttribute("vectorPrimitives", List.of());
            row.putAttribute("repeatingGroupId", "grp1");
            section.addChild(row);
        }

        List<DetectionCandidate> candidates = new TableDetector().detect(section, new PipelineContext(EngineConfig.defaults()));

        assertEquals(7, candidates.size(), "6 cells + 1 aggregate table region");
        assertTrue(candidates.stream().allMatch(c -> c.type() == CandidateType.TABLE));

        long col0Count = candidates.stream().filter(c -> Integer.valueOf(0).equals(c.attributes().get("col"))).count();
        long col1Count = candidates.stream().filter(c -> Integer.valueOf(1).equals(c.attributes().get("col"))).count();
        assertEquals(3, col0Count);
        assertEquals(3, col1Count);

        assertEquals(0.9, candidates.get(0).rawConfidence(), 0.01);

        DetectionCandidate aggregate = candidates.stream()
                .filter(c -> Boolean.TRUE.equals(c.attributes().get("aggregate"))).findFirst().orElseThrow();
        assertEquals(50, aggregate.box().x0(), 0.01);
        assertEquals(240, aggregate.box().x1(), 0.01, "aggregate spans all cells");
    }

    @Test
    void ignoresRowsWithoutARepeatingGroupTag() {
        LayoutNode section = new LayoutNode("section-0", LayoutNodeType.SECTION, new BoundingBox(0, 0, 400, 800), 0);
        LayoutNode row = new LayoutNode("row-0", LayoutNodeType.ROW, new BoundingBox(0, 700, 400, 712), 0);
        row.putAttribute("textLines", List.of(new TextLine("Solo", new BoundingBox(50, 700, 90, 712), 0, List.of())));
        row.putAttribute("vectorPrimitives", List.of());
        section.addChild(row);

        List<DetectionCandidate> candidates = new TableDetector().detect(section, new PipelineContext(EngineConfig.defaults()));

        assertTrue(candidates.isEmpty());
    }
}
