package com.pranicdoc.docengine.graph;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Every detected/layout object as a node, connected by typed edges. Built in Stage 5, queried by Stage 6 and the debug renderer. */
public class DocumentGraph {

    private final Map<String, GraphNode> nodes = new LinkedHashMap<>();
    private final List<GraphEdge> edges = new ArrayList<>();

    public void addNode(GraphNode node) {
        nodes.put(node.id(), node);
    }

    public void addEdge(GraphEdge edge) {
        edges.add(edge);
    }

    public GraphNode node(String id) {
        return nodes.get(id);
    }

    public List<GraphNode> nodesOfType(GraphNodeType type) {
        return nodes.values().stream().filter(n -> n.type() == type).toList();
    }

    public List<GraphEdge> edgesFrom(String nodeId) {
        return edges.stream().filter(e -> e.from().equals(nodeId)).toList();
    }

    public List<GraphNode> allNodes() {
        return List.copyOf(nodes.values());
    }

    public List<GraphEdge> allEdges() {
        return List.copyOf(edges);
    }
}
