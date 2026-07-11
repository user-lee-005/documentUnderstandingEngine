package com.pranicdoc.docengine.layout.detectors;

import com.pranicdoc.docengine.core.PipelineContext;
import com.pranicdoc.docengine.layout.LayoutNode;

import java.util.List;

/**
 * Coarse table-region detection (fine-grained cell/row/column detection is Stage 4's TableDetector).
 * Deliberately deferred past Phase 1's column/header/footer/repeated-block work — Roadmap Phase 2.
 */
public class TableRegionDetector implements LayoutDetector {

    @Override
    public List<LayoutNode> detect(LayoutNode pageNode, PipelineContext ctx) {
        throw new UnsupportedOperationException("TableRegionDetector is not implemented yet — see Roadmap Phase 2");
    }
}
