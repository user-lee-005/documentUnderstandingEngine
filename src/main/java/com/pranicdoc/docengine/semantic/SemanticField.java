package com.pranicdoc.docengine.semantic;

import com.pranicdoc.docengine.geometry.BoundingBox;

import java.util.List;

/**
 * One resolved form field — Stage 6's output unit, mirroring the gold-schema contract
 * (see src/test/resources/gold/README.md): a label, the writable/written value area, and for
 * composite fields the checkbox options or repeated array items.
 */
public record SemanticField(
    String id,
    /** The printed caption, decoded ("PATIENT'S NAME"), or a derived name for label-less fields. */
    String name,
    /** text | number | date | signature | checkbox | checkbox-group | array | table */
    String inferredType,
    /** Text read from inside the value area (native PDFs); null when blank or unreadable. */
    String value,
    /** Where the value is / must be placed. Null only for checkbox-groups (options carry boxes). */
    BoundingBox box,
    /** The printed caption's box; null for fields without a detected label. */
    BoundingBox labelBox,
    int page,
    /** Nearest heading above the field ("DECLARATION"), or null when the page has none. */
    String sectionName,
    /** The structural layout-tree node id the value candidate was found in. */
    String sectionNodeId,
    double confidence,
    String detectorId,
    /** checkbox-group members; empty otherwise. */
    List<Option> options,
    /** array items (SYMPTOM 1..4); empty otherwise. */
    List<Item> items,
    List<String> relatedFieldIds
) {

    public record Option(String name, BoundingBox box, boolean selected) {
    }

    public record Item(int index, BoundingBox labelBox, BoundingBox valueBox, String value) {
    }
}
