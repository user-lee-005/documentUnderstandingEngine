package com.pranicdoc.docengine.layout.detectors;

import com.pranicdoc.docengine.core.PipelineContext;
import com.pranicdoc.docengine.layout.LayoutNode;

import java.util.List;

/** Detects structurally identical repeating blocks (e.g. every row in a scored checklist). Roadmap Phase 1. */
public class RepeatedBlockDetector implements LayoutDetector {

    @Override
    public List<LayoutNode> detect(LayoutNode pageNode, PipelineContext ctx) {
        throw new UnsupportedOperationException("RepeatedBlockDetector is not implemented yet — see Roadmap Phase 1");
    }
}
