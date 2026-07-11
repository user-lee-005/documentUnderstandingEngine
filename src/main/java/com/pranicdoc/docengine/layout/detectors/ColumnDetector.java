package com.pranicdoc.docengine.layout.detectors;

import com.pranicdoc.docengine.core.PipelineContext;
import com.pranicdoc.docengine.layout.LayoutNode;

import java.util.List;

/** Detects multi-column layout via vertical whitespace gutters. Roadmap Phase 1. */
public class ColumnDetector implements LayoutDetector {

    @Override
    public List<LayoutNode> detect(LayoutNode pageNode, PipelineContext ctx) {
        throw new UnsupportedOperationException("ColumnDetector is not implemented yet — see Roadmap Phase 1");
    }
}
