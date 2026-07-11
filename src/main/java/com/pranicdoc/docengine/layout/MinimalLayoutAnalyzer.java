package com.pranicdoc.docengine.layout;

import com.pranicdoc.docengine.core.PipelineContext;
import com.pranicdoc.docengine.geometry.BoundingBox;
import com.pranicdoc.docengine.primitives.model.TextLine;
import com.pranicdoc.docengine.primitives.model.VectorPrimitive;

import java.util.List;

/**
 * Roadmap Phase 0's layout analyzer: wraps every primitive on a page into a single PAGE node,
 * with no column/section/row/block subdivision. Detectors and the resolver still work against
 * this — they just get one big scope instead of several precise ones. Real segmentation
 * (ColumnDetector, HeaderFooterDetector, ...) is Phase 1.
 */
public class MinimalLayoutAnalyzer implements LayoutAnalyzer {

    @Override
    public LayoutNode analyzePage(int pageIndex, List<TextLine> textLines, List<VectorPrimitive> vectorPrimitives, PipelineContext ctx) {
        double pageWidth = ctx.metadata().pages().get(pageIndex).widthPts();
        double pageHeight = ctx.metadata().pages().get(pageIndex).heightPts();

        LayoutNode pageNode = new LayoutNode(
            "page-" + pageIndex,
            LayoutNodeType.PAGE,
            new BoundingBox(0, 0, pageWidth, pageHeight),
            pageIndex
        );
        pageNode.putAttribute("textLines", textLines);
        pageNode.putAttribute("vectorPrimitives", vectorPrimitives);
        return pageNode;
    }
}
