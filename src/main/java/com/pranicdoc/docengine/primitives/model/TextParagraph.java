package com.pranicdoc.docengine.primitives.model;

import com.pranicdoc.docengine.geometry.BoundingBox;

import java.util.List;

public record TextParagraph(BoundingBox box, int page, List<TextLine> lines) {
}
