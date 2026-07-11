# documentUnderstandingEngine

A self-hosted, deterministic-first Document Understanding Engine — pluggable field detectors,
a confidence-scored candidate pipeline, and a relationship graph, with AI reserved as an opt-in
fallback for low-confidence cases rather than a dependency of the primary pipeline.

Standalone module — Java 21, its own Gradle build, published independently. Not currently wired
into `pdfWorker` or any other repo in this workspace; see "Relationship to pdfWorker" below.

## Status: Roadmap Phase 1 (core pipeline skeleton)

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
  -> RectangleDetector + LabelDetector (still reading the PAGE node's flat, unfiltered
     textLines/vectorPrimitives attributes — unchanged from Phase 0, Stage 4 rewiring is Phase 2)
  -> CandidateMerger (IoU-based non-max suppression)
  -> NaiveProximityResolver (nearest-neighbor label/value pairing, no rule chain yet)
  -> DocumentResult (JSON via DocumentResultSerializer)
```

The built `DocumentLayout` (the tree) and `DocumentGraph` are real pipeline artifacts — stashed
on `PipelineContext` during `PipelineRunner.run()` — even though Stage 6 doesn't consume the
graph yet (that's Phase 3). Every new component is unit-tested in isolation against its own
inputs (`ColumnDetectorTest`, `HeaderFooterDetectorTest`, `RepeatedBlockDetectorTest`,
`LayoutGraphBuilderTest`, `ImagePrimitiveExtractorTest`), not only through the end-to-end
`Phase0PipelineTest`, which still passes unchanged.

Entry point (unchanged):

```java
DocumentResult result = new DocumentEngine().process(new File("form.pdf"));
String json = new DocumentResultSerializer().toJson(result);
```

**Deliberately deferred, even within "Phase 1" scope**: `TableRegionDetector` (coarse table
regions) — the roadmap only committed column/header/footer/repeated-block detection to Phase 1;
table detection waits for Phase 2 alongside Stage 4's `TableDetector`. `BLOCK`-level tree nodes
also don't exist yet — `ROW` is the current leaf level; finer text-run splitting within a row is
Phase 2+ territory once Stage 4 detectors actually need that granularity.

Everything else in the package structure (the rest of the Stage 4 detector set —
underline/checkbox/radio/signature/handwriting/table/whitespace/image-placeholder, the Stage 6
rule chain, template fingerprinting/learning, the AI fallback gateway, the debug overlay
renderer, and the OCR/CV/ONNX backends) is still scaffolded as interfaces and data types but
**not implemented** — those methods throw `UnsupportedOperationException` naming the roadmap
phase that implements them.

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
LayoutLM, and the phase-by-phase build order) for the complete picture. Phases 0-1 are
implemented here; Phases 2-7 are scaffolded but not built.
