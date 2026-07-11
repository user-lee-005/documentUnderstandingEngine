package com.pranicdoc.docengine.detect.impl;

import com.pranicdoc.docengine.core.PipelineContext;
import com.pranicdoc.docengine.detect.DetectionCandidate;
import com.pranicdoc.docengine.detect.FieldCandidateDetector;
import com.pranicdoc.docengine.layout.LayoutNode;

import java.util.List;

/** Fine-grained cell/row/column detection within a coarse TableRegionDetector region — lattice/stream heuristics, per Camelot. Roadmap Phase 2. */
public class TableDetector implements FieldCandidateDetector {

    public static final String ID = "table-detector";

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
        throw new UnsupportedOperationException("TableDetector is not implemented yet — see Roadmap Phase 2");
    }
}
