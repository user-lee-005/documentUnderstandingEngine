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
# AND writes debug artifacts (annotated PDF + PNG + verbose JSON) to build/detected-fields/
gradlew.bat run --args="C:\path\to\your\form.pdf"

# ...or write the JSON to a file instead of printing it (debug artifacts still get written)
gradlew.bat run --args="C:\path\to\your\form.pdf C:\path\to\output.json"
```

Every run writes three files to `build/detected-fields/` — see
[Debugging](#debugging--seeing-how-a-document-was-understood) for what they show and why:

```
build/detected-fields/<your-file>-annotated.pdf   — original PDF + every detected box, colored by confidence
build/detected-fields/<your-file>-page-0.png       — same thing, rasterized (one PNG per page)
build/detected-fields/<your-file>-debug.json       — every merged candidate + the final DocumentResult
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

## Debugging — seeing how a document was understood

### How detection actually works, end to end

1. **Classify** the document as NATIVE, SCANNED, or HYBRID (heuristic: extracted text volume vs.
   embedded images).
2. **Extract primitives** per page — every character's position (`TextPrimitiveExtractor`),
   every stroked/filled shape from the content stream (`VectorPrimitiveExtractor`), every
   embedded image's placed position via its transform matrix (`ImagePrimitiveExtractor`). Nothing
   here makes a judgment about what anything *means* yet — it's pure geometry.
3. **Build the layout tree** (`DefaultLayoutAnalyzer`): pull out lines that repeat across pages'
   top/bottom bands (headers/footers), split remaining content into columns wherever there's a
   wide enough empty vertical band, cluster content into rows by Y-proximity, group rows into
   sections wherever the gap to the next row is unusually large, and tag runs of 3+
   structurally-identical rows as a repeating block (candidate checklist/table rows).
4. **Detect candidates**, independently, against every leaf (row) and every non-leaf section in
   that tree: is this a bordered box the right size for a text field (`RectangleDetector`)? A
   short colon-terminated line (`LabelDetector`)? A long straight underline
   (`UnderlineDetector`)? An interior gap between two items wide enough to be an unruled field
   (`WhitespaceDetector`)? A tall square empty box shaped like a photo slot
   (`ImagePlaceholderDetector`)? A column-consistent cell across a tagged repeating row group
   (`TableDetector`)? **Every detector runs independently and doesn't know what the others
   found** — this is the part that answers your "single input or array" question below.
5. **Merge** overlapping candidates (same box detected twice) via IoU-based non-max suppression —
   this only collapses near-*duplicate* detections of the same physical box, never combines
   *different* boxes into one.
6. **Resolve** (today, `NaiveProximityResolver`): for each `LABEL` candidate, find its single
   nearest still-available `RECTANGLE` candidate on the same page, within a distance threshold.
   Whichever wins is removed from the pool so it can't be reused by a different label.
7. **Serialize** the resolved (label, value) pairs to `DocumentResult` JSON.

### "If a section has many boxes, is that one input or an array?"

**Always an array — nothing in this pipeline merges multiple detected boxes into a single
input.** Concretely, verified against a real 3-box example (`Name:` + a nearby box, `DOB:` + a
nearby box, and a third box far off to the side with no label near it):

- Step 4 (detection) produced **5 independent candidates**: 2 `LABEL`, 3 `RECTANGLE` — every box
  and every label is tracked as its own entry in a `List<DetectionCandidate>`, always. There is
  no code path anywhere that looks at "how many boxes are in this row" and decides to combine
  them.
- Step 6 (resolution) is **strictly one-to-one, greedy, nearest-neighbor**: `Name:` claimed the
  box next to it, `DOB:` claimed its own nearest box, and the third box — the one with no label
  close enough — was never claimed by anyone.
- The final `DocumentResult.fields` array had **2 entries**, not 3, not 1. The unclaimed third box
  doesn't get merged into either field, and it doesn't get dropped as an "array of extra values"
  under a field either — it simply never becomes a field. It's silently absent from the
  production output.
- The **debug JSON still shows all 5** — that's the entire reason the debug export serializes the
  merged candidate list, not just the final result. `PdfDebugOverlayRendererTest`'s
  `twoBoxesInOneRowAreTrackedAsSeparateCandidatesNotOneMergedInput` is this exact scenario, asserted.

