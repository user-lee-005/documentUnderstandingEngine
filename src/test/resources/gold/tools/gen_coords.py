"""Generate coordinate ground truth for CL01: caption->container matching.

Coordinates: PDF points, bottom-left origin (engine convention).
- caption rect: small rect (h 5-9pt) painted behind a caption on a container border
- container: larger rect whose top edge passes through the caption rect
- valueBox: container interior (where the value is / should be placed)
Also dumps curves (rounded checkbox outlines) and diagnostics.
"""
import json
import pdfplumber

PDF = r"E:\PranicDoc\PranicDoc\documentUnderstandingEngine\src\test\resources\gold\cl01\source.pdf"
OUT = r"C:\Users\venki\AppData\Local\Temp\claude\E--PranicDoc-PranicDoc-documentUnderstandingEngine\522200f5-4641-4add-82a6-6f54e4a98169\scratchpad\fieldmap.json"


def decode_cid(s):
    """This template's caption font maps cid -> ascii+29."""
    out = []
    i = 0
    while i < len(s):
        if s.startswith("(cid:", i):
            j = s.index(")", i)
            n = int(s[i + 5:j])
            c = chr(n + 29)
            out.append("'" if n == 183 else c)
            i = j + 1
        else:
            out.append(s[i])
            i += 1
    return "".join(out)


def bl(h, x0, top, x1, bottom):
    return [round(x0, 2), round(h - bottom, 2), round(x1, 2), round(h - top, 2)]


def overlap_x(a, b):
    return min(a[2], b[2]) - max(a[0], b[0])


pages_out = []
with pdfplumber.open(PDF) as pdf:
    for pi, page in enumerate(pdf.pages):
        H = page.height
        rects = [bl(H, r["x0"], r["top"], r["x1"], r["bottom"]) for r in page.rects]
        curves = [bl(H, c["x0"], c["top"], c["x1"], c["bottom"]) for c in page.curves]
        words = []
        for w in page.extract_words(keep_blank_chars=False):
            box = bl(H, w["x0"], w["top"], w["x1"], w["bottom"])
            words.append({"text": decode_cid(w["text"]), "box": box})

        # caption rects: thin horizontal strips; containers: everything taller
        cap_rects = [r for r in rects if 4.0 <= (r[3] - r[1]) <= 10.0 and (r[2] - r[0]) > 10.0]
        containers = [r for r in rects if (r[3] - r[1]) > 12.0 and (r[2] - r[0]) > 12.0]

        captions = []
        for cr in cap_rects:
            inside = [w for w in words
                      if w["box"][0] >= cr[0] - 2 and w["box"][2] <= cr[2] + 6
                      and w["box"][1] >= cr[1] - 2 and w["box"][3] <= cr[3] + 2]
            if not inside:
                continue
            text = " ".join(w["text"] for w in sorted(inside, key=lambda w: w["box"][0]))
            # container whose top edge passes through this caption strip
            best = None
            for c in containers:
                if cr[1] - 2 <= c[3] <= cr[3] + 2 and overlap_x(c, cr) > 5:
                    if best is None or (c[2] - c[0]) * (c[3] - c[1]) < (best[2] - best[0]) * (best[3] - best[1]):
                        best = c
            captions.append({"caption": text, "labelBox": cr, "container": best})

        pages_out.append({
            "page": pi, "width": round(page.width, 2), "height": round(H, 2),
            "captions": captions,
            "containers": containers,
            "curves": curves,
            "words": words,
        })

with open(OUT, "w", encoding="utf-8") as f:
    json.dump({"pages": pages_out}, f, indent=1)

for p in pages_out:
    print(f"--- page {p['page']}: captions={len(p['captions'])} containers={len(p['containers'])} curves={len(p['curves'])}")
    for c in p["captions"]:
        cont = "-> " + ",".join(str(v) for v in c["container"]) if c["container"] else "-> NO CONTAINER"
        print(f"  [{c['caption']}] {cont}")
