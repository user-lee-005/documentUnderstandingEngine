package com.pranicdoc.docengine.graph;

import java.util.List;
import java.util.Optional;

/** Fluent traversal over a DocumentGraph, e.g. {@code GraphQuery.from(graph, node.id()).outgoing(NEAREST_LABEL).first()}. */
public final class GraphQuery {

    private final DocumentGraph graph;
    private final GraphNode current;

    private GraphQuery(DocumentGraph graph, GraphNode current) {
        this.graph = graph;
        this.current = current;
    }

    public static GraphQuery from(DocumentGraph graph, String nodeId) {
        return new GraphQuery(graph, graph.node(nodeId));
    }

    public List<GraphNode> outgoing(EdgeType type) {
        if (current == null) {
            return List.of();
        }
        return graph.edgesFrom(current.id()).stream()
            .filter(e -> e.type() == type)
            .map(e -> graph.node(e.to()))
            .toList();
    }

    public Optional<GraphNode> first(EdgeType type) {
        return outgoing(type).stream().findFirst();
    }
}
