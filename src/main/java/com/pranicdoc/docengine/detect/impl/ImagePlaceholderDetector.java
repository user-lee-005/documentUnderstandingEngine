package com.pranicdoc.docengine.detect.impl;

import com.pranicdoc.docengine.core.PipelineContext;
import com.pranicdoc.docengine.detect.DetectionCandidate;
import com.pranicdoc.docengine.detect.FieldCandidateDetector;
import com.pranicdoc.docengine.layout.LayoutNode;

import java.util.List;

/** Detects empty bordered regions sized/labeled like a photo or logo slot. Roadmap Phase 2. */
public class ImagePlaceholderDetector implements FieldCandidateDetector {

    public static final String ID = "image-placeholder-detector";

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
        throw new UnsupportedOperationException("ImagePlaceholderDetector is not implemented yet — see Roadmap Phase 2");
    }
}
