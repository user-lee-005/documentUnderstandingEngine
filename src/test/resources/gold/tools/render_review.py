"""Render review overlays: blue=labelBox, green=valueBox, orange=option/checkbox, purple=grid.

Usage: python render_review.py <gold-dir> [json-filename]
  <gold-dir>      e.g. src/test/resources/gold/intake-form-v2
  [json-filename]  defaults to expected.json if present, else expected-draft.json — pass this
                    explicitly to re-render a draft mid-review before it's promoted.

Re-run this after hand-editing a draft/expected JSON to visually confirm your coordinate fixes
landed where you meant them to, before promoting expected-draft.json -> expected.json.
"""
import json
import os
import sys

import pdfplumber
from PIL import ImageDraw

GOLD_DIR = sys.argv[1] if len(sys.argv) > 1 else \
    r"E:\PranicDoc\PranicDoc\documentUnderstandingEngine\src\test\resources\gold\cl01"

if len(sys.argv) > 2:
    JSON_NAME = sys.argv[2]
elif os.path.exists(os.path.join(GOLD_DIR, "expected.json")):
    JSON_NAME = "expected.json"
else:
    JSON_NAME = "expected-draft.json"

SCALE = 2.0  # 144 dpi


def draw_box(draw, H, box, color, width=2):
    if not box:
        return
    x0, y0, x1, y1 = [v * SCALE for v in box]
    draw.rectangle([x0, (H - y1 / SCALE) * SCALE, x1, (H - y0 / SCALE) * SCALE], outline=color, width=width)


gold = json.load(open(os.path.join(GOLD_DIR, JSON_NAME), encoding="utf-8"))
pages_gold = {p["page"]: p for p in gold["pages"]}

with pdfplumber.open(os.path.join(GOLD_DIR, "source.pdf")) as pdf:
    for pi, page in enumerate(pdf.pages):
        pg = pages_gold.get(pi)
        if not pg or not pg["sections"]:
            continue
        im = page.to_image(resolution=int(72 * SCALE))
        draw = ImageDraw.Draw(im.original)
        H = page.height

        def walk_field(f):
            draw_box(draw, H, f.get("labelBox"), "#1565c0")
            draw_box(draw, H, f.get("valueBox"), "#2e7d32")
            for o in f.get("options", []) if isinstance(f.get("options"), list) else []:
                if isinstance(o, dict):
                    draw_box(draw, H, o.get("box"), "#e65100")
            for it in f.get("items", []):
                draw_box(draw, H, it.get("labelBox"), "#1565c0")
                draw_box(draw, H, it.get("valueBox"), "#2e7d32")
            g = f.get("grid")
            if g:
                draw_box(draw, H, g["bbox"], "#6a1b9a", 3)
                bx = g["bbox"]
                for y in g["rowYs"]:
                    draw.line([bx[0] * SCALE, (H - y) * SCALE, bx[2] * SCALE, (H - y) * SCALE], fill="#6a1b9a", width=1)
                for x in g["colXs"]:
                    draw.line([x * SCALE, (H - bx[1]) * SCALE, x * SCALE, (H - bx[3]) * SCALE], fill="#6a1b9a", width=1)
            for slot in f.get("symptomNameSlots", []):
                draw_box(draw, H, slot.get("labelBox"), "#1565c0")
                draw_box(draw, H, slot.get("valueBox"), "#2e7d32")

        for s in pg["sections"]:
            for f in s["fields"]:
                walk_field(f)
        out = os.path.join(GOLD_DIR, f"review-page-{pi}.png")
        im.original.save(out)
        print("wrote", out)
