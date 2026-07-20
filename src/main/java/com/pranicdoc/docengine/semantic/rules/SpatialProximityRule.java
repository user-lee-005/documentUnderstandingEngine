package com.pranicdoc.docengine.semantic.rules;

import com.pranicdoc.docengine.core.PipelineContext;
import com.pranicdoc.docengine.detect.DetectionCandidate;
import com.pranicdoc.docengine.semantic.SemanticField;

import java.util.List;

/** Graph-based generalization of NaiveProximityResolver's nearest-neighbor pairing. Roadmap Phase 3. */
public class SpatialProximityRule implements SemanticRule {

    @Override
    public List<SemanticField> apply(List<DetectionCandidate> candidates, PipelineContext ctx) {
        throw new UnsupportedOperationException("SpatialProximityRule is not implemented yet — see Roadmap Phase 3");
    }
}
