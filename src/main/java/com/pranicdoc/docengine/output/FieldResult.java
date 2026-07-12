package com.pranicdoc.docengine.output;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.pranicdoc.docengine.geometry.BoundingBox;
import com.pranicdoc.docengine.semantic.SemanticField;

import java.util.List;

/** One field of the final result, mirroring the gold-schema contract (gold/README.md). */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record FieldResult(
    String label,
    String type,
    String value,
    BoundingBox labelBox,
    BoundingBox valueBox,
    int page,
    double confidence,
    String detector,
    List<OptionResult> options,
    List<ItemResult> items
) {

    public record OptionResult(String name, BoundingBox box, boolean selected) {
    }

    public record ItemResult(int index, BoundingBox labelBox, BoundingBox valueBox, String value) {
    }

    public static FieldResult from(SemanticField field) {
        return new FieldResult(
            field.name(), field.inferredType(), field.value(),
            field.labelBox(), field.box(), field.page(), field.confidence(), field.detectorId(),
            field.options().stream().map(o -> new OptionResult(o.name(), o.box(), o.selected())).toList(),
            field.items().stream().map(i -> new ItemResult(i.index(), i.labelBox(), i.valueBox(), i.value())).toList()
        );
    }
}
