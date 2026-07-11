package com.pranicdoc.docengine.primitives.extractors;

import com.pranicdoc.docengine.core.PipelineContext;
import com.pranicdoc.docengine.primitives.model.ImagePrimitive;
import org.apache.pdfbox.pdmodel.PDDocument;

import java.io.IOException;
import java.util.List;

/** Embedded raster image (logo/photo/full-page scan body) extraction — Roadmap Phase 1. */
public class ImagePrimitiveExtractor implements PrimitiveExtractor<ImagePrimitive> {

    @Override
    public List<ImagePrimitive> extract(PDDocument document, int pageIndex, PipelineContext ctx) {
        throw new UnsupportedOperationException("ImagePrimitiveExtractor is not implemented yet — see Roadmap Phase 1");
    }
}
