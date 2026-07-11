# documentUnderstandingEngine

A self-hosted, deterministic-first Document Understanding Engine — pluggable field detectors,
a confidence-scored candidate pipeline, and a relationship graph, with AI reserved as an opt-in
fallback for low-confidence cases rather than a dependency of the primary pipeline.

Standalone module — Java 21, its own Gradle build, published independently. Not currently wired
into `pdfWorker` or any other repo in this workspace; see "Relationship to pdfWorker" below.

## Status: Roadmap Phase 0 (validation spike)

What actually runs today:

```
classify (NATIVE/SCANNED/HYBRID)
  -> per page: text primitives + vector primitives
  -> one flat PAGE layout node (no column/section/row/block subdivision yet)
  -> RectangleDetector + LabelDetector
  -> CandidateMerger (IoU-based non-max suppression)
  -> NaiveProximityResolver (nearest-neighbor label/value pairing, no graph, no rule chain)
  -> DocumentResult (JSON via DocumentResultSerializer)
```

Entry point:

```java
DocumentResult result = new DocumentEngine().process(new File("form.pdf"));
String json = new DocumentResultSerializer().toJson(result);
```

Everything else in the package structure (layout column/section/table detection, the full
detector set — underline/checkbox/radio/signature/handwriting/table/whitespace/image-placeholder,
the DocumentGraph + Stage 6 rule chain, template fingerprinting/learning, the AI fallback gateway,
the debug overlay renderer, and the OCR/CV/ONNX backends) is scaffolded as interfaces and
data types but **not implemented** — those methods throw `UnsupportedOperationException` with a
message naming the roadmap phase that implements them. This is intentional: the architecture
doc this module was built from (see below) explicitly recommends validating against real
documents on a thin slice before building breadth.

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
LayoutLM, and the phase-by-phase build order) for the complete picture. Phase 0 is what's
implemented here; Phases 1-7 are scaffolded but not built.
