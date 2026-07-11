package com.pranicdoc.docengine.primitives.model;

import com.pranicdoc.docengine.geometry.BoundingBox;

/** A stroked or filled-and-stroked rectangular path, as drawn in the page content stream. */
public record RectanglePrimitive(BoundingBox box, int page, boolean filled, double strokeWidth) implements VectorPrimitive {
}
