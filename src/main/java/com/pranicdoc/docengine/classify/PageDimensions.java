package com.pranicdoc.docengine.classify;

/** Page size in raw PDF points (MediaBox), plus its rotation in degrees clockwise as stored in the PDF. */
public record PageDimensions(double widthPts, double heightPts, int rotationDegrees) {
}
