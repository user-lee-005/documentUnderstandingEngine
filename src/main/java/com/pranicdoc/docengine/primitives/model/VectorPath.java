package com.pranicdoc.docengine.primitives.model;

import com.pranicdoc.docengine.geometry.BoundingBox;

import java.util.List;

/** The raw path a rectangle/line/curve primitive was extracted from, kept for debug rendering and re-analysis. */
public record VectorPath(BoundingBox box, int page, List<LinePrimitive> segments, List<CurvePrimitive> curves, boolean closed) {
}
