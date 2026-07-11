package com.pranicdoc.docengine.semantic.rules;

import com.pranicdoc.docengine.core.PipelineContext;
import com.pranicdoc.docengine.detect.DetectionCandidate;
import com.pranicdoc.docengine.semantic.SemanticField;

import java.util.List;

/** One link in the Stage 6 rule chain — ordered rules, each may early-accept a pairing above a confidence threshold. Roadmap Phase 3. */
public interface SemanticRule {
    List<SemanticField> apply(List<DetectionCandidate> candidates, PipelineContext ctx);
}
