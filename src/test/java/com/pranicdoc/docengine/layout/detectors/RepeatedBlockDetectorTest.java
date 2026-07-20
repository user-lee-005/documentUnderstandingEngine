package com.pranicdoc.docengine.layout.detectors;

import com.pranicdoc.docengine.core.EngineConfig;
import com.pranicdoc.docengine.core.PipelineContext;
import com.pranicdoc.docengine.geometry.BoundingBox;
import com.pranicdoc.docengine.layout.LayoutNode;
import com.pranicdoc.docengine.layout.LayoutNodeType;
import com.pranicdoc.docengine.primitives.model.RectanglePrimitive;
import com.pranicdoc.docengine.primitives.model.TextLine;
import com.pranicdoc.docengine.primitives.model.VectorPrimitive;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RepeatedBlockDetectorTest {

    @Test
    void tagsARunOfStructurallyIdenticalRowsButNotAnOutlier() {
        LayoutNode section = new LayoutNode("section-0", LayoutNodeType.SECTION, new BoundingBox(0, 0, 600, 800), 0);

        for (int i = 0; i < 4; i++) {
            double y0 = 700 - i * 20;
            LayoutNode row = new LayoutNode("row-" + i, LayoutNodeType.ROW, new BoundingBox(0, y0, 600, y0 + 12), 0);
            row.putAttribute("textLines", List.of(new TextLine("Item " + i, new BoundingBox(0, y0, 100, y0 + 12), 0, List.of())));
            row.putAttribute("vectorPrimitives", List.of());
            section.addChild(row);
        }

        LayoutNode oddRow = new LayoutNode("row-odd", LayoutNodeType.ROW, new BoundingBox(0, 600, 600, 650), 0);
        oddRow.putAttribute("textLines", List.of());
        oddRow.putAttribute("vectorPrimitives", List.<VectorPrimitive>of(new RectanglePrimitive(new BoundingBox(0, 600, 600, 650), 0, false, 1.0)));
        section.addChild(oddRow);

        PipelineContext ctx = new PipelineContext(EngineConfig.defaults());
        List<LayoutNode> tagged = new RepeatedBlockDetector().detect(section, ctx);

        assertEquals(4, tagged.size());
        String groupId = tagged.get(0).attribute("repeatingGroupId");
        assertNotNull(groupId);
        assertTrue(tagged.stream().allMatch(r -> groupId.equals(r.attribute("repeatingGroupId"))));
        assertNull(oddRow.attribute("repeatingGroupId"));
    }
}
