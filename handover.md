# HANDOVER — documentUnderstandingEngine

> **Purpose of this file**: persistent, self-contained memory for any Claude Code session (or
> human) picking up this project cold. It captures the *original design intent*, the *reasoning
> behind every non-obvious decision*, and the *exact current state of execution* — not just what
> exists, but why, and what deliberately doesn't exist yet. `README.md` in this repo is the
> living user-facing doc (build/test/run/architecture-as-built); this file is the fuller
> session-to-session narrative, including things README intentionally leaves out (the original
> unabridged architecture vision, the clarifying-question decisions, the scoping calls made along
> the way, and what's explicitly next). Read this before touching the code.
>
> Last updated: end of the session that built Phases 0-2 + the debugger (5 commits,
> `54a8c61`..`b7c45db`). If you continue this work, append to the "Session Log" section at the
> bottom rather than rewriting this document from scratch.

---

## 1. What this project is, and why it's a separate repo

**The ask**: build a production-grade, self-hosted Document Understanding Engine in Java —
architecturally similar to Amazon Textract / Google Document AI / Azure Form Recognizer, but
**deterministic-first**: geometric/rule-based detection as the primary pipeline, with AI (LLMs)
reserved as an *opt-in fallback for low-confidence cases only*, never a dependency of the core
path. Must work fully offline. The user (a Principal-Architect-style brief) explicitly asked for
**architecture and critique first, code only after** — this is why the first deliverable in this
project was a full design document (not committed to this repo as a file — it was produced and
approved inside a Claude Code Plan Mode session; its substance is reproduced in full in §3 below
so it isn't lost).

**Where this sits relative to the rest of the workspace**: the wider workspace
(`E:\PranicDoc\PranicDoc\`) is a Pranic Healing case-documentation platform. `pdfWorker` is an
existing Spring Boot 3.3.5 service there that already does narrow PDF field detection —
`FormBoxDetector` (a `PDFGraphicsStreamEngine` subclass detecting bordered rectangles and
underlines) and `PdfFieldResolverRegistry` (a `List<FieldResolver>` plugin registry). This new
engine **generalizes those two proven patterns** into a much broader, document-type-agnostic
pipeline, but is a **fully standalone module — no code dependency either direction**.
`pdfWorker`'s own `FormBoxDetector` was mid-rewrite (Phase 07.1, confidence-scoring) when this
project started; this engine's design was built to not collide with that, and to be adoptable
later without contradicting it.

**Decisions locked in by the user early on** (asked via `AskUserQuestion`, not assumed):
1. **Placement**: initially "pure design exercise, no placement yet" during the architecture
   phase; later, explicitly **"make this into a separate module like a different jar service...
   in `E:\PranicDoc\PranicDoc\documentUnderstandingEngine`"** — i.e. a standalone repo/service
   that could later be injected into or replace `pdfWorker`'s extraction, not a package inside it.
2. **Java version**: **Java 21**, deliberately independent of `pdfWorker`'s Java 17 pin (the
   brief specified 21; no reason to inherit `pdfWorker`'s constraint for a from-scratch module).
3. **OCR/CV dependency posture**: **"Full stack from day one"** — Tesseract (via Tess4J), OpenCV
   (via `org.bytedeco:opencv-platform`, which bundles native libs), and ONNX Runtime are all
   declared in `build.gradle` from the very first commit, even though **no code calls them yet**
   (that's Phase 4). This was an explicit tradeoff choice over "PDFBox-only core, add OCR/CV
   later" — the user chose to commit to the full dependency footprint immediately rather than
   defer it.
4. **Build scope for each session**: rather than build the entire 7-phase vision in one shot
   (which the architecture doc's own self-critique flagged as the single biggest risk — see §3.7),
   work proceeded **phase by phase, with a working, tested, committed state after each phase**.
   This handover reflects that: Phases 0, 1, 2 are done; Phase 3 onward are not.

---

## 2. The absolute non-negotiables — read this before writing any code here

These are conventions that, if violated, will silently corrupt output rather than throw an
exception. Every session so far has treated these as load-bearing law:

1. **Every coordinate everywhere is a raw PDF point, bottom-left origin.** No Y-flip anywhere in
   this engine — not in primitive extraction, not in the layout tree, not in detection, not in
   output. This mirrors a hard-won, explicitly-documented convention already proven in
   `pdfWorker` (`pdfCoordsToOverlay()` is the *only* place pdfWorker flips Y, and only at its
   frontend render boundary). The one place this engine's own code has to convert *from* a
   different coordinate space *to* this convention is `TextPrimitiveExtractor`, which reads
   PDFBox's `TextPosition` (top-down distance from page top) and flips it back to bottom-left via
   `topY = pageHeightPts - tp.getY(); bottomY = topY - tp.getHeight();` — that conversion is
   intentional and lives in exactly one place. If you ever see a flip formula anywhere else in
   this codebase, that's a bug.
2. **Detectors are self-filtering, not scope-typed.** There is no type system or registry logic
   that says "only run `TableDetector` against `SECTION` nodes." Every registered detector gets
   handed every candidate scope; each detector reads the attributes it needs
   (`textLines`/`vectorPrimitives`) off the `LayoutNode` and returns an empty list if they're
   absent or don't qualify. This is why adding a new detector is *purely additive* — register it,
   done, nothing else changes. Don't "optimize" this into type-based dispatch; it would break the
   plugin-architecture property the whole design is built around.
3. **`COLUMN` nodes are never a detection scope.** `PipelineRunner.detectionScopes()` collects
   only leaf nodes (`LayoutNode::isLeaf`) and non-leaf `SECTION` nodes. `COLUMN` nodes always have
   `SECTION`/`ROW` children in the normal case, so they're excluded by the leaf check — but if you
   ever change tree-building such that a `COLUMN` could end up childless, it would get scanned
   too, and since a `COLUMN`'s `textLines`/`vectorPrimitives` attributes are supersets of its
   descendants' content, you'd get every leaf's content detected *twice* under the wrong
   provenance (`scopeNodeId` pointing at the column, not the actual row). If you touch
   `DefaultLayoutAnalyzer` or `detectionScopes()`, re-verify this invariant.
4. **Multiple boxes are always an array of independent `DetectionCandidate`s — nothing merges
   them into one input.** This was asked about explicitly by the user and verified with a real
   test (`PdfDebugOverlayRendererTest.twoBoxesInOneRowAreTrackedAsSeparateCandidatesNotOneMergedInput`):
   detection always produces one candidate per detected box/label, full stop.
   `CandidateMerger`'s IoU-based non-max suppression *only* collapses near-duplicate detections of
   the *same physical box* (two detectors independently flagging the identical rectangle) — it
   never combines two genuinely different boxes. Resolution (`NaiveProximityResolver`, Stage 6)
   is strictly **one-label-to-one-nearest-value, greedy**: each label claims exactly one
   still-available value candidate; extras are simply absent from `DocumentResult`, not merged,
   not dropped-with-a-warning, not kept as a list under one field. There is currently **no
   "one label → array of values" concept** (e.g. a `Gender` label mapping to a checkbox-option
   array) — building that is explicitly Phase 3 / `FieldTypeInferenceRule` territory, not done.
5. **`scopeNodeId` / `sectionNodeId` / `FieldResult.section` are structural node IDs, not
   semantic section names.** `"page-0-body-col-0-row-0"` is a real, traceable layout-tree
   coordinate, not "Patient Information". Don't let a future session assume `section` is
   human-readable without adding real header-style section detection first.
6. **Every unimplemented method throws `UnsupportedOperationException` naming the roadmap phase
   that implements it.** Never let a stub silently return an empty list or a fake default when it
   would misrepresent "this works" — the whole point of leaving things unimplemented rather than
   half-implemented is that failures are loud, not silent regressions. If you're tempted to make a
   stub "just return something reasonable" to unblock a build, don't — implement it for real or
   leave it throwing.

---

## 3. The original architecture vision (unabridged, for context — see §4 for what's actually built)

This section reproduces the substance of the approved architecture design doc so it survives
even if the original Plan Mode artifact doesn't. Treat §4 as ground truth for *what exists*; treat
this section as ground truth for *what the eventual full system is supposed to become* and *why*
each piece was designed the way it was.

### 3.1 Scope framing

Everything below — 7 pipeline stages, ~15 detector types, template learning, a confidence engine,
an AI fallback, a plugin system, debug visualization, "thousands of documents/hour" — is,
combined, roughly Textract/Document AI-scale in ambition. The design doc was explicit that this
should **not** be built breadth-first before validating against real documents (see §3.7) — hence
the phased, narrow-then-widening execution actually followed (§4).

### 3.2 High-level architecture

```
                          ┌─────────────────────────────────────────┐
                          │              DocumentEngine              │  (facade / entry point)
                          └───────────────────┬───────────────────────┘
                                              │
                    ┌─────────────────────────┼─────────────────────────┐
                    │                         │                         │
            ┌───────▼────────┐      ┌─────────▼─────────┐      ┌────────▼────────┐
            │ TemplateRegistry│      │   PipelineRunner   │      │  DebugRenderer  │
            │ (fingerprint,   │◄────►│  (orchestrates     │─────►│  (annotated PDF/│
            │  fast-path)     │      │   S1-S7 per page)  │      │   PNG/JSON dump)│
            └────────────────┘      └─────────┬───────────┘      └─────────────────┘
                                              │
        ┌───────────┬────────────┬───────────┼────────────┬────────────┬───────────┐
        │           │            │           │            │            │           │
   ┌────▼───┐  ┌─────▼────┐ ┌─────▼─────┐ ┌───▼────┐  ┌─────▼─────┐ ┌────▼─────┐ ┌───▼────┐
   │Stage 1 │  │ Stage 2  │ │  Stage 3  │ │Stage 4 │  │  Stage 5  │ │ Stage 6  │ │Stage 7 │
   │Classify│─▶│Primitives│▶│  Layout   │▶│Detector│─▶│Relationship│▶│Semantic │▶│Output  │
   │        │  │          │ │   Tree    │ │Plugins │  │   Graph    │ │Resolver │ │(JSON)  │
   └────────┘  └──────────┘ └───────────┘ └────┬───┘  └───────────┘ └────┬─────┘ └────────┘
                                                │                         │
                                          ┌─────▼─────┐            ┌──────▼──────┐
                                          │ Confidence │            │  AIFallback  │
                                          │  Engine    │◄──────────►│ (opt-in,     │
                                          │ (cross-    │  low-conf  │  structured  │
                                          │  cutting)  │  escalation│  candidates) │
                                          └────────────┘            └─────────────┘
```

Key structural decisions from the original design:
- `PipelineRunner` is a **straight-line orchestrator**, not a generic DAG engine — Stages 1→7 run
  in a fixed order; only Stage 4 (detectors) fans out internally. A generic pluggable-DAG pipeline
  was deliberately rejected as unjustified complexity for what's fundamentally a linear flow.
- `ConfidenceEngine` and `AIFallback` are **cross-cutting**, not stages — injected into each
  stage as a shared scoring service, not something that "runs between" stages.
- `TemplateRegistry` sits **beside** the pipeline, not inside it — consulted before Stage 1
  (fingerprint match → skip expensive analysis for known layouts) and updated after Stage 7 (on
  human-validated documents).

### 3.3 The 7 stages, as originally specified

1. **Classification** — Native / Scanned / Hybrid; metadata (fonts, page size, rotation, image
   inventory).
2. **Primitive Extraction** — text (char→word→line→paragraph), vector (rectangles/lines/curves),
   images, whitespace regions. Deliberately makes *no* semantic judgment — pure geometry.
3. **Layout Analysis** — build Page→Column→Section→Row→Block→Field→Value tree; detect headers,
   footers, repeated blocks, multi-column layouts, coarse table regions.
4. **Field Candidate Detection** — independent pluggable detectors (rectangle, underline,
   whitespace, table, label, checkbox, radio, signature, image-placeholder), each producing
   `DetectionCandidate`s with a self-reported confidence.
5. **Relationship Graph** — every object becomes a node; typed edges (nearest-label, reading-order,
   belongs-to-table, parent/child, same-repeating-group) make the structure queryable.
6. **Semantic Resolver** — spatial-reasoning rule chain (not template matching) pairs labels with
   values and infers field types (date, checkbox-group, multiline text, etc.) — "do NOT hardcode
   templates."
7. **Structured Output** — JSON per field: name, type, value, coordinates, confidence, page,
   section, detector, relationships.

### 3.4 Cross-cutting concerns, as originally specified

- **Confidence Engine**: two-level — each detector self-reports a raw confidence however it
  wants; a pluggable `ConfidenceAggregator` (default: weighted average) combines contributions
  from every stage that touched a candidate into one final score. Contributions are tracked
  individually (`ConfidenceContribution{source, score, weight}`), not collapsed early, so a
  field's score is always traceable back to *why*.
- **Template Registry & Fingerprinting**: `TemplateFingerprint` deliberately NOT filename- or
  raw-byte-hash based (two scans of the same form at different JPEG quality must fingerprint
  identically) — built from structural hash (layout-tree shape, quantized) + text anchors + logo
  positions + page dimensions. Explicit honesty flag in the original design: the fast-path/
  partial-trust logic (how loosely a fingerprint match should be trusted before re-verifying) was
  called out as **the least-specified, highest-risk part of the whole design** — get the
  threshold wrong either way (too strict = no perf win, too loose = silently wrong template
  applied) and you reintroduce exactly the kind of misplaced-field bug pdfWorker's Phase 07.1 was
  fixing.
- **AI Fallback**: never the default path — only an `EscalationPolicy` (confidence < threshold, OR
  unknown layout, OR OCR ambiguous) calls `AIFallbackGateway`. Structured-first: candidates + local
  context by default; a cropped region image is opt-in per-field, never the whole page/document.
  Backend-swappable (local LLM / hosted API / human-review queue) behind one interface.
- **Plugin System**: `FieldCandidateDetector{detectorId(), isApplicable(ctx), detect(scope, ctx)}`
  — adding a detector never touches the pipeline.
- **Debug Mode**: render the original PDF with overlays (boxes, labels, relationships, tables,
  reading order, confidence, distinct colors), export as annotated PDF + PNG + JSON — meant as a
  first-class regression-testing tool (snapshot-test the debug JSON against a real-document corpus
  to catch detector-heuristic drift), not just a human convenience.
- **Performance**: parallel page processing (Stages 1-4 are page-local), streaming (never
  materialize a full-document primitive list before Stage 3 starts), object pooling for
  Tesseract/OpenCV instances specifically (non-thread-safe, expensive to construct — the two
  resources that actually justify pooling machinery).

### 3.5 Package structure, as originally specified

```
com.docengine
├── core        (DocumentEngine, PipelineRunner, PipelineContext, PipelineStage, EngineConfig)
├── classify    (Stage 1)
├── primitives  (Stage 2: extractors/ + model/)
├── layout      (Stage 3: LayoutNode tree + detectors/)
├── detect      (Stage 4: FieldCandidateDetector plugin system + impl/)
├── graph       (Stage 5: DocumentGraph, GraphNode/Edge, GraphQuery)
├── semantic    (Stage 6: SemanticResolver + rules/)
├── confidence  (cross-cutting)
├── template    (template learning + registry)
├── ai          (AI fallback — optional, isolated)
├── output      (Stage 7)
├── debug       (visualization/debug mode)
└── ocr         (OCR/CV/ONNX integration layer)
```

(Actual package root became `com.pranicdoc.docengine`, matching the workspace's `com.pranicdoc`
group convention — see §4.2.)

### 3.6 Self-critique from the original design (still valid — don't assume these are solved)

1. The linear 7-stage pipeline is a simplification that will leak — e.g. knowing a region is a
   table (Stage 4) should sometimes retroactively refine layout segmentation (Stage 3), which a
   strict one-directional pipeline doesn't accommodate. (Partially addressed in practice: Stage 4's
   `TableDetector` reads Stage 3's `repeatingGroupId` tagging, a form of forward-only composition,
   not a true feedback loop.)
2. Confidence aggregation via weighted average is simplistic — a single global per-detector
   weight doesn't account for a detector being reliable on typed forms but unreliable on faxed
   scans. `ConfidenceAggregator` is pluggable specifically so this can evolve.
3. Template fingerprinting's partial-match/partial-trust policy is the least-specified,
   highest-risk part of the whole design (see §3.4).
4. OCR/handwriting confidence is not commensurable with geometric confidence, but the
   `ConfidenceContribution` model currently treats all sources as flat 0-1 scores. Needs
   source-type-aware aggregation before Phase 4's OCR confidence should be trusted alongside
   Phase 0-2's geometric confidence.
5. Performance targets ("thousands of documents/hour") were stated with **zero profiling
   behind them** — a placeholder to replace once real data exists, not a target to design against
   blindly.
6. Plugin system makes adding detectors easy but says nothing about detector *conflict*
   resolution quality beyond "closest wins" (`CandidateMerger`'s IoU-based NMS) — real overlaps
   (a checkbox inside a table cell inside a signature block) need a richer policy eventually.

### 3.7 The single biggest risk, as named in the original design

> Building everything (all 7 stages, all detectors, template learning, confidence engine, AI
> fallback, debug mode) before validating against real documents is the biggest risk in the
> brief. Nothing in the weaknesses above can actually be resolved by more design — they need real
> documents and real failure cases.

This is *why* execution proceeded Phase 0 (thinnest possible slice) → Phase 1 (real tree) →
Phase 2 (full native-PDF detector set + real Stage 4 wiring), each fully tested and committed,
rather than attempting the whole system at once. **Continue this discipline.** Don't jump ahead
to Phase 4/5/6 machinery without Phase 3 (the real Stage 6 rule chain) landing and being validated
first.

### 3.8 Comparative positioning (from the original design, condensed)

This engine is closest in spirit to a **self-hosted, rule-first hybrid** of Textract's modularity
and Camelot/pdfplumber's determinism, with the `ocr/` package's `OnnxInferenceEngine` deliberately
left as a slot for LayoutParser/LayoutLM-style models to be added *later as an additional
confidence signal*, never as the primary extraction mechanism. Named tradeoff: this ceiling on
unseen/messy/scanned-layout performance is lower than Textract's/Document AI's out of the box —
trading generalization for determinism and self-hosting, which was the explicit point of the
brief, but the gap should stay named, not assumed away.

### 3.9 Original roadmap (Phase 0 → Phase 7)

- **Phase 0** — thinnest slice: classify → text+vector primitives → 2 detectors (rectangle,
  label) → naive nearest-neighbor pairing → flat JSON. No template learning, no AI fallback, no
  debug renderer, no OCR/CV.
- **Phase 1** — core pipeline skeleton for real: full Stage 1-3 (classification, full primitive
  extraction including images, full layout tree with column/header/footer/repeated-block
  detection), `DocumentGraph`+`GraphQuery`. Still native-PDF only. Debug overlay basic (PDF+JSON,
  no PNG yet, per the *original* plan — this got revised in execution, see §4.5).
- **Phase 2** — detector plugin system completed: the detectors that don't need OCR/CV
  (rectangle, underline, whitespace, label, table, image-placeholder). `ConfidenceEngine` +
  default weighted-average aggregator.
- **Phase 3** — semantic resolution + structured output for real: `SemanticResolver` rule chain,
  `SemanticField` model, Stage 7 JSON contract finalized with a real schema.
- **Phase 4** — OCR/CV integration: `RasterPrimitiveExtractor`, `OcrEngine` (Tesseract/Tess4J),
  `CvEngine` (OpenCV), `NativeResourcePool`, and the detectors that depend on them (checkbox,
  radio group, signature, handwriting region). Confidence aggregation becomes source-type-aware
  here.
- **Phase 5** — template registry & learning: `TemplateFingerprint`, `FingerprintMatcher`,
  `TemplateStore`, the fast-path/partial-trust logic — deliberately last among "core" phases
  because it needs Phase 0-4's real failure data to calibrate against.
- **Phase 6** — AI fallback: `AIFallbackGateway`, `EscalationPolicy`, `AICandidatePayload` —
  deliberately last of all; needs Phase 0-5's confidence scoring to be trustworthy before
  deciding *when* to escalate.
- **Phase 7** — performance hardening: parallel page processing tuning, object-pool sizing,
  streaming for large documents, horizontal-scaling packaging — only once real profiling data
  exists.

---

## 4. Execution status — what is ACTUALLY built (ground truth as of this handover)

### 4.1 High-level status

| Phase | Status | Commit(s) |
|---|---|---|
| 0 — thin end-to-end slice | ✅ Done | `54a8c61` |
| 1 — full primitive extraction + real layout tree + DocumentGraph | ✅ Done | `23e63aa` |
| 2 — full native-PDF detector set + Stage 4 rewired to the real tree | ✅ Done | `4e0cbc0` |
| (ad hoc) — CLI entry point + comprehensive README | ✅ Done | `d42f400` |
| (ad hoc) — real debug renderer (pulled forward from later phases) | ✅ Done | `b7c45db` |
| 3 — real Stage 6 rule chain + finalized JSON schema | ❌ Not started | — |
| 4 — OCR/CV integration + 4 dependent detectors | ❌ Not started (deps declared, unused) | — |
| 5 — template registry & learning | ❌ Not started (interfaces only) | — |
| 6 — AI fallback gateway | ❌ Not started (interfaces only) | — |
| 7 — performance hardening | ❌ Not started | — |

**All 18 tests pass. `gradlew.bat build` is green.** This has been re-verified after every single
change across all 5 commits — never assume a prior session's "tests pass" claim without rerunning
`gradlew.bat test` yourself if you're about to build on top of it.

### 4.2 Concrete deviations from the original design (and why)

- **Root package is `com.pranicdoc.docengine`**, not `com.docengine` — matches the workspace's
  `com.pranicdoc` group convention (same as `pdfWorker`, `cph`, `common-dtos`, `storage-sdk`).
- **`build.gradle` follows the exact `java-library` + `maven-publish` + GitHub Packages pattern**
  already proven by `storageService`/`common-dtos` in this workspace (artifact
  `document-understanding-engine`, publishes to
  `https://maven.pkg.github.com/PranicDoc/document-understanding-engine`). Not published yet —
  publish credentials would need `gpr.user`/`gpr.key` in `~/.gradle/gradle.properties`, same as
  every other module in this workspace.
- **`application` plugin added** (not in the original design at all) — this is a pure library
  with no server/UI, so a one-file CLI (`DocumentEngineCli`) was added purely so there's something
  to literally run and test against a real PDF by hand. Not part of the published library's
  public API surface — it's a dev convenience, `gradlew.bat run --args="path.pdf"`.
- **Debug renderer was built in this session even though it's nominally later-roadmap** — the
  original Phase 1 plan said "debug overlay (PDF+JSON only, no PNG yet)"; what actually got built
  (in response to an explicit user request, not as part of Phase 1/2 proper) is a *complete*
  debug renderer: annotated PDF **and** per-page PNG **and** verbose JSON, colored by confidence
  (not by detector, which was the original plan — the user explicitly asked for confidence-based
  coloring, so that superseded the original per-detector-color idea; detector identity is still
  visible via the caption text). See §4.5 for full detail.
- **`TableRegionDetector`** (Stage 3, coarse table-region marking) is **deliberately still
  unimplemented**, but the *reason* changed between Phase 1 and Phase 2: in Phase 1 it was "not
  yet, Phase 2's job." After Phase 2, the reasoning became "superseded" — Stage 4's `TableDetector`
  now finds table cells directly by scanning `repeatingGroupId`-tagged row groups within a
  `SECTION`, without needing a separate coarse-region-marking pass first. Its Javadoc says: revisit
  only if something downstream (debug rendering, the relationship graph) ends up needing an
  explicit "this region is a table" node that `TableDetector`'s cell-level candidates don't already
  imply. **Do not implement this reflexively "because the roadmap says Phase 2" — check whether
  it's still needed first.**
- **`BLOCK`-level tree nodes don't exist.** `ROW` is the current leaf level of the layout tree.
  Nothing built so far needed finer text-run splitting within a row. `LayoutNodeType.BLOCK` is
  defined as an enum value but nothing constructs one.

### 4.3 The pipeline as it actually runs today

```
DocumentEngine.process(file) / .processWithDebug(file)
  │
  ▼
PdfDocumentClassifier.classify(document)              → DocumentMetadata (NATIVE/SCANNED/HYBRID)
  │
  ▼ (per page)
TextPrimitiveExtractor.extract()      → List<TextLine>          (via PDFTextStripper subclass, TextPosition-based)
VectorPrimitiveExtractor.extract()    → List<VectorPrimitive>   (PDFGraphicsStreamEngine subclass; RectanglePrimitive/LinePrimitive/CurvePrimitive)
ImagePrimitiveExtractor.extract()     → List<ImagePrimitive>    (PDFGraphicsStreamEngine subclass; drawImage()+CTM)
  │
  ▼
DefaultLayoutAnalyzer.analyzeDocument(List<PageContent>, ctx)  → DocumentLayout (List<LayoutNode> pageRoots, List<ReadingOrder>)
  1. HeaderFooterDetector.detect(pages, ctx)     — cross-page frequency analysis of top/bottom-band
     text lines (>=50% of pages, by default) — document-level, NOT the page-level LayoutDetector
     interface, because this fundamentally can't work one page at a time.
  2. For each page: build a "body" scope excluding header/footer lines, then:
     a. ColumnDetector.detect(bodyScope, ctx)    — bins x-axis coverage (4pt bins), finds gaps
        >=24pt wide, splits into COLUMN nodes. Always returns >=1 properly-typed COLUMN even when
        no gutter exists (never returns the raw scope unchanged).
     b. Per column: row-clustering by Y-proximity (inline in DefaultLayoutAnalyzer, not a separate
        detector) — sorts items by y1 descending, merges into a row while
        item.y1 >= currentRowY0 - rowGroupingGapTolerancePts (2pt default).
     c. Per column: section-grouping (inline) — gap-to-next-row > max(minSectionBreakGapPts=10pt,
        median(gaps) * sectionBreakGapMultiplier=1.8) starts a new SECTION.
     d. RepeatedBlockDetector.detect(section, ctx) — groups a section's ROW children by a coarse
        structural signature (textLine count : vectorPrimitive count : height-bucket); consecutive
        runs >= repeatedBlockMinRunLength (3 default) get tagged with a shared repeatingGroupId
        attribute, mutating the row nodes in place.
  3. Header/footer lines become their own leaf SECTION nodes (role="header"/"footer" attribute),
     attached as first/last children of the page node.
  4. Page node's OWN textLines/vectorPrimitives/images attributes are the FULL, unfiltered
     per-page lists (kept for potential future consumers — NOT used by Stage 4 detection anymore,
     see below).
  │
  ▼
LayoutGraphBuilder.build(pageRoots, readingOrders)   → DocumentGraph
  - PARENT_OF/CHILD_OF edges for every tree containment relationship
  - READING_ORDER_NEXT edges walking each page's ReadingOrder sequence
  - NOT YET consumed by anything downstream (Stage 6 doesn't read the graph — that's Phase 3)
  │
  ▼
PipelineRunner.detectionScopes(pageRoot)             → List<LayoutNode>
  = pageRoot.collect(LayoutNode::isLeaf)                          (ROW leaves + header/footer bands)
  + pageRoot.collect(n -> n.type()==SECTION && !n.isLeaf())       (real multi-row sections)
  (COLUMN nodes excluded — see §2 point 3)
  │
  ▼ for each scope:
DetectorRegistry.detectAll(scope, ctx)   — runs ALL 6 registered detectors against EVERY scope;
  each detector self-filters (reads scope.attribute("textLines"/"vectorPrimitives"), no-ops if
  null/empty/wrong shape):
    RectangleDetector          — RectanglePrimitive, height in [8,40]pt, width>=40pt
    UnderlineDetector          — LinePrimitive, dy<=1.5pt, dx>=30pt
    LabelDetector               — TextLine, <=4 words, <=30 chars, ends-with-":" OR digit-free
    WhitespaceDetector          — interior gap between two items in one scope, >=40pt wide
    ImagePlaceholderDetector    — RectanglePrimitive, height>40pt (NOT RectangleDetector's band),
                                   aspect ratio 0.4-2.5 (roughly square)
    TableDetector               — only fires on non-leaf SECTION scopes with repeatingGroupId-
                                   tagged ROW children; clusters item x-centers into columns
                                   (8pt gap threshold), emits one candidate per (row,col) cell
  DetectorRegistry stamps candidate.withScopeNodeId(scope.id()) on every result afterward — the
  detectors themselves never set this.
  │
  ▼
CandidateMerger.merge(allCandidates)     — sort by confidence desc, greedy NMS: keep a candidate
  unless it overlaps (same page, IoU>=0.5) an already-kept higher-confidence one. Only collapses
  duplicate detections of the SAME box.
  │
  ▼
NaiveProximityResolver.resolve(merged, ctx)   — Stage 6, still the Phase 0 placeholder:
  for each LABEL candidate: find nearest same-page RECTANGLE candidate (center distance),
  within maxLabelToValueDistancePts (120pt default); if found, remove it from the pool (one
  value per label, greedy, first-come across labels in input order) and build one SemanticField.
  Confidence = ConfidenceEngine.score([label's own confidence * weight, value's own confidence *
  weight, naive-proximity-score * 1.0]) via WeightedAverageConfidenceAggregator.
  Only LABEL+RECTANGLE pairs become fields — TABLE/WHITESPACE/UNDERLINE/IMAGE_PLACEHOLDER
  candidates are computed but never consumed here. This is the biggest functional gap Phase 3
  needs to close.
  │
  ▼
DocumentResult.of(documentId, pageCount, fields)   → JSON via DocumentResultSerializer (Jackson +
  JavaTimeModule + indented output)

  [processWithDebug() path only, same PipelineRunResult, additionally:]
  PdfDebugOverlayRenderer.render(document, mergedCandidates, result, OverlayStyle.defaults())
    1. Save the caller's PDDocument to bytes, reload a FRESH copy (never mutates caller's object)
    2. Group candidates by page; for each page, open ONE PDPageContentStream in APPEND mode,
       draw every candidate's box (ConfidenceColorScale.colorFor(rawConfidence): HSB hue
       0.0(red)->0.33(green) as confidence 0->1) + caption "detectorId TYPE confidence"
    3. Save the now-annotated copy to bytes → annotatedPdf
    4. Rasterize the SAME annotated copy via PDFRenderer, one PNG per page (150 DPI default) —
       no second pixel-coordinate drawing pass; PDF and PNG can never drift apart
    5. Serialize {result, candidates} to verbose JSON (fresh ObjectMapper, same config as
       DocumentResultSerializer)
    → DebugExport(annotatedPdf, List<byte[]> annotatedPngPerPage, verboseJson)
    → DebugExport.writeTo(dir, baseName) writes 3 files: <baseName>-annotated.pdf,
      <baseName>-page-N.png (one per page), <baseName>-debug.json
```

### 4.4 Full file manifest (main sources, 103 files)

**`core/`** — `DocumentEngine` (public facade: `process()`, `processWithDebug()`),
`PipelineRunner` (the orchestration above), `PipelineRunResult` (result + merged candidates,
lets the debug path see everything without re-running the pipeline), `PipelineContext` (mutable
per-run state: config, metadata, currentPage, documentLayout, documentGraph — the latter two are
set but not yet exposed via `DocumentEngine`'s public API), `EngineConfig` (every tunable
threshold, see §4.6), `PipelineStage` (generic `run(I, ctx)` interface — not actually implemented
by anything yet, an aspirational leftover from the original design).

**`classify/`** — `DocumentType` enum, `PageDimensions`, `DocumentMetadata`,
`DocumentClassifier` interface, `PdfDocumentClassifier` (real: per-page text-char-count vs.
embedded-image-presence heuristic).

**`primitives/model/`** — `TextChar`/`TextWord`/`TextLine`/`TextParagraph` (the last one, unused
— nothing groups lines into paragraphs yet), `RectanglePrimitive`/`LinePrimitive`/`CurvePrimitive`
(all implement `VectorPrimitive`), `VectorPrimitive` interface, `ImagePrimitive`,
`WhitespaceRegion` (unused — a Stage 2 primitive type from the original design that nothing
constructs; `WhitespaceDetector` computes its own gaps directly rather than consuming this type).

**`primitives/extractors/`** — `PrimitiveExtractor<T>` generic interface,
`TextPrimitiveExtractor` (real), `VectorPrimitiveExtractor` (real, generalizes `FormBoxDetector`),
`ImagePrimitiveExtractor` (real, CTM-based placement), `RasterPrimitiveExtractor` (Phase 4 stub —
scanned-page rasterization for OCR/CV, throws `UnsupportedOperationException`).

**`geometry/`** — `BoundingBox` record (x0,y0,x1,y1, PDF points, bottom-left origin) with
`width()`/`height()`/`centerX()`/`centerY()`/`area()`/`overlaps()`/`iou()`/`centerDistanceTo()`/
static `unionOf(List<BoundingBox>)`. The one shared geometry primitive everything else builds on.

**`layout/`** — `LayoutNode` (mutable tree node: id/type/box/page/children/attributes map,
`isLeaf()`, `collect(Predicate)`), `LayoutNodeType` enum (PAGE/COLUMN/SECTION/ROW/BLOCK/FIELD/
VALUE — FIELD/VALUE/BLOCK unused so far), `PageContent` (Stage 2 output bundle fed to the
analyzer), `DocumentLayout` (pageRoots + readingOrders), `ReadingOrder` (list of node IDs),
`LayoutAnalyzer` interface (document-level: `analyzeDocument(List<PageContent>, ctx)`),
`DefaultLayoutAnalyzer` (the real tree-builder, see §4.3).

**`layout/detectors/`** — `LayoutDetector` interface (page-level: `detect(LayoutNode, ctx)`),
`ColumnDetector` (real), `HeaderFooterDetector` (real, document-level, does NOT implement
`LayoutDetector`), `HeaderFooterResult` record, `RepeatedBlockDetector` (real),
`TableRegionDetector` (deliberately unimplemented, superseded — see §4.2).

**`detect/`** — `FieldCandidateDetector` interface (`detectorId()`/`isApplicable(ctx)`/
`detect(scope, ctx)`), `DetectorRegistry` (holds `List<FieldCandidateDetector>`, stamps
`scopeNodeId`), `DetectionCandidate` record (detectorId/box/type/rawConfidence/page/attributes/
scopeNodeId — has a 6-arg secondary constructor defaulting scopeNodeId to null, so detector impls
never deal with provenance), `CandidateType` enum (RECTANGLE/UNDERLINE/CHECKBOX/RADIO/TABLE/
LABEL/SIGNATURE/IMAGE_PLACEHOLDER/HANDWRITING/WHITESPACE — CHECKBOX/RADIO/HANDWRITING unused until
Phase 4), `CandidateMerger` (real, IoU-based NMS).

**`detect/impl/`** — `RectangleDetector`, `UnderlineDetector`, `LabelDetector`,
`WhitespaceDetector`, `ImagePlaceholderDetector`, `TableDetector` (all real, all detailed in
§4.3) — plus `CheckboxDetector`/`RadioGroupDetector`/`SignatureDetector`/`HandwritingRegionDetector`
(all Phase 4 stubs, `isApplicable()` returns `false` unconditionally, `detect()` throws).

**`graph/`** — `DocumentGraph` (nodes map + edges list, `addNode`/`addEdge`/`node(id)`/
`nodesOfType`/`edgesFrom`/`allNodes`/`allEdges`), `GraphNode` record, `GraphEdge` record,
`GraphNodeType` enum (mirrors LayoutNodeType plus TABLE/TABLE_CELL/IMAGE/SIGNATURE — the extras
unused), `EdgeType` enum (PARENT_OF/CHILD_OF/READING_ORDER_NEXT real; NEAREST_LABEL/NEAREST_VALUE/
BELONGS_TO_TABLE/TABLE_CELL_AT/SAME_REPEATING_GROUP all defined but nothing constructs them yet —
that needs Stage 6 to exist), `GraphQuery` (fluent traversal: `GraphQuery.from(graph,
id).outgoing(type)`/`.first(type)`), `LayoutGraphBuilder` (real, builds containment + reading
order edges only).

**`confidence/`** — `ConfidenceContribution` record (source/score/weight), `ConfidenceAggregator`
interface, `ConfidenceEngine` (thin wrapper calling the aggregator), `confidence/impl/
WeightedAverageConfidenceAggregator` (the only aggregator implementation so far — matches the
original design's default, source-type-aware aggregation from §3.6 point 4 is not built).

**`semantic/`** — `SemanticField` record (id/name/inferredType/value/box/page/sectionNodeId/
confidence/detectorId/relatedFieldIds — `value` is ALWAYS null right now, nothing reads box
content), `SemanticResolver` interface, `semantic/impl/NaiveProximityResolver` (the real, but
intentionally naive, only implementation — see §4.3), `semantic/rules/` — `SemanticRule`
interface, `SpatialProximityRule`/`LabelValuePairingRule`/`FieldTypeInferenceRule` (all Phase 3
stubs, throw `UnsupportedOperationException`). **This is exactly where Phase 3 work starts.**

**`output/`** — `DocumentResult` record (documentId/pageCount/fields/generatedAt, `of()` factory
maps `List<SemanticField>` → `List<FieldResult>`), `FieldResult` record (the Stage 7 JSON
contract: name/type/value/coordinates/confidence/page/section/detector/relationships),
`DocumentResultSerializer` (Jackson ObjectMapper + JavaTimeModule + indented output — note:
`generatedAt` serializes as a raw decimal-seconds `Instant` timestamp, e.g.
`1783831742.488230100`, not ISO-8601 — cosmetic, never fixed, harmless for debug/dev use, would
want `WRITE_DATES_AS_TIMESTAMPS` disabled if this ever becomes a real external API contract).

**`debug/`** — `DebugOverlayRenderer` interface (`render(document, candidates, result, style)`),
`PdfDebugOverlayRenderer` (the only real implementation — see §4.3 and §4.5), `OverlayStyle`
record (strokeWidth/drawCaptions/renderDpi, `defaults()` = 1.5f/true/150f), `ConfidenceColorScale`
(static `colorFor(double)` → red-to-green HSB), `DebugExport` record (annotatedPdf +
annotatedPngPerPage + verboseJson, with a `writeTo(Path, String)` I/O convenience method),
`DebugRun` record (result + export, what `DocumentEngine.processWithDebug()` returns).

**`template/`** — `TextAnchor`/`LogoRegion` records, `TemplateFingerprint` record (structuralHash
+ anchors + logoPositions + pageDimensions; `similarityTo()` throws), `LearnedTemplate` record,
`TemplateMatch` record, `FingerprintMatcher` interface, `TemplateStore` interface,
`TemplateRegistry` class (`findBestMatch()` delegates to the matcher; `learn()` throws). **All
Phase 5 — nothing here is implemented, and nothing calls any of it.**

**`ai/`** — `AICandidatePayload` record (ambiguousCandidates/localContext/ocrTextIfAny/
croppedRegionImage — structured-first by construction), `AIResolution` record,
`AIFallbackGateway` interface, `EscalationPolicy` interface, `ThresholdEscalationPolicy` (the
**one real class in this package** — trivially compares `field.confidence() <
ctx.config().lowConfidenceEscalationThreshold()`, deterministic and auditable by design; not
wired into the pipeline anywhere yet since there's no `AIFallbackGateway` implementation to
escalate to). **Phase 6 — otherwise nothing implemented, nothing called.**

**`ocr/`** — `OcrHints`/`OcrResult` records, `OcrEngine`/`CvEngine`/`OnnxInferenceEngine`
interfaces, `NativeResourcePool<T>` (the **one real, generic class** in this package — a working
`ArrayBlockingQueue`-based object pool with `borrow()`/`release()`/`close()`, ready to pool
Tesseract/OpenCV instances once Phase 4 needs it, but nothing constructs one yet). **Phase 4 —
otherwise nothing implemented.**

**`cli/`** — `DocumentEngineCli` (the one main-method class, `gradlew.bat run --args="..."`; not
part of the published library's API).

### 4.5 The debugger, in detail (built ad hoc, in response to explicit user request)

This session added real debug visualization, going beyond both the original Phase 1 spec
("PDF+JSON, no PNG yet") and beyond what any of Phases 0-2 required. Triggered by direct user
ask: *"Add a debugger that should be saving the files in build/detected-fields and that should be
coloring the fields based on the detected confidence."* Full detail already captured in §4.3's
pipeline walkthrough and README's "Debugging" section — the two things worth remembering that
aren't obvious from reading the code cold:

1. **Coloring is confidence-based, not detector-based** — this was a deliberate override of the
   *original* design (which said "different colors per detector"). Detector identity didn't
   disappear; it just moved from color into the caption text (`"detectorId TYPE confidence"`).
   If a future ask wants detector-based coloring back, that's a straightforward
   `OverlayStyle`/`ConfidenceColorScale` swap, not a redesign.
2. **The renderer draws the PRE-RESOLUTION candidate pool, not just final `DocumentResult`
   fields.** This was a deliberate choice specifically because the user asked how detection
   works and whether multiple boxes become one input or an array — showing only the final,
   narrow `fields` list would have hidden exactly the behavior being asked about. `PipelineRunner`
   had to change its return type (`PipelineRunResult`, carrying `mergedCandidates`) specifically
   to make this possible; `DocumentEngine.process()`'s existing behavior/signature was preserved
   unchanged, `processWithDebug()` is additive.

**A real, load-bearing bug was caught and fixed during this work**: the CLI's final status
message tried to build a human-readable path hint using `Path.resolve("basename-*")` — the literal
`*` wildcard character is illegal in a Windows path and `Path.resolve()` throws
`InvalidPathException` even though nothing was ever going to open that path as a file. Fixed by
building the hint as a plain string concatenation instead of a `Path` operation. **Lesson: never
use `Path`/`Paths` APIs for messages that aren't real filesystem operations — string concat is
correct there.**

**A verified example exists proving the array-vs-single-input behavior** (see §2 point 4 and
`PdfDebugOverlayRendererTest`): 2 labels + 3 boxes (one deliberately unpaired) → exactly 5 merged
candidates, exactly 2 final fields. Re-run this test (or the equivalent CLI exercise) if you ever
touch `NaiveProximityResolver`, `CandidateMerger`, or `DetectorRegistry` — it's the sharpest
regression check for this specific behavior.

### 4.6 `EngineConfig` — every tunable, current defaults

```java
minRectangleWidthPts = 40.0          // RectangleDetector floor
minRectangleHeightPts = 8.0          // RectangleDetector floor
maxRectangleHeightPts = 40.0         // RectangleDetector ceiling / ImagePlaceholderDetector floor
minUnderlineLengthPts = 30.0         // UnderlineDetector dx floor
maxUnderlineDeltaYPts = 1.5          // UnderlineDetector dy ceiling
maxLabelToValueDistancePts = 120.0   // NaiveProximityResolver pairing radius
lowConfidenceEscalationThreshold = 0.6   // ThresholdEscalationPolicy cutoff (unused — nothing calls it yet)
columnGutterMinWidthPts = 24.0       // ColumnDetector gutter floor
columnGutterBinWidthPts = 4.0        // ColumnDetector x-axis coverage bin size
rowGroupingGapTolerancePts = 2.0     // DefaultLayoutAnalyzer row-clustering Y tolerance
minSectionBreakGapPts = 10.0         // DefaultLayoutAnalyzer section-break floor
sectionBreakGapMultiplier = 1.8      // DefaultLayoutAnalyzer section-break vs. median-gap multiplier
repeatedBlockMinRunLength = 3        // RepeatedBlockDetector / implicitly TableDetector's row-group floor
headerFooterBandFraction = 0.12      // HeaderFooterDetector top/bottom band size (fraction of page height)
headerFooterMinRepeatFraction = 0.5  // HeaderFooterDetector: must repeat on >=50% of pages
detectorWeights = {
    "rectangle-detector": 1.0, "label-detector": 1.0,        // explicit drawn geometry, trusted most
    "underline-detector": 0.9,
    "table-detector": 0.85,
    "image-placeholder-detector": 0.8,
    "whitespace-detector": 0.6                                // inferred, trusted least
}
```
`TableDetector`'s own column-clustering gap (`COLUMN_CLUSTER_GAP_PTS = 8.0`) is hardcoded inside
the class, not in `EngineConfig` — deliberate: it's a narrow heuristic detail, not judged worth
the config-surface cost of yet another tunable (see the class if you disagree and want to promote
it).

---

## 5. Known limitations / gaps — be honest about these, don't paper over them

- **`value` is always `null`.** Nothing reads content inside a detected box. Native AcroForm
  value extraction and OCR'd handwritten/printed content are both entirely unbuilt (Phase 4+).
- **No "one label → many values" grouping** (checkbox groups, radio groups as a single semantic
  field with an array of options) — Stage 6 is still the Phase 0 naive placeholder.
- **`DocumentLayout`/`DocumentGraph` aren't exposed via any public API** — only
  `DetectionCandidate`s surface, via `processWithDebug()`. If a consumer needs the tree/graph
  shape itself, they currently have to instantiate `PipelineRunner`/`DefaultLayoutAnalyzer`
  directly rather than going through `DocumentEngine`.
- **`TABLE`/`WHITESPACE`/`UNDERLINE`/`IMAGE_PLACEHOLDER` candidates are computed but never reach
  `DocumentResult`** — only `LABEL`+`RECTANGLE` pairs do. This is arguably the single biggest
  "wait, why isn't X in my output" surprise a new user of this engine will hit. It's expected and
  by design (Stage 6 hasn't been built for real yet), but it is *not yet documented anywhere as a
  loud warning at the API level* — worth considering whether `DocumentResult`/`FieldResult`
  should carry some indicator of this, or whether Phase 3 should just close the gap outright.
- **Confidence aggregation is a flat weighted average across incommensurable signal types** — see
  §3.6 point 4. Not urgent while everything is geometric (Phase 0-2), becomes a real problem the
  moment Phase 4's OCR confidence enters the same aggregation.
- **No profiling has ever been done.** Nothing about `EngineConfig`'s thresholds, `CandidateMerger`'s
  IoU cutoff, or any performance characteristic has been validated against real, messy, varied
  documents — only synthetic test PDFs and one hand-built two/three-field sample. Treat every
  threshold in §4.6 as a first-guess default, not a calibrated constant.
- **Debug renderer caption text can visually overlap on cramped layouts** — cosmetic, seen on a
  synthetic test form where rows sit very close together; not a detection bug, just a rendering
  rough edge (see README's Debugging section for the concrete example).
- **`WhitespaceRegion` and `TextParagraph` primitive types are defined but never constructed** —
  leftover scaffolding from the original Stage 2 design that no detector ended up needing in this
  form.
- **No sample/fixture PDFs are committed to this repo.** Every test builds its own synthetic PDF
  inline via PDFBox. There is no corpus of real-world documents to validate against — this is
  precisely the gap the original design's Phase 0 called "the practical starting point," and it
  still hasn't been done. **If you're picking this project up to make real progress rather than
  add more scaffolding, assembling a small real-document corpus and running the debugger against
  it by hand is probably higher-value than writing more unit tests.**

---

## 6. How to build / test / run (see README.md for the full, user-facing version)

```powershell
cd E:\PranicDoc\PranicDoc\documentUnderstandingEngine
gradlew.bat build                                    # full build + all 18 tests
gradlew.bat test                                      # tests only
gradlew.bat run --args="C:\path\to\form.pdf"          # prints DocumentResult JSON, writes
                                                        # build/detected-fields/<name>-annotated.pdf,
                                                        # -page-N.png, -debug.json
```

No sample PDF is committed — build one inline via PDFBox (see any test's `buildSyntheticPdf()`
method, e.g. `Phase0PipelineTest` or `PdfDebugOverlayRendererTest`) or point the CLI at any real
PDF on disk.

---

## 7. What to do next (Phase 3, concretely)

Per the roadmap (§3.9) and the discipline in §3.7, **the next phase is Phase 3: the real Stage 6
rule chain**, not Phase 4/5/6. Concretely, that means:

1. Implement `SpatialProximityRule`, `LabelValuePairingRule`, `FieldTypeInferenceRule`
   (`semantic/rules/`) for real, replacing/extending `NaiveProximityResolver`'s naive-only
   approach. These should consume the `DocumentGraph` (already built by `LayoutGraphBuilder`,
   currently unused downstream) — this is the natural point where `NEAREST_LABEL`/`NEAREST_VALUE`/
   `BELONGS_TO_TABLE`/`SAME_REPEATING_GROUP` edges finally get constructed and queried via
   `GraphQuery`, none of which happens today.
2. Extend Stage 6 to consume `TABLE`/`WHITESPACE`/`UNDERLINE`/`IMAGE_PLACEHOLDER` candidates, not
   just `LABEL`+`RECTANGLE` — this closes the biggest gap named in §5.
3. Decide (and document, in this file and in README) how `FieldTypeInferenceRule` should
   represent a "one label → many values" field (checkbox group, radio group) — this needs a
   `SemanticField`/`FieldResult` shape decision, since the current model assumes exactly one
   value per field.
4. Finalize the real Stage 7 JSON schema — right now `FieldResult`'s shape is whatever fell out
   of Phase 0's first pass; Phase 3 is when the original design says this should be locked down
   for real.
5. Test discipline to continue: unit-test each new `SemanticRule` in isolation (constructing a
   `DocumentGraph`/candidate list directly, same pattern as every existing detector test), plus
   extend the end-to-end test coverage to prove the whole chain still produces sane output on the
   existing synthetic fixtures before moving to Phase 4.
6. Before writing any Phase 3 code, re-read §3.6 point 1 (the pipeline-leak self-critique) and
   §3.4's honesty flag about template fingerprinting risk — Phase 3 is exactly where "spatial
   reasoning, not template matching" either holds up as a real design principle or starts leaking
   template-specific assumptions. Watch for that specifically.

Do **not** start Phase 4 (OCR/CV) before Phase 3 lands — Phase 4's whole point (per §3.6 point 4)
is bringing a second, incommensurable confidence signal into an aggregation model that Phase 3 is
supposed to have made trustworthy first.

---

## 8. Session Log

Append new entries here rather than editing history above. Format: date, phase/task, one
paragraph, commit range.

### Session 1 (this document's origin)
Full architecture design produced via Claude Code Plan Mode (approved by user); scoping decisions
made via `AskUserQuestion` (standalone repo, Java 21, full OCR/CV stack from day one). Scaffolded
the module (`54a8c61`), then implemented Phase 0 real end-to-end slice in the same commit.
Implemented Phase 1 — full primitive extraction + real Page→Column→Section→Row tree +
DocumentGraph containment/reading-order edges (`23e63aa`). Implemented Phase 2 — remaining
native-PDF detector set + Stage 4 rewired from flat page-level scanning to real tree scopes
(`4e0cbc0`). Added a CLI entry point (`application` plugin) and a comprehensive README covering
architecture/testing/startup (`d42f400`). Built a real debug renderer on explicit user request —
confidence-colored annotated PDF + per-page PNG + verbose candidate JSON, always written to
`build/detected-fields/` by the CLI (`b7c45db`) — and used it to give a concrete, tested answer to
"does a section with multiple boxes become one input or an array" (always an array; verified with
`PdfDebugOverlayRendererTest`). All 18 tests green across every commit. This handover document
written at the end of this session, before Phase 3 work begins.
