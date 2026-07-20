"""Merge geometry from fieldmap.json into gold/cl01/expected.json.

Box convention: [x0, y0, x1, y1], PDF points, bottom-left origin (engine convention).
- labelBox : printed caption strip
- valueBox : where the value is / must be placed (container interior, value row,
             or underline strip). For blank fields this is derived geometry.
- checkbox-group options carry their own box; a mark is placed at the box center.
"""
import json

SCRATCH = r"C:\Users\venki\AppData\Local\Temp\claude\E--PranicDoc-PranicDoc-documentUnderstandingEngine\522200f5-4641-4add-82a6-6f54e4a98169\scratchpad"
GOLD = r"E:\PranicDoc\PranicDoc\documentUnderstandingEngine\src\test\resources\gold\cl01\expected.json"

fm = json.load(open(SCRATCH + r"\fieldmap.json", encoding="utf-8"))
gold = json.load(open(GOLD, encoding="utf-8"))
dump = json.load(open(SCRATCH + r"\layout-dump.json", encoding="utf-8"))

P = {p["page"]: p for p in fm["pages"]}
D = {p["page"]: p for p in dump["pages"]}
issues = []


def r2(box):
    return [round(v, 1) for v in box]


def cap(page, prefix, nth=0):
    """nth caption whose decoded text starts with prefix (top-to-bottom order)."""
    hits = [c for c in P[page]["captions"] if c["caption"].upper().startswith(prefix.upper())]
    hits.sort(key=lambda c: (-c["labelBox"][1], c["labelBox"][0]))
    if nth < len(hits):
        return hits[nth]
    issues.append(f"page {page}: caption not found: {prefix!r} #{nth}")
    return None


def words_in(page, box, pad=2.0):
    return [w for w in P[page]["words"]
            if w["box"][0] >= box[0] - pad and w["box"][2] <= box[2] + pad
            and w["box"][1] >= box[1] - pad and w["box"][3] <= box[3] + pad]


def interior(c, cap_h=8.0):
    """Container interior below the caption strip on its top border."""
    return r2([c[0] + 5, c[1] + 4, c[2] - 5, c[3] - cap_h - 3])


def value_row(page, cap_box, col_x1, drop=19.0):
    """Writing band directly under a caption strip, bounded right by col_x1."""
    return r2([cap_box[0], cap_box[1] - drop, col_x1, cap_box[1] - 2])


def curve_box_for_word(page, text, band=None):
    """Smallest curve bbox containing a word with this exact decoded text."""
    cands = []
    for w in P[page]["words"]:
        if w["text"].upper() != text.upper():
            continue
        if band and not (band[1] <= w["box"][1] and w["box"][3] <= band[3]):
            continue
        for cv in P[page]["curves"]:
            if (cv[0] <= w["box"][0] and w["box"][2] <= cv[2]
                    and cv[1] <= w["box"][1] - 1 and w["box"][3] <= cv[3] + 1):
                cands.append((abs((cv[2] - cv[0]) * (cv[3] - cv[1])), cv))
    if cands:
        return r2(min(cands)[1])
    issues.append(f"page {page}: no curve box for option {text!r} band={band}")
    return None


def underline_after(page, cap_box):
    """Underline (hline) on the caption's row, starting at/after the caption."""
    best = None
    for hl in D[page]["hlines"]:
        if abs(hl[1] - cap_box[1]) < 6 and hl[2] > cap_box[2] - 2:
            x0 = max(hl[0], cap_box[2])
            if best is None or x0 < best[0]:
                best = [x0, hl[1], hl[2], hl[1]]
    if best is None:
        issues.append(f"page {page}: no underline after caption at {cap_box}")
        return None
    return r2([best[0] + 2, best[1] + 1, best[2] - 2, best[1] + 13])


def field(section, label):
    for f in section["fields"]:
        if f["label"] == label:
            return f
    raise KeyError(label)


def sec(page_no, name):
    for pg in gold["pages"]:
        if pg["page"] == page_no:
            for s in pg["sections"]:
                if s["name"] == name:
                    return s
    raise KeyError((page_no, name))


def set_boxes(f, caption, value_box, static=True):
    f["static"] = static
    if caption:
        f["labelBox"] = r2(caption["labelBox"])
    f["valueBox"] = value_box


# ---------------- PAGE 1 ----------------
s = sec(1, "PATIENT INFORMATION")
c = cap(1, "PATIENT'S NAME")
set_boxes(field(s, "PATIENT'S NAME"), c, interior(c["container"]))

