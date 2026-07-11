package com.pranicdoc.docengine.layout.detectors;

import com.pranicdoc.docengine.core.PipelineContext;
import com.pranicdoc.docengine.layout.LayoutNode;

import java.util.List;

/** Refines a coarse PAGE node into more precise structural nodes (columns, sections, ...). Roadmap Phase 1. */
public interface LayoutDetector {
    List<LayoutNode> detect(LayoutNode pageNode, PipelineContext ctx);
}
