package com.pranicdoc.docengine.detect;

import com.pranicdoc.docengine.core.PipelineContext;
import com.pranicdoc.docengine.layout.LayoutNode;

import java.util.List;

public interface FieldCandidateDetector {

    /** Stable ID, used as the confidence-source key (EngineConfig.detectorWeights) and the debug-overlay color key. */
    String detectorId();

    boolean isApplicable(PipelineContext ctx);

    List<DetectionCandidate> detect(LayoutNode scope, PipelineContext ctx);
}
