package com.pranicdoc.docengine.layout;

import com.pranicdoc.docengine.core.PipelineContext;
import com.pranicdoc.docengine.primitives.model.TextLine;
import com.pranicdoc.docengine.primitives.model.VectorPrimitive;

import java.util.List;

public interface LayoutAnalyzer {
    LayoutNode analyzePage(int pageIndex, List<TextLine> textLines, List<VectorPrimitive> vectorPrimitives, PipelineContext ctx);
}
