package com.pranicdoc.docengine.core;

import com.pranicdoc.docengine.classify.DocumentClassifier;
import com.pranicdoc.docengine.classify.PdfDocumentClassifier;
import com.pranicdoc.docengine.confidence.ConfidenceEngine;
import com.pranicdoc.docengine.confidence.impl.WeightedAverageConfidenceAggregator;
import com.pranicdoc.docengine.detect.CandidateMerger;
import com.pranicdoc.docengine.detect.DetectionCandidate;
import com.pranicdoc.docengine.detect.DetectorRegistry;
import com.pranicdoc.docengine.detect.impl.ImagePlaceholderDetector;
import com.pranicdoc.docengine.detect.impl.LabelDetector;
import com.pranicdoc.docengine.detect.impl.RectangleDetector;
import com.pranicdoc.docengine.detect.impl.TableDetector;
import com.pranicdoc.docengine.detect.impl.UnderlineDetector;
import com.pranicdoc.docengine.detect.impl.WhitespaceDetector;
import com.pranicdoc.docengine.graph.DocumentGraph;
import com.pranicdoc.docengine.graph.LayoutGraphBuilder;
import com.pranicdoc.docengine.layout.DefaultLayoutAnalyzer;
import com.pranicdoc.docengine.layout.DocumentLayout;
import com.pranicdoc.docengine.layout.LayoutAnalyzer;
import com.pranicdoc.docengine.layout.LayoutNode;
import com.pranicdoc.docengine.layout.LayoutNodeType;
import com.pranicdoc.docengine.layout.PageContent;
import com.pranicdoc.docengine.output.DocumentResult;
import com.pranicdoc.docengine.primitives.extractors.ImagePrimitiveExtractor;
import com.pranicdoc.docengine.primitives.extractors.TextPrimitiveExtractor;
import com.pranicdoc.docengine.primitives.extractors.VectorPrimitiveExtractor;
import com.pranicdoc.docengine.primitives.model.ImagePrimitive;
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
 * Roadmap Phase 2 wiring: classify -> full per-page primitive extraction -> DefaultLayoutAnalyzer's
 * real tree -> LayoutGraphBuilder's DocumentGraph -> the full native-PDF detector set, run against
 * the tree's real scopes (Stage 4 rewiring — no longer against one flat PAGE-level attribute bag):
 *
 *   - leaf nodes (ROW leaves + header/footer bands) get Rectangle/Underline/Label/Whitespace/
 *     ImagePlaceholder — each already no-ops on scopes lacking the attributes it needs
 *   - non-leaf SECTION nodes (whose children are ROWs) get TableDetector, which reads its own
 *     ROW children's attributes directly and no-ops with zero ROW children
 *
 * COLUMN nodes are deliberately excluded from both passes — their textLines/vectorPrimitives
 * attributes exist only so DefaultLayoutAnalyzer's row-clustering can read them; scanning COLUMN
 * nodes too would re-detect every leaf's content a second time under the wrong provenance.
 */
public class PipelineRunner {

    private final DocumentClassifier classifier = new PdfDocumentClassifier();
    private final TextPrimitiveExtractor textExtractor = new TextPrimitiveExtractor();
    private final VectorPrimitiveExtractor vectorExtractor = new VectorPrimitiveExtractor();
    private final ImagePrimitiveExtractor imageExtractor = new ImagePrimitiveExtractor();
    private final LayoutAnalyzer layoutAnalyzer = new DefaultLayoutAnalyzer();
    private final LayoutGraphBuilder graphBuilder = new LayoutGraphBuilder();
    private final DetectorRegistry detectorRegistry = new DetectorRegistry(List.of(
        new RectangleDetector(),
        new UnderlineDetector(),
        new LabelDetector(),
        new WhitespaceDetector(),
        new ImagePlaceholderDetector(),
        new TableDetector()
    ));
    private final CandidateMerger merger = new CandidateMerger();
    private final SemanticResolver resolver;

    public PipelineRunner() {
        ConfidenceEngine confidenceEngine = new ConfidenceEngine(new WeightedAverageConfidenceAggregator());
        this.resolver = new NaiveProximityResolver(confidenceEngine);
    }

    public DocumentResult run(PDDocument document, String documentId, EngineConfig config) throws IOException {
        PipelineContext ctx = new PipelineContext(config);
        ctx.setMetadata(classifier.classify(document));

        List<PageContent> pages = new ArrayList<>();
        for (int page = 0; page < document.getNumberOfPages(); page++) {
            ctx.setCurrentPage(page);
            List<TextLine> textLines = textExtractor.extract(document, page, ctx);
            List<VectorPrimitive> vectorPrimitives = vectorExtractor.extract(document, page, ctx);
            List<ImagePrimitive> images = imageExtractor.extract(document, page, ctx);
            pages.add(new PageContent(page, ctx.metadata().pages().get(page), textLines, vectorPrimitives, images));
        }

        DocumentLayout layout = layoutAnalyzer.analyzeDocument(pages, ctx);
        DocumentGraph graph = graphBuilder.build(layout.pageRoots(), layout.readingOrders());
        ctx.setDocumentLayout(layout);
        ctx.setDocumentGraph(graph);

        List<DetectionCandidate> allCandidates = new ArrayList<>();
        for (LayoutNode pageRoot : layout.pageRoots()) {
            ctx.setCurrentPage(pageRoot.page());
            for (LayoutNode scope : detectionScopes(pageRoot)) {
                allCandidates.addAll(detectorRegistry.detectAll(scope, ctx));
            }
        }

        List<DetectionCandidate> merged = merger.merge(allCandidates);
        List<SemanticField> fields = resolver.resolve(merged, ctx);

        return DocumentResult.of(documentId, document.getNumberOfPages(), fields);
    }

    private List<LayoutNode> detectionScopes(LayoutNode pageRoot) {
        List<LayoutNode> scopes = new ArrayList<>(pageRoot.collect(LayoutNode::isLeaf));
        scopes.addAll(pageRoot.collect(n -> n.type() == LayoutNodeType.SECTION && !n.isLeaf()));
        return scopes;
    }
}
