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
    double confidence,
    String detectorId,
    List<String> relatedFieldIds
) {
}
