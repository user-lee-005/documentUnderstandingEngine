"""Dump per-page rects and text lines from the CL01 PDF, bottom-left-origin PDF points."""
import json
import pdfplumber

PDF = r"E:\PranicDoc\PranicDoc\documentUnderstandingEngine\src\test\resources\gold\cl01\source.pdf"
OUT = r"C:\Users\venki\AppData\Local\Temp\claude\E--PranicDoc-PranicDoc-documentUnderstandingEngine\522200f5-4641-4add-82a6-6f54e4a98169\scratchpad\layout-dump.json"

def bl(page, x0, top, x1, bottom):
    """pdfplumber top-origin -> bottom-left-origin box."""
    h = page.height
    return [round(x0, 2), round(h - bottom, 2), round(x1, 2), round(h - top, 2)]

pages_out = []
with pdfplumber.open(PDF) as pdf:
    for i, page in enumerate(pdf.pages):
        # rects (bordered containers, checkboxes)
        rects = [bl(page, r["x0"], r["top"], r["x1"], r["bottom"]) for r in page.rects]
        # horizontal lines (underlines, table rules)
        hlines = [bl(page, l["x0"], l["top"], l["x1"], l["bottom"])
                  for l in page.lines if abs(l["top"] - l["bottom"]) < 2.0]
        vlines = [bl(page, l["x0"], l["top"], l["x1"], l["bottom"])
                  for l in page.lines if abs(l["x0"] - l["x1"]) < 2.0]
        # words grouped into lines by top coordinate
        words = page.extract_words(keep_blank_chars=False, use_text_flow=False)
        lines = {}
        for w in words:
            key = round(w["top"], 1)
            lines.setdefault(key, []).append(w)
        text_lines = []
        for key in sorted(lines):
            ws = sorted(lines[key], key=lambda w: w["x0"])
            text = " ".join(w["text"] for w in ws)
            box = bl(page, min(w["x0"] for w in ws), min(w["top"] for w in ws),
                     max(w["x1"] for w in ws), max(w["bottom"] for w in ws))
            text_lines.append({"text": text, "box": box})
        pages_out.append({
            "page": i,
            "width": round(page.width, 2), "height": round(page.height, 2),
            "rects": rects, "hlines": hlines, "vlines": vlines,
            "textLines": text_lines,
        })

with open(OUT, "w", encoding="utf-8") as f:
    json.dump({"pages": pages_out}, f, indent=1)
print("pages:", len(pages_out))
for p in pages_out:
    print(f"page {p['page']}: rects={len(p['rects'])} hlines={len(p['hlines'])} vlines={len(p['vlines'])} textLines={len(p['textLines'])}")
