package com.pranicdoc.docengine.detect.impl;

import com.pranicdoc.docengine.core.PipelineContext;
import com.pranicdoc.docengine.detect.DetectionCandidate;
import com.pranicdoc.docengine.detect.FieldCandidateDetector;
import com.pranicdoc.docengine.layout.LayoutNode;

import java.util.List;

/** OpenCV-backed handwritten-vs-printed region classification, only applicable on SCANNED/HYBRID pages. Roadmap Phase 4. */
public class HandwritingRegionDetector implements FieldCandidateDetector {

    public static final String ID = "handwriting-region-detector";

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
        throw new UnsupportedOperationException("HandwritingRegionDetector is not implemented yet — see Roadmap Phase 4");
    }
}
