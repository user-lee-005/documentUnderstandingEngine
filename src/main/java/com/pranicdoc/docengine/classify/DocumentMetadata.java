package com.pranicdoc.docengine.classify;

import java.util.List;

public record DocumentMetadata(
    int pageCount,
    List<PageDimensions> pages,
    boolean hasEmbeddedImages,
    boolean hasVectorGraphics,
    int totalExtractedTextChars,
    DocumentType documentType
) {
}