c = cap(1, "AGE")
set_boxes(field(s, "AGE"), c, value_row(1, c["labelBox"], 105.0))

c = cap(1, "GENDER")
f = field(s, "GENDER")
set_boxes(f, c, None)
# M/F are circles whose letters don't extract as words -> assign circles by x-order
circles = sorted([cv for cv in P[1]["curves"]
                  if 699 <= cv[1] and cv[3] <= 712 and 10 <= (cv[2] - cv[0]) <= 14
                  and cv[0] < 160], key=lambda b: b[0])
f["options"] = [{"name": n, "box": r2(b), "_review": "circle assigned by position (M left, F right); letter glyphs not text-extractable"}
                for n, b in zip(["M", "F"], circles)]
f.pop("valueBox")

c = cap(1, "MARITAL STATUS")
f = field(s, "MARITAL STATUS")
set_boxes(f, c, None)
f["options"] = [{"name": n, "box": curve_box_for_word(1, n, [0, 695, 600, 712])}
                for n in ["SINGLE", "MARRIED", "OTHERS"]]
f.pop("valueBox")

c = cap(1, "EMAIL")
set_boxes(field(s, "EMAIL"), c, value_row(1, c["labelBox"], 280.0))
c = cap(1, "CONTACT")
set_boxes(field(s, "CONTACT / WHATSAPP"), c, value_row(1, c["labelBox"], 280.0))
c = cap(1, "OCCUPATION")
set_boxes(field(s, "OCCUPATION"), c, value_row(1, c["labelBox"], 280.0))

c = cap(1, "ADDRESS LINE")
addr = c["container"]  # right block; address area is above CITY caption
city = cap(1, "CITY")
set_boxes(field(s, "ADDRESS LINE 1 & 2"), c, r2([addr[0] + 5, city["labelBox"][3] + 2, addr[2] - 5, addr[3] - 11]))
set_boxes(field(s, "CITY"), city, value_row(1, city["labelBox"], 455.0))
c = cap(1, "STATE")
set_boxes(field(s, "STATE"), c, value_row(1, c["labelBox"], 455.0))
c = cap(1, "ZIP / PINCODE")
set_boxes(field(s, "ZIP / PINCODE"), c, value_row(1, c["labelBox"], 540.0))

s = sec(1, "PATIENT'S CONDITION")
c = cap(1, "MEDICAL CONDITION")
set_boxes(field(s, "MEDICAL CONDITION / DIAGNOSIS"), c, interior(c["container"]))

# symptoms -> one array field
sym_cap = cap(1, "SYMPTOMS")
sym_box = sym_cap["container"]
items = []
old = {f["label"]: f for f in s["fields"]}
for i in range(1, 5):
    ci = cap(1, f"SYMPTOM {i}")
    items.append({
        "index": i, "labelBox": r2(ci["labelBox"]),
        "valueBox": value_row(1, ci["labelBox"], sym_box[2] - 8),
        "value": old[f"SYMPTOM {i}"]["value"],
    })
s["fields"] = [f for f in s["fields"] if not f["label"].startswith("SYMPTOM")]
s["fields"].append({
    "label": "SYMPTOMS", "type": "array", "itemType": "text", "static": False,
    "arrayEvidence": "numbered caption sequence SYMPTOM 1..4, identical geometry, fixed pitch",
    "labelBox": r2(sym_cap["labelBox"]), "groupBox": r2(sym_box), "items": items,
})

s = sec(1, "DECLARATION")
# three ticked squares on the left of each declaration line (curves)
decl_lines = [307.37, 287.37, 255.37]
decl_labels = ["Not meant to replace conventional medicine", "Release from liabilities",
               "Consent to use testimonial"]
for label, y in zip(decl_labels, decl_lines):
    boxes = [cv for cv in P[1]["curves"]
             if cv[0] < 65 and abs((cv[1] + cv[3]) / 2 - (y + 5)) < 8
             and 6 < (cv[2] - cv[0]) < 18]
    f = field(s, label)
    f["static"] = True
    if boxes:
        f["valueBox"] = r2(boxes[0])
    else:
        issues.append(f"declaration checkbox square not found near y={y}")

c = cap(1, "SIGNATURE OF THE PATIENT")
set_boxes(field(s, "SIGNATURE OF THE PATIENT"), c, underline_after(1, c["labelBox"]))
c = cap(1, "DATE")
set_boxes(field(s, "DATE"), c, underline_after(1, c["labelBox"]))
c = cap(1, "NAME OF THE HEALER")
set_boxes(field(s, "NAME OF THE HEALER"), c, underline_after(1, c["labelBox"]))

