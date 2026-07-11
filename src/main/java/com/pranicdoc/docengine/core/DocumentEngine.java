package com.pranicdoc.docengine.core;

import com.pranicdoc.docengine.output.DocumentResult;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;

import java.io.File;
import java.io.IOException;

/** Entry point. {@code new DocumentEngine().process(pdfFile)} runs the currently-wired pipeline end to end. */
public class DocumentEngine {

    private final EngineConfig config;
    private final PipelineRunner runner;

    public DocumentEngine() {
        this(EngineConfig.defaults());
    }

    public DocumentEngine(EngineConfig config) {
        this.config = config;
        this.runner = new PipelineRunner();
    }

    public DocumentResult process(File pdfFile) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdfFile)) {
            return runner.run(document, pdfFile.getName(), config);
        }
    }
}
