package com.pranicdoc.docengine.layout.detectors;

import com.pranicdoc.docengine.core.EngineConfig;
import com.pranicdoc.docengine.core.PipelineContext;
import com.pranicdoc.docengine.geometry.BoundingBox;
import com.pranicdoc.docengine.layout.LayoutNode;
import com.pranicdoc.docengine.layout.LayoutNodeType;
import com.pranicdoc.docengine.primitives.model.TextLine;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ColumnDetectorTest {

    @Test
    void splitsIntoTwoColumnsWhenAWideGutterExists() {
        TextLine left = new TextLine("Left", new BoundingBox(50, 700, 100, 712), 0, List.of());
        TextLine right = new TextLine("Right", new BoundingBox(300, 700, 350, 712), 0, List.of());

        LayoutNode scope = new LayoutNode("page-0-body", LayoutNodeType.SECTION, new BoundingBox(0, 0, 600, 800), 0);
        scope.putAttribute("textLines", List.of(left, right));
        scope.putAttribute("vectorPrimitives", List.of());

        PipelineContext ctx = new PipelineContext(EngineConfig.defaults());
        List<LayoutNode> columns = new ColumnDetector().detect(scope, ctx);

        assertEquals(2, columns.size());
        List<TextLine> firstColumnLines = columns.get(0).attribute("textLines");
        List<TextLine> secondColumnLines = columns.get(1).attribute("textLines");
        assertTrue(firstColumnLines.contains(left));
        assertTrue(secondColumnLines.contains(right));
    }

    @Test
    void returnsASingleColumnWhenNoGutterExists() {
        TextLine a = new TextLine("A", new BoundingBox(50, 700, 80, 712), 0, List.of());
        TextLine b = new TextLine("B", new BoundingBox(90, 700, 120, 712), 0, List.of());

        LayoutNode scope = new LayoutNode("page-0-body", LayoutNodeType.SECTION, new BoundingBox(0, 0, 600, 800), 0);
        scope.putAttribute("textLines", List.of(a, b));
        scope.putAttribute("vectorPrimitives", List.of());

        PipelineContext ctx = new PipelineContext(EngineConfig.defaults());
        List<LayoutNode> columns = new ColumnDetector().detect(scope, ctx);

        assertEquals(1, columns.size());
        assertEquals(LayoutNodeType.COLUMN, columns.get(0).type());
    }
}
