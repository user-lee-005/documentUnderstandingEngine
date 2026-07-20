package com.pranicdoc.docengine.layout.detectors;

import com.pranicdoc.docengine.primitives.model.TextLine;

import java.util.Set;

public record HeaderFooterResult(Set<TextLine> headerLines, Set<TextLine> footerLines) {
}
