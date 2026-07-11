# documentUnderstandingEngine

A self-hosted, deterministic-first Document Understanding Engine — pluggable field detectors,
a confidence-scored candidate pipeline, and a relationship graph, with AI reserved as an opt-in
fallback for low-confidence cases rather than a dependency of the primary pipeline.

Standalone module — Java 21, its own Gradle build, published independently. Not currently wired
into `pdfWorker` or any other repo in this workspace; see "Relationship to pdfWorker" below.

## Status: Roadmap Phase 2 (detector plugin expansion + Stage 4 rewiring)

What actually runs today:

```
classify (NATIVE/SCANNED/HYBRID)
  -> per page: text primitives + vector primitives + image primitives (full Stage 2)
  -> DefaultLayoutAnalyzer: real Page -> Column -> Section -> Row tree (Stage 3)
       - HeaderFooterDetector: cross-page repeated top/bottom-band lines pulled out first
       - ColumnDetector: whitespace-gutter x-axis splitting
       - row clustering by Y-proximity, section grouping by outlier gap detection
       - RepeatedBlockDetector: tags consecutive structurally-identical rows
  -> LayoutGraphBuilder: DocumentGraph with PARENT_OF/CHILD_OF/READING_ORDER_NEXT edges (Stage 5, partial)
  -> Stage 4, now run against the real tree instead of one flat page-level attribute bag:
       - leaf nodes (ROW leaves + header/footer bands) get Rectangle/Underline/Label/
         Whitespace/ImagePlaceholder — each detector no-ops on scopes lacking what it needs
       - non-leaf SECTION nodes get TableDetector, which clusters item x-centers into columns
         across the ROW children Phase 1's RepeatedBlockDetector already tagged as repeating
       - every candidate now carries scopeNodeId (which tree node it came from) for provenance
  -> CandidateMerger (IoU-based non-max suppression)
  -> NaiveProximityResolver (nearest-neighbor label/value pairing, no rule chain yet;
     SemanticField.sectionNodeId now threads the value candidate's scopeNodeId through to
     FieldResult.section — a real structural node id, not yet a semantic section name)
  -> DocumentResult (JSON via DocumentResultSerializer)
```

Every detector in the native-PDF set is real now: `RectangleDetector`, `UnderlineDetector`,
`LabelDetector`, `WhitespaceDetector`, `ImagePlaceholderDetector` (all row/leaf-scoped),
`TableDetector` (section-scoped, per-cell candidates with `row`/`col` attributes and
column-consistency-based confidence). `EngineConfig.defaults()` now assigns each detector a
default confidence weight — explicit drawn geometry (rectangles, labels) trusted most, inferred
gaps (whitespace) trusted least. Only the OCR/CV-dependent detectors (`CheckboxDetector`,
`RadioGroupDetector`, `SignatureDetector`, `HandwritingRegionDetector`, Roadmap Phase 4) and the
Stage 6 rule chain (Roadmap Phase 3) remain unimplemented.

**`COLUMN` nodes are deliberately excluded from detection** — they only exist so
`DefaultLayoutAnalyzer`'s row-clustering can read their attributes; scanning them too would
re-detect every leaf's content a second time under the wrong provenance. `TableRegionDetector`
(coarse Stage 3 table marking) stays deferred, now for a different reason than in Phase 1:
`TableDetector`'s cell-level candidates already cover the practical need without a separate
coarse-region pass — see its class Javadoc if that changes.

Entry point (unchanged):

```java
DocumentResult result = new DocumentEngine().process(new File("form.pdf"));
String json = new DocumentResultSerializer().toJson(result);
```

Every new/changed component is unit-tested in isolation (`LayoutNodeTest`, `UnderlineDetectorTest`,
`WhitespaceDetectorTest`, `ImagePlaceholderDetectorTest`, `TableDetectorTest`), and the existing
end-to-end `Phase0PipelineTest` plus every Phase 1 test still pass unchanged — meaningful proof
the rewiring correctly reaches real tree scopes, since if leaf/section collection were broken,
`Phase0PipelineTest` would find zero fields.

`BLOCK`-level tree nodes still don't exist — `ROW` remains the leaf level. Nothing in Phase 2's
detector set needed finer text-run splitting within a row, so it stays deferred until something
does.

## Building

```
gradlew.bat build
gradlew.bat test
```

## Package structure

Mirrors the pipeline: `classify` (Stage 1) → `primitives` (Stage 2) → `layout` (Stage 3) →
`detect` (Stage 4, plugin system) → `graph` (Stage 5) → `semantic` (Stage 6) → `output` (Stage 7),
with `confidence`, `template`, `ai`, and `debug` as cross-cutting concerns, and `ocr` holding
the Tesseract/OpenCV/ONNX Runtime integration surface.

## Relationship to pdfWorker

This module generalizes a pattern already proven in `pdfWorker` — `FormBoxDetector`
(a `PDFGraphicsStreamEngine` subclass detecting bordered rectangles and underlines) and
`PdfFieldResolverRegistry` (a `List<FieldResolver>` plugin registry) — into a broader,
document-type-agnostic detection pipeline. It does not depend on `pdfWorker` and `pdfWorker`
does not depend on it. If this is ever adopted as pdfWorker's extraction engine, sequence
that after pdfWorker's own Phase 07.1 (`FormBoxDetector` confidence-scoring rewrite, in
flight as of this module's creation) lands, to avoid two concurrent rewrites of the same
detection logic.

Coordinate convention carried over deliberately: every stored coordinate is a raw PDF point,
bottom-left origin. No Y-flip happens anywhere in this engine — that's a render-boundary
concern for whatever eventually consumes `DocumentResult`.

## Roadmap

See the full architecture document (pipeline design, interfaces, self-critique, comparison
against Textract/Document AI/Azure Form Recognizer/LayoutParser/pdfplumber/Camelot/Tabula/
LayoutLM, and the phase-by-phase build order) for the complete picture. Phases 0-2 are
implemented here; Phases 3-7 are scaffolded but not built.
