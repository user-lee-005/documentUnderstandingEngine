package com.pranicdoc.docengine.detect.impl;

import com.pranicdoc.docengine.core.EngineConfig;
import com.pranicdoc.docengine.core.PipelineContext;
import com.pranicdoc.docengine.detect.CandidateType;
import com.pranicdoc.docengine.detect.DetectionCandidate;
import com.pranicdoc.docengine.geometry.BoundingBox;
import com.pranicdoc.docengine.layout.LayoutNode;
import com.pranicdoc.docengine.layout.LayoutNodeType;
import com.pranicdoc.docengine.primitives.model.CurvePrimitive;
import com.pranicdoc.docengine.primitives.model.LinePrimitive;
import com.pranicdoc.docengine.primitives.model.RectanglePrimitive;
import com.pranicdoc.docengine.primitives.model.VectorPrimitive;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CheckboxDetectorTest {

    @Test
    void aCircleOfFourBezierSegmentsBecomesOneCheckbox() {
        // 12pt circle centered at (119, 706) — the GENDER M/F pattern
        List<VectorPrimitive> circle = List.of(
                arc(113, 706, 119, 712),  // top-left quarter
                arc(119, 712, 125, 706),  // top-right
                arc(125, 706, 119, 700),  // bottom-right
                arc(119, 700, 113, 706)); // bottom-left
        List<DetectionCandidate> candidates = detect(circle);

        assertEquals(1, candidates.size());
        assertEquals(CandidateType.CHECKBOX, candidates.get(0).type());
        assertEquals(113, candidates.get(0).box().x0(), 0.01);
        assertEquals(125, candidates.get(0).box().x1(), 0.01);
    }

    @Test
    void aPlainSmallRectangleQualifiesAlone() {
        RectanglePrimitive square = new RectanglePrimitive(new BoundingBox(49, 303, 61, 315), 0, false, 1.0);

        List<DetectionCandidate> candidates = detect(List.of(square));

        assertEquals(1, candidates.size());
        assertEquals(CandidateType.CHECKBOX, candidates.get(0).type());
    }

    @Test
    void aTickMarkInsideTheBoxJoinsItsClusterWithoutGrowingIt() {
        RectanglePrimitive square = new RectanglePrimitive(new BoundingBox(49, 303, 61, 315), 0, false, 1.0);
        LinePrimitive tickDown = new LinePrimitive(52, 308, 55, 305, 0, 1.0);
        LinePrimitive tickUp = new LinePrimitive(55, 305, 60, 313, 0, 1.0);

        List<DetectionCandidate> candidates = detect(List.of(square, tickDown, tickUp));

        assertEquals(1, candidates.size());
        assertEquals(49, candidates.get(0).box().x0(), 0.01);
        assertEquals(61, candidates.get(0).box().x1(), 0.01);
    }

    @Test
    void longLinesAndLoneShortSegmentsAreNotCheckboxes() {
        LinePrimitive divider = new LinePrimitive(49, 690, 285, 690, 0, 1.0);   // row divider
        LinePrimitive loneDash = new LinePrimitive(100, 500, 110, 500, 0, 1.0); // stray mark

        assertTrue(detect(List.of(divider, loneDash)).isEmpty());
    }

    private static List<DetectionCandidate> detect(List<VectorPrimitive> primitives) {
        LayoutNode row = new LayoutNode("row-0", LayoutNodeType.ROW, new BoundingBox(0, 0, 600, 800), 0);
        row.putAttribute("vectorPrimitives", primitives);
        return new CheckboxDetector().detect(row, new PipelineContext(EngineConfig.defaults()));
    }

    /** Quarter-circle Bezier from (x0,y0) to (x3,y3); control points stay inside the arc's bbox. */
    private static CurvePrimitive arc(double x0, double y0, double x3, double y3) {
        return new CurvePrimitive(x0, y0, x0, y3, x3, y0, x3, y3, 0);
    }
}
