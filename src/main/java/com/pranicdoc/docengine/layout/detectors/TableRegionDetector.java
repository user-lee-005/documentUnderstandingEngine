package com.pranicdoc.docengine.layout.detectors;

import com.pranicdoc.docengine.core.PipelineContext;
import com.pranicdoc.docengine.layout.LayoutNode;

import java.util.List;

/**
 * Coarse table-region marking, as a distinct Stage 3 pass. Deferred again past Phase 2: Stage 4's
 * TableDetector now finds table cells directly by scanning repeatingGroupId-tagged row groups
 * (Phase 1's RepeatedBlockDetector) within a SECTION, without needing a separate coarse-region
 * pass first. Revisit only if something downstream (debug rendering, the relationship graph)
 * ends up needing an explicit "this region is a table" node that TableDetector's cell-level
 * candidates don't already imply.
 */
public class TableRegionDetector implements LayoutDetector {

    @Override
    public List<LayoutNode> detect(LayoutNode pageNode, PipelineContext ctx) {
        throw new UnsupportedOperationException("TableRegionDetector is not implemented — superseded by Stage 4's TableDetector, see class Javadoc");
    }
}
