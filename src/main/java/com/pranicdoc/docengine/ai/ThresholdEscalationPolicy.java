package com.pranicdoc.docengine.ai;

import com.pranicdoc.docengine.core.PipelineContext;
import com.pranicdoc.docengine.semantic.SemanticField;

/** Deliberately plain and deterministic — escalation must be auditable, not itself a model. */
public class ThresholdEscalationPolicy implements EscalationPolicy {

    @Override
    public boolean shouldEscalate(SemanticField field, PipelineContext ctx) {
        return field.confidence() < ctx.config().lowConfidenceEscalationThreshold();
    }
}
