package com.pranicdoc.docengine.semantic;

import com.pranicdoc.docengine.geometry.BoundingBox;

import java.util.List;

public record SemanticField(
    String id,
    String name,
    String inferredType,
    String value,
    BoundingBox box,
    int page,
    /** The structural layout-tree node id (row/band) the value candidate was found in — not yet a semantic section name (that needs header-style section detection, not built). */
    String sectionNodeId,
    double confidence,
    String detectorId,
    List<String> relatedFieldIds
) {
}
