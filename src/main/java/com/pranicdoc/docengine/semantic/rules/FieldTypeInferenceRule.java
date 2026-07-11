package com.pranicdoc.docengine.semantic.rules;

import com.pranicdoc.docengine.core.PipelineContext;
import com.pranicdoc.docengine.detect.DetectionCandidate;
import com.pranicdoc.docengine.semantic.SemanticField;

import java.util.List;

/** Infers field type (date, checkbox-group, multiline text, ...) from label wording and candidate shape. Roadmap Phase 3. */
public class FieldTypeInferenceRule implements SemanticRule {

    @Override
    public List<SemanticField> apply(List<DetectionCandidate> candidates, PipelineContext ctx) {
        throw new UnsupportedOperationException("FieldTypeInferenceRule is not implemented yet — see Roadmap Phase 3");
    }
}
