package com.pranicdoc.docengine.graph;

import com.pranicdoc.docengine.geometry.BoundingBox;

import java.util.Map;

public record GraphNode(
    String id,
    GraphNodeType type,
    BoundingBox box,
    int page,
    double confidence,
    String sourceDetectorId,
    Map<String, Object> attributes
) {
}
