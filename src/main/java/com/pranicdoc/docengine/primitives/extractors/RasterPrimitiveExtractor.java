package com.pranicdoc.docengine.primitives.extractors;

import com.pranicdoc.docengine.core.PipelineContext;
import com.pranicdoc.docengine.primitives.model.ImagePrimitive;
import org.apache.pdfbox.pdmodel.PDDocument;

import java.util.List;

/**
 * Rasterizes scanned/hybrid pages for OCR/CV consumption. Every pixel coordinate this
 * produces must be converted back to raw PDF points (pixel / dpi * 72) before leaving
 * this class — no downstream code may see pixel coordinates. Roadmap Phase 4.
 */
public class RasterPrimitiveExtractor implements PrimitiveExtractor<ImagePrimitive> {

    @Override
    public List<ImagePrimitive> extract(PDDocument document, int pageIndex, PipelineContext ctx) {
        throw new UnsupportedOperationException("RasterPrimitiveExtractor is not implemented yet — see Roadmap Phase 4");
    }
}
