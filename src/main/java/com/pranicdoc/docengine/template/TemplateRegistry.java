package com.pranicdoc.docengine.template;

import com.pranicdoc.docengine.semantic.SemanticField;

import java.util.List;
import java.util.Optional;

/**
 * Sits beside the pipeline, not inside it: consulted before Stage 1 (fingerprint match ->
 * skip straight to a known layout) and updated after Stage 7 (on human-validated documents).
 * Not wired into PipelineRunner yet — see Roadmap Phase 5.
 */
public class TemplateRegistry {

    private final TemplateStore store;
    private final FingerprintMatcher matcher;

    public TemplateRegistry(TemplateStore store, FingerprintMatcher matcher) {
        this.store = store;
        this.matcher = matcher;
    }

    public Optional<TemplateMatch> findBestMatch(TemplateFingerprint fingerprint, double similarityThreshold) {
        return matcher.findBestMatch(fingerprint, similarityThreshold);
    }

    public void learn(TemplateFingerprint fingerprint, List<SemanticField> resolvedFields) {
        throw new UnsupportedOperationException("Template learning is not implemented yet — see Roadmap Phase 5");
    }
}
