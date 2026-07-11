package com.pranicdoc.docengine.output;

import com.pranicdoc.docengine.geometry.BoundingBox;
import com.pranicdoc.docengine.semantic.SemanticField;

import java.util.List;

public record FieldResult(
    String name,
    String type,
    String value,
    BoundingBox coordinates,
    double confidence,
    int page,
    String section,
    String detector,
    List<String> relationships
) {
    public static FieldResult from(SemanticField field) {
        return new FieldResult(
            field.name(), field.inferredType(), field.value(), field.box(),
            field.confidence(), field.page(), field.sectionNodeId(), field.detectorId(), field.relatedFieldIds()
        );
    }
}
