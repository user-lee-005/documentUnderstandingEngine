package com.pranicdoc.docengine.core;

import com.pranicdoc.docengine.debug.DebugExport;
import com.pranicdoc.docengine.debug.DebugOverlayRenderer;
import com.pranicdoc.docengine.debug.DebugRun;
import com.pranicdoc.docengine.debug.OverlayStyle;
import com.pranicdoc.docengine.debug.PdfDebugOverlayRenderer;
import com.pranicdoc.docengine.output.DocumentResult;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;

import java.io.File;
import java.io.IOException;

/** Entry point. {@code new DocumentEngine().process(pdfFile)} runs the currently-wired pipeline end to end. */
public class DocumentEngine {

    private final EngineConfig config;
    private final PipelineRunner runner;
    private final DebugOverlayRenderer debugRenderer = new PdfDebugOverlayRenderer();

    public DocumentEngine() {
        this(EngineConfig.defaults());
    }

    public DocumentEngine(EngineConfig config) {
        this.config = config;
        this.runner = new PipelineRunner();
    }

    public DocumentResult process(File pdfFile) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdfFile)) {
            return runner.run(document, pdfFile.getName(), config).result();
        }
    }

    /**
     * Same pipeline run as {@link #process}, plus a confidence-colored annotated PDF/PNGs and a
     * verbose JSON dump of every merged candidate (not just the fields that survived Stage 6's
     * pairing) — see {@link DebugExport}. Runs the pipeline once; the document stays open only
     * long enough for the debug renderer to read it.
     */
    public DebugRun processWithDebug(File pdfFile) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdfFile)) {
            PipelineRunResult runResult = runner.run(document, pdfFile.getName(), config);
            DebugExport export = debugRenderer.render(document, runResult.mergedCandidates(), runResult.result(), OverlayStyle.defaults());
            return new DebugRun(runResult.result(), export);
        }
    }
}
