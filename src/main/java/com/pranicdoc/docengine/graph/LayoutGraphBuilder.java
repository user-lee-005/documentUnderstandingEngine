package com.pranicdoc.docengine.graph;

import com.pranicdoc.docengine.layout.LayoutNode;
import com.pranicdoc.docengine.layout.LayoutNodeType;
import com.pranicdoc.docengine.layout.ReadingOrder;

import java.util.List;
import java.util.Map;

/**
 * Converts the Stage 3 layout tree into the Stage 5 DocumentGraph: PARENT_OF/CHILD_OF edges
 * for tree containment, READING_ORDER_NEXT for sequence. NEAREST_LABEL/NEAREST_VALUE and
 * BELONGS_TO_TABLE edges need detection candidates, which are Stage 4's output — those are
 * added once the detector set moves past Rectangle/Label (Roadmap Phase 2+).
 */
public class LayoutGraphBuilder {

    public DocumentGraph build(List<LayoutNode> pageRoots, List<ReadingOrder> readingOrders) {
        DocumentGraph graph = new DocumentGraph();
        for (LayoutNode pageRoot : pageRoots) {
            addNodesAndContainmentEdges(pageRoot, graph);
        }
        for (ReadingOrder order : readingOrders) {
            addReadingOrderEdges(order, graph);
        }
        return graph;
    }

    private void addNodesAndContainmentEdges(LayoutNode node, DocumentGraph graph) {
        graph.addNode(toGraphNode(node));
        for (LayoutNode child : node.children()) {
            graph.addEdge(new GraphEdge(node.id(), child.id(), EdgeType.PARENT_OF, 1.0));
            graph.addEdge(new GraphEdge(child.id(), node.id(), EdgeType.CHILD_OF, 1.0));
            addNodesAndContainmentEdges(child, graph);
        }
    }

    private void addReadingOrderEdges(ReadingOrder order, DocumentGraph graph) {
        List<String> ids = order.nodeIdsInOrder();
        for (int i = 1; i < ids.size(); i++) {
            graph.addEdge(new GraphEdge(ids.get(i - 1), ids.get(i), EdgeType.READING_ORDER_NEXT, 1.0));
        }
    }

    private GraphNode toGraphNode(LayoutNode node) {
        return new GraphNode(node.id(), mapType(node.type()), node.box(), node.page(), 1.0, "layout-analyzer", Map.of());
    }

    private GraphNodeType mapType(LayoutNodeType type) {
        return switch (type) {
            case PAGE -> GraphNodeType.PAGE;
            case COLUMN -> GraphNodeType.COLUMN;
            case SECTION -> GraphNodeType.SECTION;
            case ROW -> GraphNodeType.ROW;
            case BLOCK -> GraphNodeType.BLOCK;
            case FIELD -> GraphNodeType.FIELD;
            case VALUE -> GraphNodeType.VALUE;
        };
    }
}
