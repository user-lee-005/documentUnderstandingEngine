package com.pranicdoc.docengine.layout;

import com.pranicdoc.docengine.classify.PageDimensions;
import com.pranicdoc.docengine.primitives.model.ImagePrimitive;
import com.pranicdoc.docengine.primitives.model.TextLine;
import com.pranicdoc.docengine.primitives.model.VectorPrimitive;

import java.util.List;

/** One page's worth of Stage 2 output, bundled for LayoutAnalyzer — which needs the whole document at once for cross-page analysis (header/footer detection). */
public record PageContent(
    int pageIndex,
    PageDimensions dimensions,
    List<TextLine> textLines,
    List<VectorPrimitive> vectorPrimitives,
    List<ImagePrimitive> images
) {
}
