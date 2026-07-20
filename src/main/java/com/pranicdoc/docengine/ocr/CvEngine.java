package com.pranicdoc.docengine.ocr;

import com.pranicdoc.docengine.geometry.BoundingBox;

import java.awt.image.BufferedImage;
import java.util.List;

/** Backed by OpenCV — contour/line/shape operations for checkbox/radio/signature/handwriting detectors. Roadmap Phase 4. */
public interface CvEngine {
    List<BoundingBox> detectContours(BufferedImage region);
}