s = sec(1, "HEALING DETAILS")
c = cap(1, "HEALING DETAILS")
set_boxes(field(s, "HEALING DETAILS"), c, interior(c["container"]))
bb = cap(1, "BASIC BOOK PG. NO.")
ab = cap(1, "ADVANCED BOOK PG. NO.")
pb = cap(1, "PSYCHOTHERAPY PG. NO.")
right = bb["container"]
set_boxes(field(s, "BASIC BOOK PG. NO."), bb, r2([right[0] + 5, ab["labelBox"][3] + 2, right[2] - 5, bb["labelBox"][1] - 2]))
set_boxes(field(s, "ADVANCED BOOK PG. NO."), ab, r2([right[0] + 5, pb["labelBox"][3] + 2, right[2] - 5, ab["labelBox"][1] - 2]))
set_boxes(field(s, "PSYCHOTHERAPY PG. NO."), pb, r2([right[0] + 5, right[1] + 4, right[2] - 5, pb["labelBox"][1] - 2]))

# ---------------- PAGE 2 ----------------
s = sec(2, "FINDINGS AND OTHER INFORMATION")
c = cap(2, "PERTINENT LAB")
set_boxes(field(s, "PERTINENT LAB, XRAY AND OTHER FINDINGS"), c, interior(c["container"]))
c = cap(2, "ANY OTHER RELEVANT INFORMATION")
set_boxes(field(s, "ANY OTHER RELEVANT INFORMATION"), c, interior(c["container"]))

for capname, glabel, elabel in [
    ("ARE YOU CURRENTLY PRACTISING", "ARE YOU CURRENTLY PRACTISING OR RECEIVING ANY COMPLEMENTARY OR ALTERNATIVE MODALITIES?", "If yes, explain here (modalities)"),
    ("HAVE YOU RECENTLY BEEN DIAGNOSED", "HAVE YOU RECENTLY BEEN DIAGNOSED WITH ANY INFECTIOUS ILLNESS?", "If yes, explain here (infectious illness)"),
]:
    c = cap(2, capname)
    box = c["container"]
    f = field(s, glabel)
    set_boxes(f, c, None)
    f["options"] = [{"name": n, "box": curve_box_for_word(2, n, box)} for n in ["YES", "NO"]]
    f.pop("valueBox")
    fe = field(s, elabel)
    fe["static"] = True
    # explain area: below the YES/NO row down to container bottom
    ybot = min((o["box"][1] for o in f["options"] if o["box"]), default=box[3] - 60)
    fe["valueBox"] = r2([box[0] + 5, box[1] + 4, box[2] - 5, ybot - 22])

# medication slots: containers with no caption strip, sorted reading order
s = sec(2, "MEDICATION AND SUPPLEMENTS")
matched = {tuple(c["container"]) for c in P[2]["captions"] if c["container"]}
slots = [c for c in P[2]["containers"] if tuple(c) not in matched]
slots.sort(key=lambda b: (-b[1], b[0]))
if len(slots) != 6:
    issues.append(f"page 2: expected 6 medication slots, found {len(slots)}")
old = {f["label"]: f for f in s["fields"]}
s["fields"] = [{
    "label": "MEDICATION AND SUPPLEMENTS", "type": "array", "itemType": "text", "static": False,
    "arrayEvidence": "6 structurally identical numbered containers in a 3x2 grid",
    "items": [{"index": i + 1, "valueBox": interior(b, cap_h=2.0),
               "value": old[str(i + 1)]["value"]} for i, b in enumerate(slots)],
}]

# ---------------- PAGE 3 ----------------
s = sec(3, "MEDICAL, SURGICAL AND SOCIAL HISTORY")


def yn_group(capname, glabel, options):
    c = cap(3, capname)
    box = c["container"]
    f = field(s, glabel)
    set_boxes(f, c, None)
    f["options"] = [{"name": n, "box": curve_box_for_word(3, n, box)} for n in options]
    f.pop("valueBox")
    return c, box


