# Gold dataset — coordinate ground truth for accuracy evaluation

Each subdirectory is one sample document:

```
gold/
├── cl01/
│   ├── source.pdf          — the exact PDF fed to the engine
│   ├── expected.json       — perfect extraction of source.pdf, WITH coordinates
│   └── review-page-N.png   — visual proof: expected.json boxes drawn on the page
└── tools/                  — python scripts that generated/verify the ground truth
```

## Purpose

The primary consumer is **pdfWorker-style placement**: given form data (see pdfWorker's
`CaseSnapshot`), know *where on the template each value must be written* — without
hardcoding coordinates in Java. Extraction accuracy (reading filled forms) is scored
against the same file.

## Coordinate conventions

All boxes are `[x0, y0, x1, y1]`, **PDF points, bottom-left origin** (the engine's
convention — no Y-flip anywhere).

- `labelBox` — the printed caption strip (in CL01, captions sit ON a container's top border)
- `valueBox` — the writable area: where a value **is** (this sample) or **must be placed**
  (blank fields; derived from container interior / row pitch / underline extent)
- `static: true|false` — single-value field vs. repeating structure
- checkbox-group `options[].box` — the tick target; a mark goes at its center
- `type: "array"` + `items[]` — repeating fields (SYMPTOMS 1–4, MEDICATION 1–6), one
  `valueBox` per item; `arrayEvidence` records the geometric signal that makes it an array
- `type: "table"` + `grid` — `bbox` + `rowYs`/`colXs` rule positions; cell (i,j) is the
  region between consecutive boundaries. `rowGroups` lists pre-printed row headers
  (e.g. the 25 chakra names on page 4)

## How arrays are distinguished from static fields (from the form alone)

Three geometric signals, all present in CL01 and all detectable deterministically:

1. **Numbered caption sequence** — same caption stem + increasing integer
   (`SYMPTOM 1..4`), identical container geometry, fixed vertical pitch
2. **Repeating uncaptioned containers** — N structurally identical rects in a grid
   (medication slots, 3×2)
3. **Ruled grid** — uniform row/column rule lines (chakra table, symptom-scale table)

A field with a unique caption and a single container is static. This is what Stage 4's
`RepeatedBlockDetector`/`TableDetector` already tag and what the Phase 3 rule chain must
turn into `array`/`table` fields in the final schema.

## How this ground truth was made (repeat for each new sample)

1. Drop the PDF in as `gold/<id>/source.pdf`
2. `pip install pdfplumber pypdfium2`, then adapt `tools/` scripts (paths are per-sample):
   - `dump_layout.py` — rects/lines/words dump, bottom-left-origin points
   - `gen_coords.py` — caption→container matching (+ cid→unicode decode, see below)
   - `merge_coords.py` — writes boxes into `expected.json` (CL01-specific field wiring)
   - `render_review.py` — draws every box back onto the page → `review-page-N.png`
3. **Review the PNGs by eye** — blue=label, green=value, orange=checkbox, purple=grid
4. Human confirms values/boxes; delete any `_review` keys

## Known quirk: caption font has no ToUnicode CMap

Captions in pdfWorker-generated CL01 PDFs extract as raw glyph codes
(`(cid:36)(cid:42)(cid:40)` = "AGE"; printable chars decode as `chr(cid + 29)`). This is why
the engine's `LabelDetector` produced garbage names like `"$*("`. Long-term fix belongs in
**pdfWorker** (embed the caption font with a proper ToUnicode CMap); until then any consumer
of this template must apply the +29 decode for caption text.
