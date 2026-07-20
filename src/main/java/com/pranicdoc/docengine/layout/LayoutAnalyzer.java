package com.pranicdoc.docengine.layout;

import com.pranicdoc.docengine.core.PipelineContext;

import java.util.List;

/** Document-level by design: header/footer detection needs every page at once, not one page in isolation. */
public interface LayoutAnalyzer {
    DocumentLayout analyzeDocument(List<PageContent> pages, PipelineContext ctx);
}
