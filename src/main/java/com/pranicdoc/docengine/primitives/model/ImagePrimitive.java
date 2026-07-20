package com.pranicdoc.docengine.primitives.model;

import com.pranicdoc.docengine.geometry.BoundingBox;

/** An embedded raster image (photo, logo, scanned page body) placed on a page. Extraction is Phase 1. */
public record ImagePrimitive(BoundingBox box, int page, int pixelWidth, int pixelHeight, String format) {
}
