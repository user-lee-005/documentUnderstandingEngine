package com.pranicdoc.docengine.detect.impl;

import com.pranicdoc.docengine.core.PipelineContext;
import com.pranicdoc.docengine.detect.DetectionCandidate;
import com.pranicdoc.docengine.detect.FieldCandidateDetector;
import com.pranicdoc.docengine.layout.LayoutNode;

import java.util.List;

/** Finds empty-field candidates: rectangular gaps bounded by text/lines on multiple sides. Roadmap Phase 2. */
public class WhitespaceDetector implements FieldCandidateDetector {

    public static final String ID = "whitespace-detector";

    @Override
    public String detectorId() {
        return ID;
    }

    @Override
    public boolean isApplicable(PipelineContext ctx) {
        return false;
    }

    @Override
    public List<DetectionCandidate> detect(LayoutNode scope, PipelineContext ctx) {
        throw new UnsupportedOperationException("WhitespaceDetector is not implemented yet — see Roadmap Phase 2");
    }
}
