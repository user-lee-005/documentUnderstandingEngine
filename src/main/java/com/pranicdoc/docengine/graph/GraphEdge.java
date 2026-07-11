package com.pranicdoc.docengine.graph;

public record GraphEdge(String from, String to, EdgeType type, double weight) {
}
