package com.pranicdoc.docengine.primitives.model;

import com.pranicdoc.docengine.geometry.BoundingBox;

/** A rectangular region with no text/vector/image content — a candidate gap for column/section detection or an empty field. */
public record WhitespaceRegion(BoundingBox box, int page) {
}
