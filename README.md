# documentUnderstandingEngine

A self-hosted, deterministic-first Document Understanding Engine — pluggable field detectors,
a confidence-scored candidate pipeline, and a relationship graph, with AI reserved as an opt-in
fallback for low-confidence cases rather than a dependency of the primary pipeline.

Standalone module — Java 21, its own Gradle build, published independently. Not currently wired
into `pdfWorker` or any other repo in this workspace; see [Relationship to pdfWorker](#relationship-to-pdfworker).

This is a **library**, not a service — there's no server, no REST API, no `docker-compose`.
The only "application" is a one-file CLI wrapper for trying it against a real PDF by hand
(see [Quick Start](#quick-start)); real consumers embed `DocumentEngine` directly in their own code.

---

## Quick Start

**Prerequisites:** nothing beyond the JDK — `gradlew.bat` downloads its own Gradle and the
Java 21 toolchain if you don't have one.

```powershell
# Build the jar (also runs the full test suite — see #testing)
gradlew.bat build

# Run just the tests
gradlew.bat test

# Try it against a real PDF you have on disk — prints DocumentResult JSON to stdout
gradlew.bat run --args="C:\path\to\your\form.pdf"

# ...or write the JSON to a file instead of printing it
gradlew.bat run --args="C:\path\to\your\form.pdf C:\path\to\output.json"
```

Sample output on a simple two-field form (`Name:` + a box, `DOB:` + a box):

```json
{
  "documentId" : "sample-form.pdf",
  "pageCount" : 1,
  "fields" : [ {
    "name" : "Name",
    "type" : "text",
    "value" : null,
    "coordinates" : { "x0" : 100.0, "y0" : 736.0, "x1" : 250.0, "y1" : 756.0 },
    "confidence" : 0.64,
    "page" : 0,
    "section" : "page-0-body-col-0-row-0",
    "detector" : "rectangle-detector",
    "relationships" : [ ]
  } ]
}
```

`value` is always `null` right now — nothing in the pipeline reads the *content* inside a
detected box yet (that needs OCR for scanned regions or AcroForm-style value extraction for
native fields; neither exists yet, see [Roadmap](#roadmap)). What you get today is accurate
*where things are and what they probably are*, with a confidence score and provenance for each.

There's no committed sample PDF in this repo — grab any real form/PDF you have, or build a tiny
one yourself with a few lines of PDFBox (`Phase0PipelineTest` below is a working template).

---

## Testing

Three layers, all real, none of them mocked-and-forgotten:

### 1. Unit tests per component (the majority)

Every detector, every layout-tree component, and every extractor is tested in isolation by
constructing its input directly — no PDF, no full pipeline. This is deliberate (the architecture
doc this module was built from calls it out explicitly: "every stage should be independently
testable"). Look at any of these as a template for testing a new detector or layout component:

| Test | What it proves |
|---|---|
| `detect/impl/UnderlineDetectorTest` | dy-small/dx-large lines get flagged; short or slanted ones don't |
| `detect/impl/WhitespaceDetectorTest` | interior gaps between two row items get flagged; a lone item doesn't |
| `detect/impl/ImagePlaceholderDetectorTest` | tall/square empty rectangles get flagged; text-field-sized ones don't |
| `detect/impl/TableDetectorTest` | a repeating-tagged row group clusters into consistent columns; untagged rows are skipped |
| `layout/detectors/ColumnDetectorTest` | a wide gutter splits content into two columns; a narrow gap doesn't |
| `layout/detectors/HeaderFooterDetectorTest` | text repeating across pages' top/bottom bands gets pulled out; a single page never does |
| `layout/detectors/RepeatedBlockDetectorTest` | a run of 4 structurally-identical rows gets tagged; an outlier doesn't |
| `layout/LayoutNodeTest` | `isLeaf()`/`collect()` tree-walking utilities |
| `graph/LayoutGraphBuilderTest` | a small tree produces the right PARENT_OF/CHILD_OF/READING_ORDER_NEXT edges |
| `primitives/extractors/ImagePrimitiveExtractorTest` | an embedded image's placed bounding box matches where it was drawn |

Run just one: `gradlew.bat test --tests "com.pranicdoc.docengine.detect.impl.TableDetectorTest"`

### 2. One end-to-end test through the real PDF pipeline

`Phase0PipelineTest` builds a synthetic single-page PDF with real PDFBox calls (draw text, draw
a rectangle), runs it through `DocumentEngine.process(file)` — the actual public entry point,
not a shortcut — and asserts the resulting field. This is the test that would fail first if the
Stage 3 tree-building or the Stage 4 scope-collection ever broke, since it exercises the whole
real path: classify → extract → build tree → detect → merge → resolve → serialize. Treat it as
the template for "does the whole thing still work" checks; extend it or add siblings next to it
when testing genuinely end-to-end behavior (e.g. multi-page header/footer stripping feeding into
real detection, once that's worth a dedicated test).

### 3. Manual testing against a real PDF

For anything the two automated layers don't cover — "does this hold up on a form I didn't write
a test for" — use the CLI (`gradlew.bat run --args="..."`, see Quick Start) and read the JSON.
Things worth checking by eye on a real document:
- Are boxes/labels near each other correctly paired (check `name` against the `coordinates`)?
- Do `confidence` scores look plausible relative to how clean the source PDF is?
- For a repeating table/checklist, do the `table-detector` candidates line up into sensible
  columns? (These aren't in `DocumentResult` yet — Stage 6 doesn't consume `TABLE`/`WHITESPACE`/
  `UNDERLINE`/`IMAGE_PLACEHOLDER` candidates, only `LABEL`+`RECTANGLE` pairs, see
  [Architecture](#architecture) — you'd need a short throwaway test that calls
  `DetectorRegistry`/`TableDetector` directly against the built tree to see them, the same way
  `TableDetectorTest` does.)

There is currently no way to get the intermediate `DocumentLayout` tree or `DocumentGraph` out of
`DocumentEngine`'s public API — they're stashed on the internal `PipelineContext` during
`PipelineRunner.run()` for future stages to consume, not exposed to callers. To inspect them for
a real document today, the practical path is a short JUnit test that builds a `PipelineRunner`
and calls its internals directly (or extends one of the existing tests), not the CLI.

---

## Architecture

### Pipeline

```
classify (NATIVE / SCANNED / HYBRID)
  │
  ▼
Stage 2 — primitive extraction (per page)
  TextPrimitiveExtractor    → char/word/line hierarchy from PDFBox glyph positions
  VectorPrimitiveExtractor  → rectangles/lines/curves from the content stream
  ImagePrimitiveExtractor   → embedded images, placed via the CTM at draw time
  │
  ▼
Stage 3 — DefaultLayoutAnalyzer: real Page → Column → Section → Row tree
  HeaderFooterDetector  → cross-page repeated top/bottom-band lines, pulled out first
  ColumnDetector        → whitespace-gutter x-axis splitting
  (row clustering by Y-proximity, section grouping by outlier-gap detection — inline)
  RepeatedBlockDetector → tags consecutive structurally-identical rows
  │
  ▼
Stage 5 (partial) — LayoutGraphBuilder: DocumentGraph
  PARENT_OF / CHILD_OF containment edges, READING_ORDER_NEXT sequence edges
  │
  ▼
Stage 4 — detection, against real tree scopes (not one flat page-level bag)
  leaf nodes (ROW leaves + header/footer bands):
    RectangleDetector, UnderlineDetector, LabelDetector,
    WhitespaceDetector, ImagePlaceholderDetector
  non-leaf SECTION nodes (ROW children):
    TableDetector (per-cell, via repeatingGroupId-tagged row clustering)
  │
  ▼
CandidateMerger — IoU-based non-max suppression across all detectors' output
  │
  ▼
Stage 6 (naive) — NaiveProximityResolver: nearest LABEL↔RECTANGLE pairing only
  │
  ▼
Stage 7 — DocumentResult → JSON (DocumentResultSerializer)
```

### What's real vs. scaffolded

| Package | Status |
|---|---|
| `classify` | Real — heuristic NATIVE/SCANNED/HYBRID classification |
| `primitives` | Real (text, vector, image) — `RasterPrimitiveExtractor` (scanned-page rasterization for OCR/CV) is a Phase 4 stub |
| `layout` | Real tree-building (Column/HeaderFooter/RepeatedBlock). `TableRegionDetector` deliberately unimplemented — superseded by Stage 4's `TableDetector`, see its Javadoc. No `BLOCK`-level nodes yet — `ROW` is the current leaf |
| `graph` | Real containment + reading-order edges. `NEAREST_LABEL`/`NEAREST_VALUE`/`BELONGS_TO_TABLE` edges need Stage 6 to exist first |
| `detect` | All 6 native-PDF detectors real (Rectangle/Underline/Label/Whitespace/ImagePlaceholder/Table). `Checkbox`/`RadioGroup`/`Signature`/`HandwritingRegion` are Phase 4 stubs (need OpenCV) |
| `confidence` | Real — weighted-average aggregation, per-detector default weights in `EngineConfig` |
| `semantic` | `NaiveProximityResolver` only (nearest-neighbor, LABEL+RECTANGLE only). The real rule chain (`SpatialProximityRule`/`LabelValuePairingRule`/`FieldTypeInferenceRule`) is Phase 3 — currently throws `UnsupportedOperationException` |
| `output` | Real — `DocumentResult`/`FieldResult`/JSON serialization |
| `template`, `ai`, `debug`, `ocr` | All Phase 4+ stubs — interfaces/data types exist, no logic |

Every unimplemented method throws `UnsupportedOperationException` naming the roadmap phase that
implements it — nothing pretends to work partially.

### Key design decisions worth knowing before you touch this code

- **Coordinates are always raw PDF points, bottom-left origin.** No Y-flip anywhere in this
  engine — not in extraction, not in layout, not in output. That conversion is exclusively a
  render-boundary concern for whatever eventually consumes `DocumentResult` (this mirrors a
  hard-won convention already proven in `pdfWorker`).
- **Detectors are self-filtering, not scope-typed.** `PipelineRunner` hands every applicable
  scope to every registered detector; each detector reads the attributes it needs
  (`textLines`/`vectorPrimitives`) and returns an empty list if they're missing or empty. This
  is why `TableDetector` can safely run against every leaf node too (it just finds zero `ROW`
  children there) without any type-based gating machinery.
- **`COLUMN` nodes are never a detection scope.** Their attributes exist only so
  `DefaultLayoutAnalyzer`'s internal row-clustering can read them; every leaf beneath a column
  already carries the same content in smaller pieces. Detecting at both levels would double-count.
- **Confidence is tracked as contributions, not just a final number** (`ConfidenceContribution`
  → `ConfidenceEngine`) — a field's score is traceable back to which detector(s) and which
  heuristic (e.g. proximity distance) produced it, not just "0.64."
- **`scopeNodeId`/`sectionNodeId` are structural, not semantic.** `FieldResult.section` is a
  real layout-tree node id (e.g. `page-0-body-col-0-row-0`), not a human section name like
  "Patient Information" — that needs header-style section detection, which doesn't exist yet.

See the full architecture document (referenced in this module's git history / the conversation
that produced it) for the complete pipeline design, self-critique, and comparison against
Textract/Document AI/Azure Form Recognizer/LayoutParser/pdfplumber/Camelot/Tabula/LayoutLM — this
README covers what's actually built, not the full target design.

---

## Relationship to pdfWorker

This module generalizes a pattern already proven in `pdfWorker` — `FormBoxDetector`
(a `PDFGraphicsStreamEngine` subclass detecting bordered rectangles and underlines) and
`PdfFieldResolverRegistry` (a `List<FieldResolver>` plugin registry) — into a broader,
document-type-agnostic detection pipeline. It does not depend on `pdfWorker` and `pdfWorker`
does not depend on it. If this is ever adopted as pdfWorker's extraction engine, sequence that
after pdfWorker's own Phase 07.1 (`FormBoxDetector` confidence-scoring rewrite) lands, to avoid
two concurrent rewrites of the same detection logic.

---

## Roadmap

- ✅ **Phase 0** — thin end-to-end slice: classify → text/vector primitives → Rectangle+Label →
  naive pairing → JSON
- ✅ **Phase 1** — full primitive extraction (+images) and the real Page→Column→Section→Row tree
  + DocumentGraph
- ✅ **Phase 2** — the rest of the native-PDF detector set (Underline/Whitespace/ImagePlaceholder/
  Table) + Stage 4 rewired to run against real tree scopes instead of one flat page-level bag
- ⬜ **Phase 3** — the real Stage 6 rule chain (`SpatialProximityRule`/`LabelValuePairingRule`/
  `FieldTypeInferenceRule`) consuming the `DocumentGraph`, and the real `DocumentResult` JSON
  schema finalized
- ⬜ **Phase 4** — OCR/CV integration (Tesseract, OpenCV, `NativeResourcePool`) and the four
  detectors that depend on it (Checkbox/RadioGroup/Signature/HandwritingRegion); this is also
  where `value` in `FieldResult` would first become non-null
- ⬜ **Phase 5** — template fingerprinting & learning (`TemplateRegistry`)
- ⬜ **Phase 6** — the AI fallback gateway (`AIFallbackGateway`/`EscalationPolicy`) — deliberately
  last, since it's the exception path and needs Phases 0-5's confidence scoring to be trustworthy
  first
- ⬜ **Phase 7** — performance hardening (parallel page processing, native resource pool sizing,
  streaming for large documents) — deferred until there's real profiling data to size against
