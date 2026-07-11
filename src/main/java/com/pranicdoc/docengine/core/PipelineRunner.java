package com.pranicdoc.docengine.core;

import com.pranicdoc.docengine.classify.DocumentClassifier;
import com.pranicdoc.docengine.classify.PdfDocumentClassifier;
import com.pranicdoc.docengine.confidence.ConfidenceEngine;
import com.pranicdoc.docengine.confidence.impl.WeightedAverageConfidenceAggregator;
import com.pranicdoc.docengine.detect.CandidateMerger;
import com.pranicdoc.docengine.detect.DetectionCandidate;
import com.pranicdoc.docengine.detect.DetectorRegistry;
import com.pranicdoc.docengine.detect.impl.LabelDetector;
import com.pranicdoc.docengine.detect.impl.RectangleDetector;
import com.pranicdoc.docengine.layout.LayoutAnalyzer;
import com.pranicdoc.docengine.layout.LayoutNode;
import com.pranicdoc.docengine.layout.MinimalLayoutAnalyzer;
import com.pranicdoc.docengine.output.DocumentResult;
import com.pranicdoc.docengine.primitives.extractors.TextPrimitiveExtractor;
import com.pranicdoc.docengine.primitives.extractors.VectorPrimitiveExtractor;
import com.pranicdoc.docengine.primitives.model.TextLine;
import com.pranicdoc.docengine.primitives.model.VectorPrimitive;
import com.pranicdoc.docengine.semantic.SemanticField;
import com.pranicdoc.docengine.semantic.SemanticResolver;
import com.pranicdoc.docengine.semantic.impl.NaiveProximityResolver;
import org.apache.pdfbox.pdmodel.PDDocument;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Roadmap Phase 0 wiring: classify -> per-page text/vector primitives -> a single-PAGE
 * layout scope -> RectangleDetector + LabelDetector -> merge -> naive proximity pairing.
 * Stage 3's real layout tree, the full detector set, the DocumentGraph, and the Stage 6
 * rule chain all replace pieces of this incrementally in later phases without changing
 * this class's public contract (run(PDDocument, String) -> DocumentResult).
 */
public class PipelineRunner {

    private final DocumentClassifier classifier = new PdfDocumentClassifier();
    private final TextPrimitiveExtractor textExtractor = new TextPrimitiveExtractor();
    private final VectorPrimitiveExtractor vectorExtractor = new VectorPrimitiveExtractor();
    private final LayoutAnalyzer layoutAnalyzer = new MinimalLayoutAnalyzer();
    private final DetectorRegistry detectorRegistry = new DetectorRegistry(List.of(new RectangleDetector(), new LabelDetector()));
    private final CandidateMerger merger = new CandidateMerger();
    private final SemanticResolver resolver;

    public PipelineRunner() {
        ConfidenceEngine confidenceEngine = new ConfidenceEngine(new WeightedAverageConfidenceAggregator());
        this.resolver = new NaiveProximityResolver(confidenceEngine);
    }

    public DocumentResult run(PDDocument document, String documentId, EngineConfig config) throws IOException {
        PipelineContext ctx = new PipelineContext(config);
        ctx.setMetadata(classifier.classify(document));

        List<DetectionCandidate> allCandidates = new ArrayList<>();
        for (int page = 0; page < document.getNumberOfPages(); page++) {
            ctx.setCurrentPage(page);
            List<TextLine> textLines = textExtractor.extract(document, page, ctx);
            List<VectorPrimitive> vectorPrimitives = vectorExtractor.extract(document, page, ctx);
            LayoutNode pageNode = layoutAnalyzer.analyzePage(page, textLines, vectorPrimitives, ctx);
            allCandidates.addAll(detectorRegistry.detectAll(pageNode, ctx));
        }

        List<DetectionCandidate> merged = merger.merge(allCandidates);
        List<SemanticField> fields = resolver.resolve(merged, ctx);

        return DocumentResult.of(documentId, document.getNumberOfPages(), fields);
    }
}
