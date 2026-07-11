package com.pranicdoc.docengine.primitives.model;

import com.pranicdoc.docengine.geometry.BoundingBox;

/** A cubic Bezier segment (PDF `c` operator) from a page content stream path. */
public record CurvePrimitive(
    double x0, double y0,
    double x1, double y1,
    double x2, double y2,
    double x3, double y3,
    int page
) implements VectorPrimitive {

    /** Bounding box of the 4 control points — not the tight curve envelope, sufficient for candidate detection. */
    @Override
    public BoundingBox box() {
        double minX = Math.min(Math.min(x0, x1), Math.min(x2, x3));
        double maxX = Math.max(Math.max(x0, x1), Math.max(x2, x3));
        double minY = Math.min(Math.min(y0, y1), Math.min(y2, y3));
        double maxY = Math.max(Math.max(y0, y1), Math.max(y2, y3));
        return BoundingBox.of(minX, minY, maxX, maxY);
    }
}
