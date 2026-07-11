package com.pranicdoc.docengine.layout.detectors;

import com.pranicdoc.docengine.core.PipelineContext;
import com.pranicdoc.docengine.layout.LayoutNode;

import java.util.List;

/** Detects repeated top/bottom-of-page bands across a multi-page document. Roadmap Phase 1. */
public class HeaderFooterDetector implements LayoutDetector {

    @Override
    public List<LayoutNode> detect(LayoutNode pageNode, PipelineContext ctx) {
        throw new UnsupportedOperationException("HeaderFooterDetector is not implemented yet — see Roadmap Phase 1");
    }
}
