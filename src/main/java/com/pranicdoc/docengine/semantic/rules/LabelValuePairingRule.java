package com.pranicdoc.docengine.semantic.rules;

import com.pranicdoc.docengine.core.PipelineContext;
import com.pranicdoc.docengine.detect.DetectionCandidate;
import com.pranicdoc.docengine.semantic.SemanticField;

import java.util.List;

/** Reading-order- and alignment-aware label/value pairing over the DocumentGraph. Roadmap Phase 3. */
public class LabelValuePairingRule implements SemanticRule {

    @Override
    public List<SemanticField> apply(List<DetectionCandidate> candidates, PipelineContext ctx) {
        throw new UnsupportedOperationException("LabelValuePairingRule is not implemented yet — see Roadmap Phase 3");
    }
}
