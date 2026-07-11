package com.pranicdoc.docengine.detect.impl;

import com.pranicdoc.docengine.core.PipelineContext;
import com.pranicdoc.docengine.detect.DetectionCandidate;
import com.pranicdoc.docengine.detect.FieldCandidateDetector;
import com.pranicdoc.docengine.layout.LayoutNode;

import java.util.List;

/** OpenCV-backed circular-glyph detection, clustered into mutually-exclusive groups. Roadmap Phase 4. */
public class RadioGroupDetector implements FieldCandidateDetector {

    public static final String ID = "radio-group-detector";

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
        throw new UnsupportedOperationException("RadioGroupDetector is not implemented yet — see Roadmap Phase 4");
    }
}
