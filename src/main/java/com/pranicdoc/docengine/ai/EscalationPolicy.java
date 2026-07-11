package com.pranicdoc.docengine.ai;

import com.pranicdoc.docengine.core.PipelineContext;
import com.pranicdoc.docengine.semantic.SemanticField;

public interface EscalationPolicy {
    boolean shouldEscalate(SemanticField field, PipelineContext ctx);
}
