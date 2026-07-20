package com.pranicdoc.docengine.graph;

import com.pranicdoc.docengine.geometry.BoundingBox;
import com.pranicdoc.docengine.layout.LayoutNode;
import com.pranicdoc.docengine.layout.LayoutNodeType;
import com.pranicdoc.docengine.layout.ReadingOrder;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LayoutGraphBuilderTest {

    @Test
    void buildsContainmentAndReadingOrderEdgesFromTheTree() {
        LayoutNode page = new LayoutNode("page-0", LayoutNodeType.PAGE, new BoundingBox(0, 0, 600, 800), 0);
        LayoutNode colA = new LayoutNode("page-0-colA", LayoutNodeType.COLUMN, new BoundingBox(0, 0, 300, 800), 0);
        LayoutNode colB = new LayoutNode("page-0-colB", LayoutNodeType.COLUMN, new BoundingBox(300, 0, 600, 800), 0);
        LayoutNode row = new LayoutNode("page-0-colA-row-0", LayoutNodeType.ROW, new BoundingBox(0, 700, 300, 712), 0);
        colA.addChild(row);
        page.addChild(colA);
        page.addChild(colB);

        ReadingOrder order = new ReadingOrder(List.of(page.id(), colA.id(), row.id(), colB.id()));

        DocumentGraph graph = new LayoutGraphBuilder().build(List.of(page), List.of(order));

        assertEquals(GraphNodeType.PAGE, graph.node("page-0").type());
        assertEquals(GraphNodeType.ROW, graph.node("page-0-colA-row-0").type());

        List<GraphNode> pageChildren = GraphQuery.from(graph, "page-0").outgoing(EdgeType.PARENT_OF);
        assertEquals(2, pageChildren.size());

        List<GraphNode> rowParent = GraphQuery.from(graph, "page-0-colA-row-0").outgoing(EdgeType.CHILD_OF);
        assertEquals(1, rowParent.size());
        assertEquals("page-0-colA", rowParent.get(0).id());

        List<GraphNode> nextAfterColA = GraphQuery.from(graph, "page-0-colA").outgoing(EdgeType.READING_ORDER_NEXT);
        assertEquals(1, nextAfterColA.size());
        assertEquals("page-0-colA-row-0", nextAfterColA.get(0).id());
    }
}