Two follow-on implications worth knowing:
- **There's no "one label → many values" concept yet** (e.g. a `Gender` label mapping to an
  array of checkbox options). Building that is exactly what Stage 6's real rule chain
  (`FieldTypeInferenceRule`, Roadmap Phase 3) is for — nothing today infers that kind of grouping.
- **Extra unclaimed boxes are a real signal, not noise** — on a genuine form they usually mean
  either the label detector missed a label nearby (worth checking `LabelDetector`'s heuristics
  against that specific text) or the distance threshold (`EngineConfig.maxLabelToValueDistancePts`,
  120pt by default) is too tight for that document's layout. The debug PDF is where you'd spot
  this by eye — an unpaired box renders exactly the same as a paired one, just absent from the
  final JSON.

### Reading the debug output

- **Box color = confidence**, on a continuous red (low) → yellow → green (high) scale
  (`ConfidenceColorScale`) — not detector identity. A cluster of red boxes on a real document
  means something about that region is off (wrong size heuristic for that form's field style,
  detector picking up incidental line art, etc.) — worth a closer look, possibly an
  `EngineConfig` threshold tweak.
- **Caption text = `detectorId type confidence`** (e.g. `rectangle-detector RECTANGLE 0.83`) —
  detector identity moved from color into text since color is now reserved for confidence.
- On small/cramped synthetic test forms, captions from adjacent close-together rows can visually
  overlap each other — a cosmetic debug-view rough edge, not a detection bug; real forms with
  normal spacing don't show this.
- The PNG is just the annotated PDF rasterized (same content stream, same boxes) — there's
  deliberately no second, separate pixel-coordinate drawing pass, so the PDF and PNG can never
  drift out of sync with each other.

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
| `debug/ConfidenceColorScaleTest` | confidence 0.0 renders red, 1.0 renders green, values between are ordered correctly |
| `debug/PdfDebugOverlayRendererTest` | multiple boxes in one row stay separate candidates (see [Debugging](#debugging--seeing-how-a-document-was-understood)); `writeTo()` produces the right files on disk |

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
a test for" — use the CLI (`gradlew.bat run --args="..."`, see Quick Start) and look at
`build/detected-fields/`. This is real detection visibility, not just the narrow final JSON:
every detector's raw candidates are in there — `TABLE`/`WHITESPACE`/`UNDERLINE`/
`IMAGE_PLACEHOLDER` candidates included, none of which make it into `DocumentResult` yet (Stage 6
only consumes `LABEL`+`RECTANGLE` pairs, see [Debugging](#debugging--seeing-how-a-document-was-understood)
and [Architecture](#architecture)). Things worth checking by eye:
- Open the `-annotated.pdf` or `-page-N.png` — are boxes/labels near each other correctly paired,
  and does the color (confidence) look plausible for how clean that region of the source PDF is?
- Grep the `-debug.json` for `"type" : "TABLE"` on a repeating checklist/table — do the `row`/`col`
  attributes line up the way you'd expect, even though they're not in the final `fields` array?
- Any candidates near real content that *aren't* boxed? That's a miss — check the relevant
  detector's thresholds in `EngineConfig` against that document's actual geometry.

There is currently no way to get the intermediate `DocumentLayout` tree or `DocumentGraph` out of
`DocumentEngine`'s public API — only the flattened `DetectionCandidate` list surfaces via
`processWithDebug()`/the debug JSON. They're stashed on the internal `PipelineContext` during
`PipelineRunner.run()` for future stages to consume, not exposed to callers yet. To inspect the
tree/graph shape itself for a real document, the practical path is still a short JUnit test that
builds a `PipelineRunner`/`DefaultLayoutAnalyzer` and calls it directly, not the CLI.

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

  (in parallel, off the merged-candidate list, not the final result)
  Debug — PdfDebugOverlayRenderer → confidence-colored annotated PDF + per-page PNG + verbose JSON
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
| `debug` | Real — `PdfDebugOverlayRenderer` (confidence-colored annotated PDF + per-page PNG + verbose candidate JSON), wired into `DocumentEngine.processWithDebug()` and the CLI |
| `template`, `ai`, `ocr` | All Phase 4+ stubs — interfaces/data types exist, no logic |

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
- **Multiple boxes are always an array of independent candidates, never one merged input** —
  see [Debugging](#debugging--seeing-how-a-document-was-understood) for the full answer with a
  verified example. `CandidateMerger` only collapses duplicate detections of the *same* box.

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
