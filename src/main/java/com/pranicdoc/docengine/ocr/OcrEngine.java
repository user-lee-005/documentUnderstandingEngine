package com.pranicdoc.docengine.ocr;

import java.awt.image.BufferedImage;

/** Backed by Tesseract/Tess4J. Roadmap Phase 4. */
public interface OcrEngine {
    OcrResult recognize(BufferedImage region, OcrHints hints);
}
