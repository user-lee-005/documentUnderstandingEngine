package com.pranicdoc.docengine.geometry;

/**
 * Raw PDF user-space coordinates, bottom-left origin, in PDF points.
 * No Y-flip or pixel conversion happens anywhere in this engine — that is
 * exclusively the responsibility of a consuming renderer/UI, at the render boundary.
 */
public record BoundingBox(double x0, double y0, double x1, double y1) {

    public BoundingBox {
        if (x1 < x0 || y1 < y0) {
            throw new IllegalArgumentException("BoundingBox upper bound must be >= lower bound: " + x0 + "," + y0 + " -> " + x1 + "," + y1);
        }
    }

    public static BoundingBox of(double ax, double ay, double bx, double by) {
        return new BoundingBox(Math.min(ax, bx), Math.min(ay, by), Math.max(ax, bx), Math.max(ay, by));
    }

    public double width() {
        return x1 - x0;
    }

    public double height() {
        return y1 - y0;
    }

    public double centerX() {
        return (x0 + x1) / 2.0;
    }

    public double centerY() {
        return (y0 + y1) / 2.0;
    }

    public double area() {
        return width() * height();
    }

    public boolean overlaps(BoundingBox other) {
        return x0 < other.x1 && x1 > other.x0 && y0 < other.y1 && y1 > other.y0;
    }

    /** Intersection-over-union, 0 when the boxes don't overlap at all. */
    public double iou(BoundingBox other) {
        if (!overlaps(other)) {
            return 0.0;
        }
        double ix0 = Math.max(x0, other.x0);
        double iy0 = Math.max(y0, other.y0);
        double ix1 = Math.min(x1, other.x1);
        double iy1 = Math.min(y1, other.y1);
        double intersection = (ix1 - ix0) * (iy1 - iy0);
        double union = area() + other.area() - intersection;
        return union <= 0.0 ? 0.0 : intersection / union;
    }

    public double centerDistanceTo(BoundingBox other) {
        double dx = centerX() - other.centerX();
        double dy = centerY() - other.centerY();
        return Math.sqrt(dx * dx + dy * dy);
    }
}
