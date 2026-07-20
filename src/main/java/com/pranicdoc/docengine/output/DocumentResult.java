package com.pranicdoc.docengine.output;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.pranicdoc.docengine.semantic.SemanticField;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The final Root -> pages -> sections -> fields result — the same shape as the gold-schema
 * contract in src/test/resources/gold. Section names come from detected headings; fields on a
 * page with no heading above them land in a section with a null name.
 */
public record DocumentResult(
    String documentId,
    int pageCount,
    List<PageResult> pages,
    Instant generatedAt
) {

    public record PageResult(int page, List<SectionResult> sections) {
    }

    public record SectionResult(String name, List<FieldResult> fields) {
    }

    public static DocumentResult of(String documentId, int pageCount, List<SemanticField> fields) {
        Map<Integer, Map<String, List<FieldResult>>> byPageAndSection = new LinkedHashMap<>();
        for (SemanticField field : fields) {
            byPageAndSection
                .computeIfAbsent(field.page(), k -> new LinkedHashMap<>())
                .computeIfAbsent(field.sectionName() == null ? "" : field.sectionName(), k -> new ArrayList<>())
                .add(FieldResult.from(field));
        }

        List<PageResult> pages = new ArrayList<>();
        for (Map.Entry<Integer, Map<String, List<FieldResult>>> pageEntry : byPageAndSection.entrySet()) {
            List<SectionResult> sections = new ArrayList<>();
            for (Map.Entry<String, List<FieldResult>> sectionEntry : pageEntry.getValue().entrySet()) {
                sections.add(new SectionResult(sectionEntry.getKey().isEmpty() ? null : sectionEntry.getKey(),
                        sectionEntry.getValue()));
            }
            pages.add(new PageResult(pageEntry.getKey(), sections));
        }
        pages.sort((a, b) -> Integer.compare(a.page(), b.page()));
        return new DocumentResult(documentId, pageCount, pages, Instant.now());
    }

    /** All fields across all pages/sections, flattened — convenience for counting and simple consumers. */
    @JsonIgnore
    public List<FieldResult> allFields() {
        List<FieldResult> all = new ArrayList<>();
        for (PageResult page : pages) {
            for (SectionResult section : page.sections()) {
                all.addAll(section.fields());
            }
        }
        return all;
    }
}
