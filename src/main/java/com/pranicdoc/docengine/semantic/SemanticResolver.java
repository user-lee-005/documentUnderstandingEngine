package com.pranicdoc.docengine.semantic;

import com.pranicdoc.docengine.core.PipelineContext;
import com.pranicdoc.docengine.detect.DetectionCandidate;

import java.util.List;

public interface SemanticResolver {
    List<SemanticField> resolve(List<DetectionCandidate> mergedCandidates, PipelineContext ctx);
}
