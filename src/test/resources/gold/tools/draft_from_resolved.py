"""Convert an engine-resolved JSON into a gold expected-draft.json + review overlay PNGs.

Usage: python draft_from_resolved.py <resolved.json> <gold-sample-dir>

The draft is the ENGINE'S OWN OUTPUT reshaped into the gold schema — it is pseudo-gold:
a human must review the overlay PNGs, correct labels/boxes/values, then rename the file
to expected.json for the eval harness to pick it up. Until renamed, it is not scored.
"""
import json
import sys

import pdfplumber
from PIL import ImageDraw

SCALE = 2.0


def arr(box):
    if box is None:
        return None
    return [round(box["x0"], 1), round(box["y0"], 1), round(box["x1"], 1), round(box["y1"], 1)]


def main(resolved_path, gold_dir):
    r = json.load(open(resolved_path, encoding="utf-8"))
    pages = []
    for p in r["pages"]:
        sections = []
        for s in p["sections"]:
            fields = []
            for f in s["fields"]:
                out = {"label": f.get("label"), "type": f.get("type"), "value": f.get("value")}
                if f.get("labelBox"):
                    out["labelBox"] = arr(f["labelBox"])
                if f.get("valueBox"):
                    out["valueBox"] = arr(f["valueBox"])
                if f.get("options"):
                    out["options"] = [{"name": o["name"], "box": arr(o.get("box")),
                                       "selected": o.get("selected", False)} for o in f["options"]]
                if f.get("items"):
                    out["items"] = [{"index": i["index"], "labelBox": arr(i.get("labelBox")),
                                     "valueBox": arr(i.get("valueBox")), "value": i.get("value")}
                                    for i in f["items"]]
                fields.append(out)
            sections.append({"name": s.get("name"), "fields": fields})
        pages.append({"page": p["page"], "sections": sections})

    draft = {
        "_draft": "ENGINE OUTPUT, UNREVIEWED — verify against review-page-N.png, correct, then rename to expected.json",
        "formType": gold_dir.rstrip("\\/").split("\\")[-1].split("/")[-1],
        "pageCount": r.get("pageCount", len(pages)),
        "coordinateSystem": "PDF points, bottom-left origin, per page; box = [x0, y0, x1, y1]",
        "pages": pages,
    }
    out_path = gold_dir + "/expected-draft.json"
    json.dump(draft, open(out_path, "w", encoding="utf-8"), indent=2, ensure_ascii=False)
    print("wrote", out_path)

    # overlays: blue=labelBox, green=valueBox, orange=option boxes
    def draw_box(draw, H, box, color, width=2):
        if not box:
            return
        x0, y0, x1, y1 = box
        draw.rectangle([x0 * SCALE, (H - y1) * SCALE, x1 * SCALE, (H - y0) * SCALE], outline=color, width=width)

    with pdfplumber.open(gold_dir + "/source.pdf") as pdf:
        by_page = {p["page"]: p for p in pages}
        for pi, page in enumerate(pdf.pages):
            pg = by_page.get(pi)
            if not pg or not pg["sections"]:
                continue
            im = page.to_image(resolution=int(72 * SCALE))
            draw = ImageDraw.Draw(im.original)
            H = page.height
            for s in pg["sections"]:
                for f in s["fields"]:
                    draw_box(draw, H, f.get("labelBox"), "#1565c0")
                    draw_box(draw, H, f.get("valueBox"), "#2e7d32")
                    for o in f.get("options", []):
                        draw_box(draw, H, o.get("box"), "#e65100")
                    for it in f.get("items", []):
                        draw_box(draw, H, it.get("labelBox"), "#1565c0")
                        draw_box(draw, H, it.get("valueBox"), "#2e7d32")
            im.original.save(f"{gold_dir}/review-page-{pi}.png")
            print("wrote", f"{gold_dir}/review-page-{pi}.png")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
