package com.pranicdoc.docengine.template;

import com.pranicdoc.docengine.semantic.SemanticField;

import java.util.List;

/** Persisted layout + field definitions from a human-validated document. Roadmap Phase 5. */
public record LearnedTemplate(String templateId, TemplateFingerprint fingerprint, List<SemanticField> fieldDefinitions) {
}