yn_group("OVERALL GENERAL HEALTH", "OVERALL GENERAL HEALTH", ["GOOD", "POOR", "EXCELLENT"])
c, box = yn_group("DIET", "DIET", ["VEGETARIAN", "NON-VEGETARIAN", "VEGAN"])
# write-in area right of the italic hint text
# write-in band starts just after the italic hint text (ends x=303.36) -- never hug the right edge
fe = field(s, "DIET - if others, please write here")
fe["static"] = True
fe["valueBox"] = r2([311.0, box[1] + 4, box[2] - 10, box[3] - 4])
c, box = yn_group("EXERCISE", "EXERCISE", ["YES", "NO"])
fe = field(s, "EXERCISE - if yes, what is the frequency")
fe["static"] = True
fe["valueBox"] = r2([220.0, box[1] + 4, box[2] - 10, box[3] - 4])
yn_group("HYPERTENSION", "HYPERTENSION", ["YES", "NO"])
yn_group("GASTROINTESTINAL", "GASTROINTESTINAL (ABDOMINAL PAIN, VOMITING, DIARRHEA)", ["YES", "NO"])
yn_group("STRESS, DEPRESSION", "STRESS, DEPRESSION, ANXIETY OR PSYCHIATRIC CONDITION", ["YES", "NO"])
yn_group("ARE YOU PREGNANT", "ARE YOU PREGNANT", ["YES", "NO", "NOT SURE"])
c, box = yn_group("CANCER", "CANCER", ["YES", "NO"])
fe = field(s, "CANCER - if yes, specify location")
fe["static"] = True
ybot = min((o["box"][1] for o in field(s, "CANCER")["options"] if o["box"]), default=box[3] - 40)
fe["valueBox"] = r2([box[0] + 5, box[1] + 4, box[2] - 5, ybot - 14])

c = cap(3, "IF YES TO ANY LOCATION")
set_boxes(field(s, "IF YES TO ANY LOCATION ABOVE, PLEASE EXPLAIN MORE"), c, interior(c["container"]))
c = cap(3, "ADDITIONAL NOTES")
set_boxes(field(s, "ADDITIONAL NOTES (ANY RELEVANT MEDICAL CONDITIONS)"), c, interior(c["container"]))

# ---------------- PAGE 4: chakra grid ----------------
s = sec(4, "SCANNING RESULTS - BEFORE & AFTER HEALING")
t = s["fields"][0]
t["static"] = False
hy = sorted({hl[1] for hl in D[4]["hlines"]})
vx = sorted({vl[0] for vl in D[4]["vlines"]})
hxs = [hl[0] for hl in D[4]["hlines"]] + [hl[2] for hl in D[4]["hlines"]]
t["grid"] = {
    "bbox": r2([min(hxs), min(hy), max(hxs), max(hy)]),
    "rowYs": [round(y, 1) for y in hy],
    "colXs": [round(x, 1) for x in vx],
    "note": "rowYs = horizontal rule positions (row boundaries); colXs = vertical rule positions. Cell(i,j) = between consecutive boundaries. REVIEW against page 4 rendering.",
}
t["arrayEvidence"] = "ruled grid of uniform rows; pre-printed row-header column (chakra names)"

