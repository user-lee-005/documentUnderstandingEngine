package com.pranicdoc.docengine.primitives.extractors;

import com.pranicdoc.docengine.core.PipelineContext;
import org.apache.pdfbox.pdmodel.PDDocument;

import java.io.IOException;
import java.util.List;

public interface PrimitiveExtractor<T> {
    List<T> extract(PDDocument document, int pageIndex, PipelineContext ctx) throws IOException;
}
