package com.pranicdoc.docengine.detect.impl;

import com.pranicdoc.docengine.core.EngineConfig;
import com.pranicdoc.docengine.core.PipelineContext;
import com.pranicdoc.docengine.detect.CandidateType;
import com.pranicdoc.docengine.detect.DetectionCandidate;
import com.pranicdoc.docengine.geometry.BoundingBox;
import com.pranicdoc.docengine.layout.LayoutNode;
import com.pranicdoc.docengine.layout.LayoutNodeType;
import com.pranicdoc.docengine.primitives.model.RectanglePrimitive;
import com.pranicdoc.docengine.primitives.model.VectorPrimitive;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImagePlaceholderDetectorTest {

    @Test
    void flagsALargeSquareEmptyRectangleAsAPhotoSlot() {
        RectanglePrimitive photoBox = new RectanglePrimitive(new BoundingBox(50, 600, 150, 700), 0, false, 1.0);
        LayoutNode scope = new LayoutNode("row-0", LayoutNodeType.ROW, new BoundingBox(50, 600, 150, 700), 0);
        scope.putAttribute("vectorPrimitives", List.<VectorPrimitive>of(photoBox));

        List<DetectionCandidate> candidates = new ImagePlaceholderDetector().detect(scope, new PipelineContext(EngineConfig.defaults()));

        assertEquals(1, candidates.size());
        assertEquals(CandidateType.IMAGE_PLACEHOLDER, candidates.get(0).type());
    }

    @Test
    void ignoresATextFieldSizedRectangle() {
        RectanglePrimitive textField = new RectanglePrimitive(new BoundingBox(50, 690, 150, 710), 0, false, 1.0);
        LayoutNode scope = new LayoutNode("row-0", LayoutNodeType.ROW, new BoundingBox(50, 690, 150, 710), 0);
        scope.putAttribute("vectorPrimitives", List.<VectorPrimitive>of(textField));

        List<DetectionCandidate> candidates = new ImagePlaceholderDetector().detect(scope, new PipelineContext(EngineConfig.defaults()));

        assertTrue(candidates.isEmpty());
    }
}
