package com.pranicdoc.docengine.primitives.model;

import com.pranicdoc.docengine.geometry.BoundingBox;

/** A single straight-line segment from a page content stream path, in raw PDF points. */
public record LinePrimitive(double x0, double y0, double x1, double y1, int page, double strokeWidth) implements VectorPrimitive {

    public boolean isHorizontal(double toleranceY) {
        return Math.abs(y1 - y0) <= toleranceY;
    }

    public boolean isVertical(double toleranceX) {
        return Math.abs(x1 - x0) <= toleranceX;
    }

    public double length() {
        double dx = x1 - x0;
        double dy = y1 - y0;
        return Math.sqrt(dx * dx + dy * dy);
    }

    @Override
    public BoundingBox box() {
        return BoundingBox.of(x0, y0, x1, y1);
    }
}