# ---------------- PAGES 5 & 6: feedback ----------------
for pg, secname in [(5, "FEEDBACK FROM PATIENTS"), (6, "FEEDBACK FROM PATIENTS (CONTINUED)")]:
    s = sec(pg, secname)
    t = s["fields"][0]
    t["static"] = False
    tbl = cap(pg, "HEALING NO.", 3)["container"] or cap(pg, "SCALE", 0)["container"]
    hy = sorted({hl[1] for hl in D[pg]["hlines"] if tbl[1] - 2 <= hl[1] <= tbl[3] + 2})
    vx = sorted({vl[0] for vl in D[pg]["vlines"]})
    col_bounds = [tbl[0]] + [round(x, 1) for x in vx] + [tbl[2]]
    t["grid"] = {"bbox": r2(tbl), "rowYs": [round(y, 1) for y in hy],
                 "colXs": [round(x, 1) for x in vx],
                 "note": "column bands: healingNo | healingDate | symptom1..4; rowYs are row boundaries"}
    t["arrayEvidence"] = "ruled grid of uniform rows"
    # symptom-name write-in bands: header cell between caption strip and table top border
    heads = []
    for i in range(4):
        ci = cap(pg, "SYMPTOM NAME", i)
        if ci:
            # column band containing this caption
            cx0 = max(b for b in col_bounds if b <= ci["labelBox"][0])
            cx1 = min(b for b in col_bounds if b > ci["labelBox"][0])
            heads.append({"index": i + 1, "labelBox": r2(ci["labelBox"]),
                          "valueBox": r2([cx0 + 4, tbl[3] + 1.5, cx1 - 4, ci["labelBox"][1] - 1])})
    heads.sort(key=lambda h: h["labelBox"][0])
    for i, h in enumerate(heads):
        h["index"] = i + 1
    t["symptomNameSlots"] = heads

    fb = [("PATIENT'S INTERIM FEEDBACK 1", cap(pg, "PATIENT'S INTERIM FEEDBACK", 0)),
          ("PATIENT'S INTERIM FEEDBACK 2", cap(pg, "PATIENT'S INTERIM FEEDBACK", 1)),
          ("PATIENT'S FINAL FEEDBACK", cap(pg, "PATIENT'S FINAL FEEDBACK", 0))]
    containers = {}
    for label, c in fb:
        try:
            f = field(s, label)
        except KeyError:
            continue
        if c and c["container"]:
            containers[label] = c["container"]
            # writable area: interior above the healing-no/date band (bottom ~26pt)
            box = c["container"]
            set_boxes(f, c, r2([box[0] + 5, box[1] + 28, box[2] - 5, box[3] - 11]))
    if pg == 5:
        # healing-no / date sub-fields: band below the caption border line at container bottom
        # caption order sorted by (-y, x): HEALING NO. -> 0=table hdr, 1=fb1, 2=fb2, 3=final
        #                                  DATE        -> 0=fb1, 1=fb2, 2=final
        sub = [("PATIENT'S INTERIM FEEDBACK 1", 1, 0), ("PATIENT'S INTERIM FEEDBACK 2", 2, 1),
               ("PATIENT'S FINAL FEEDBACK", 3, 2)]
        for base, hidx, didx in sub:
            cont = containers.get(base)
            if cont is None:
                continue
            ch = cap(5, "HEALING NO.", hidx)
            cd = cap(5, "DATE", didx)
            if ch:
                try:
                    f = field(s, f"{base} - HEALING NO.")
                    set_boxes(f, ch, r2([ch["labelBox"][0], cont[1] + 2, cd["labelBox"][0] - 4 if cd else ch["labelBox"][2] + 20, ch["labelBox"][1] - 2]))
                except KeyError:
                    pass
            if cd:
                try:
                    f = field(s, f"{base} - DATE")
                    set_boxes(f, cd, r2([cd["labelBox"][0], cont[1] + 2, cont[2] - 8 if cont[2] - cd["labelBox"][0] < 220 else cd["labelBox"][0] + 120, cd["labelBox"][1] - 2]))
                except KeyError:
                    pass

# ---------------- PAGE 7: protocol grid ----------------
s = sec(7, "HEALER'S UNDERSTANDING OF PROTOCOL USED")
t = s["fields"][0]
t["static"] = False
tbl = P[7]["containers"][0]
hy = sorted({hl[1] for hl in D[7]["hlines"]})
vx = sorted({vl[0] for vl in D[7]["vlines"]})
t["grid"] = {"bbox": r2(tbl), "rowYs": [round(y, 1) for y in hy],
             "colXs": [round(x, 1) for x in vx]}
t["arrayEvidence"] = "ruled grid of uniform rows, 3 captioned columns"

# ---------------- PAGE 8 ----------------
s = sec(8, "CLOSING SUMMARY")
c = cap(8, "HEALER'S ADDITIONAL COMMENTS")
set_boxes(field(s, "HEALER'S ADDITIONAL COMMENTS"), c, interior(c["container"]))
c = cap(8, "TOTAL NO. OF HEALINGS")
set_boxes(field(s, "TOTAL NO. OF HEALINGS"), c, interior(c["container"], cap_h=2.0))
c = cap(8, "IS THE HEALING STILL CONTINUED?")
f = field(s, "IS THE HEALING STILL CONTINUED?")
set_boxes(f, c, None)
f["options"] = [{"name": n, "box": curve_box_for_word(8, n, c["container"])} for n in ["YES", "NO"]]
f.pop("valueBox")
for label in ["LAST HEALING DATE", "LAST FEEDBACK DATE", "SIGNATURE OF THE HEALER"]:
    c = cap(8, label)
    if c:
        set_boxes(field(s, label), c, underline_after(8, c["labelBox"]))

# ---------------- write ----------------
gold["coordinateSystem"] = "PDF points, bottom-left origin, per page; box = [x0, y0, x1, y1]"
with open(GOLD, "w", encoding="utf-8") as fh:
    json.dump(gold, fh, indent=2, ensure_ascii=False)

print("WROTE", GOLD)
print("ISSUES:", len(issues))
for i in issues:
    print(" -", i)
