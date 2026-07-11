package com.pranicdoc.docengine.layout;

import com.pranicdoc.docengine.geometry.BoundingBox;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LayoutNodeTest {

    @Test
    void isLeafReflectsWhetherChildrenExist() {
        LayoutNode parent = new LayoutNode("parent", LayoutNodeType.SECTION, new BoundingBox(0, 0, 100, 100), 0);
        assertTrue(parent.isLeaf());

        LayoutNode child = new LayoutNode("child", LayoutNodeType.ROW, new BoundingBox(0, 0, 100, 10), 0);
        parent.addChild(child);

        assertFalse(parent.isLeaf());
        assertTrue(child.isLeaf());
    }

    @Test
    void collectWalksSelfAndAllDescendants() {
        LayoutNode root = new LayoutNode("root", LayoutNodeType.PAGE, new BoundingBox(0, 0, 100, 100), 0);
        LayoutNode column = new LayoutNode("col", LayoutNodeType.COLUMN, new BoundingBox(0, 0, 100, 100), 0);
        LayoutNode row1 = new LayoutNode("row1", LayoutNodeType.ROW, new BoundingBox(0, 80, 100, 90), 0);
        LayoutNode row2 = new LayoutNode("row2", LayoutNodeType.ROW, new BoundingBox(0, 60, 100, 70), 0);
        column.addChild(row1);
        column.addChild(row2);
        root.addChild(column);

        List<LayoutNode> rows = root.collect(n -> n.type() == LayoutNodeType.ROW);
        assertEquals(2, rows.size());
        assertTrue(rows.stream().anyMatch(n -> n.id().equals("row1")));
        assertTrue(rows.stream().anyMatch(n -> n.id().equals("row2")));

        List<LayoutNode> leaves = root.collect(LayoutNode::isLeaf);
        assertEquals(2, leaves.size());
    }
}
