package com.pranicdoc.docengine.output;

import com.pranicdoc.docengine.semantic.SemanticField;

import java.time.Instant;
import java.util.List;

public record DocumentResult(
    String documentId,
    int pageCount,
    List<FieldResult> fields,
    Instant generatedAt
) {
    public static DocumentResult of(String documentId, int pageCount, List<SemanticField> fields) {
        return new DocumentResult(documentId, pageCount, fields.stream().map(FieldResult::from).toList(), Instant.now());
    }
}
