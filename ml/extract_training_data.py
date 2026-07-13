"""Turns the engine's raw detection candidates + reviewed gold into a labeled training table.

INPUT (per gold sample, e.g. gold/cl01/):
  1. expected.json           — reviewed ground truth (labelBox/valueBox/options/items/grid boxes)
  2. <sample>-debug.json      — the engine's raw merged DetectionCandidate list for that same
                                 source.pdf, produced by running the Java engine and saving its
                                 debug export (see "producing the debug json" below)

Only REVIEWED samples count (expected.json, not expected-draft.json) — an unreviewed draft is
the engine's own guess, so training on it would just teach the model to reproduce today's bugs.

OUTPUT: one CSV row per candidate, engineered features + a label derived by re-running the SAME
match rule GoldDatasetEvalTest.java uses (coverage>=0.5 OR IoU>=0.3) against every ground-truth
box of a category that candidate's type is allowed to claim. matched=1 means this candidate
corresponds to a real field the human confirmed; matched=0 means it's a spurious/duplicate/
half-formed candidate that never became (or wasn't part of) any real field.

Producing the debug json for a sample:
    gradlew.bat run --args="src\\test\\resources\\gold\\<sample>\\source.pdf"
    copy build\\detected-fields\\source-debug.json ml\\data\\raw\\<sample>-debug.json

Usage:
    python ml/extract_training_data.py
        (scans gold/*/ for samples with expected.json, looks for ml/data/raw/<sample>-debug.json,
         writes ml/data/training_candidates.csv)
"""
import argparse
import csv
import json
import re
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
DEFAULT_GOLD_ROOT = REPO_ROOT / "src" / "test" / "resources" / "gold"
DEFAULT_DEBUG_DIR = Path(__file__).resolve().parent / "data" / "raw"
DEFAULT_OUTPUT = Path(__file__).resolve().parent / "data" / "training_candidates.csv"

# Mirrors GoldDatasetEvalTest.Category — which CandidateType values may claim a
# ground-truth box of each kind. A candidate is checked against every category its
# own type appears in.
CATEGORY_CLAIMABLE_BY = {
    "LABEL": {"LABEL"},
    "VALUE": {"RECTANGLE", "UNDERLINE", "WHITESPACE", "IMAGE_PLACEHOLDER", "TABLE", "CHECKBOX"},
    "OPTION": None,  # None = every CandidateType (EnumSet.allOf in the Java source)
    "GRID": {"TABLE", "IMAGE_PLACEHOLDER", "RECTANGLE"},
}

FEATURE_COLUMNS = [
    "sample", "page", "detector_id", "candidate_type", "raw_confidence",
    "x0", "y0", "x1", "y1", "width", "height", "center_x", "center_y",
    "has_text", "text_word_count", "text_char_length", "text_uppercase_ratio",
    "text_ends_colon", "text_ends_question", "text_ends_paren", "text_ends_underscore",
    "text_has_digit", "font_size",
    "attr_members", "attr_checked", "attr_filled", "attr_aggregate",
    "attr_rows", "attr_cols", "attr_row", "attr_col", "attr_caption",
    # relational features — a candidate's OWN geometry says nothing about whether it fits a
    # repeating structure or has a plausible pairing partner nearby; these look at its siblings
    # on the same page (see relational_features()).
    "nearest_complementary_distance", "same_page_candidate_count",
    "row_band_count", "row_band_same_type_count",
    # diagnostic columns, not model inputs — kept so we can eyeball WHY a label landed
    # where it did before trusting the column as a training target
    "matched_gold", "matched_category", "matched_gold_name", "best_iou", "best_coverage",
]

# A LABEL's plausible pairing partner is one of these VALUE-ish types, and vice versa —
# mirrors CATEGORY_CLAIMABLE_BY["VALUE"] since that's the same "what can a value area be" set
# the eval harness (and FieldAssemblyResolver) already treats as equivalent.
VALUE_ISH_TYPES = CATEGORY_CLAIMABLE_BY["VALUE"]
NO_NEIGHBOR_SENTINEL = 9999.0
ROW_BAND_TOLERANCE_PTS = 6.0


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--gold-root", type=Path, default=DEFAULT_GOLD_ROOT)
    parser.add_argument("--debug-json-dir", type=Path, default=DEFAULT_DEBUG_DIR)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    args = parser.parse_args()

    samples = sorted(d for d in args.gold_root.iterdir() if d.is_dir() and (d / "expected.json").exists())
    if not samples:
        print(f"No reviewed gold samples (expected.json) found under {args.gold_root}", file=sys.stderr)
        sys.exit(1)

    rows = []
    for sample_dir in samples:
        debug_json_path = args.debug_json_dir / f"{sample_dir.name}-debug.json"
        if not debug_json_path.exists():
            print(f"skip {sample_dir.name}: no debug json at {debug_json_path} "
                  f"(run the engine on this sample and save its debug export there first)", file=sys.stderr)
            continue
        rows.extend(extract_sample(sample_dir.name, sample_dir / "expected.json", debug_json_path))

    args.output.parent.mkdir(parents=True, exist_ok=True)
    with open(args.output, "w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=FEATURE_COLUMNS)
        writer.writeheader()
        writer.writerows(rows)

    matched = sum(1 for r in rows if r["matched_gold"] == 1)
    print(f"wrote {len(rows)} candidate rows ({matched} matched / {len(rows) - matched} unmatched) "
          f"from {len(samples)} sample(s) -> {args.output}")


def extract_sample(sample_name, expected_json_path, debug_json_path):
    targets = load_targets(expected_json_path)
    candidates = json.loads(debug_json_path.read_text(encoding="utf-8"))["candidates"]

    by_page = {}
    for c in candidates:
        by_page.setdefault(c["page"], []).append(c)

    rows = []
    for c in candidates:
        page_siblings = by_page[c["page"]]
        rel = relational_features(c, page_siblings)
        rows.append(candidate_to_row(sample_name, c, targets, rel))
    return rows


def relational_features(c, page_siblings):
    """Features about a candidate's SIBLINGS on the same page — its own box says nothing about
    whether a plausible pairing partner sits nearby, or whether it fits a repeating row pattern;
    both are exactly the kind of context a fixed per-candidate threshold can't see but a model
    with these columns can."""
    box = c["box"]
    cx, cy = (box["x0"] + box["x1"]) / 2.0, (box["y0"] + box["y1"]) / 2.0
    ctype = c["type"]

    if ctype == "LABEL":
        complementary_types = VALUE_ISH_TYPES
    elif ctype in VALUE_ISH_TYPES:
        complementary_types = {"LABEL"}
    else:
        complementary_types = None

    nearest_complementary = NO_NEIGHBOR_SENTINEL
    row_band_count = 0
    row_band_same_type_count = 0
    for other in page_siblings:
        if other is c:
            continue
        ob = other["box"]
        ocx, ocy = (ob["x0"] + ob["x1"]) / 2.0, (ob["y0"] + ob["y1"]) / 2.0

        if complementary_types is not None and other["type"] in complementary_types:
            dist = ((cx - ocx) ** 2 + (cy - ocy) ** 2) ** 0.5
            nearest_complementary = min(nearest_complementary, dist)

        if abs(ocy - cy) <= ROW_BAND_TOLERANCE_PTS:
            row_band_count += 1
            if other["type"] == ctype:
                row_band_same_type_count += 1

    return {
        "nearest_complementary_distance": round(nearest_complementary, 2),
        "same_page_candidate_count": len(page_siblings) - 1,
        "row_band_count": row_band_count,
        "row_band_same_type_count": row_band_same_type_count,
    }


def candidate_to_row(sample_name, c, targets, relational):
    box = c["box"]
    x0, y0, x1, y1 = box["x0"], box["y0"], box["x1"], box["y1"]
    attrs = c.get("attributes") or {}
    text = attrs.get("text")

    row = {
        "sample": sample_name,
        "page": c["page"],
        "detector_id": c["detectorId"],
        "candidate_type": c["type"],
        "raw_confidence": c["rawConfidence"],
        "x0": x0, "y0": y0, "x1": x1, "y1": y1,
        "width": x1 - x0, "height": y1 - y0,
        "center_x": (x0 + x1) / 2.0, "center_y": (y0 + y1) / 2.0,
        "has_text": int(text is not None),
        "text_word_count": len(text.split()) if text else 0,
        "text_char_length": len(text) if text else 0,
        "text_uppercase_ratio": uppercase_ratio(text),
        "text_ends_colon": int(bool(text) and text.endswith(":")),
        "text_ends_question": int(bool(text) and text.endswith("?")),
        "text_ends_paren": int(bool(text) and text.endswith(")") and "(" in text),
        "text_ends_underscore": int(bool(text) and text.endswith("__")),
        "text_has_digit": int(bool(text) and any(ch.isdigit() for ch in text)),
        "font_size": attrs.get("fontSize", ""),
        "attr_members": attrs.get("members", ""),
        "attr_checked": int(bool(attrs.get("checked", False))),
        "attr_filled": int(bool(attrs.get("filled", False))),
        "attr_aggregate": int(bool(attrs.get("aggregate", False))),
        "attr_rows": attrs.get("rows", ""),
        "attr_cols": attrs.get("cols", ""),
        "attr_row": attrs.get("row", ""),
        "attr_col": attrs.get("col", ""),
        "attr_caption": attrs.get("caption", ""),
    }
    row.update(relational)

    best = best_match(c, targets)
    row["matched_gold"] = int(best is not None)
    row["matched_category"] = best.category if best else ""
    row["matched_gold_name"] = best.name if best else ""
    row["best_iou"] = round(best.iou, 4) if best else 0.0
    row["best_coverage"] = round(best.coverage, 4) if best else 0.0
    return row


def uppercase_ratio(text):
    if not text:
        return 0.0
    letters = [ch for ch in text if ch.isalpha()]
    if not letters:
        return 0.0
    return round(sum(1 for ch in letters if ch.isupper()) / len(letters), 4)


class Target:
    __slots__ = ("page", "x0", "y0", "x1", "y1", "category", "name")

    def __init__(self, page, box, category, name):
        self.page = page
        self.x0, self.y0, self.x1, self.y1 = box
        self.category = category
        self.name = name


class Match:
    __slots__ = ("category", "name", "iou", "coverage")

    def __init__(self, category, name, iou, coverage):
        self.category = category
        self.name = name
        self.iou = iou
        self.coverage = coverage


def best_match(candidate, targets):
    """Mirrors GoldDatasetEvalTest.hits(): coverage(target)>=0.5 OR IoU>=0.3, restricted to
    categories the candidate's own type is allowed to claim. Returns the highest-IoU match."""
    ctype = candidate["type"]
    page = candidate["page"]
    box = candidate["box"]
    best = None
    for t in targets:
        if t.page != page:
            continue
        claimable = CATEGORY_CLAIMABLE_BY[t.category]
        if claimable is not None and ctype not in claimable:
            continue
        iou, coverage = iou_and_coverage(box, t)
        if coverage >= 0.5 or iou >= 0.3:
            if best is None or iou > best.iou:
                best = Match(t.category, t.name, iou, coverage)
    return best


def iou_and_coverage(c, t):
    ix = max(0.0, min(c["x1"], t.x1) - max(c["x0"], t.x0))
    iy = max(0.0, min(c["y1"], t.y1) - max(c["y0"], t.y0))
    inter = ix * iy
    if inter <= 0:
        return 0.0, 0.0
    area_t = (t.x1 - t.x0) * (t.y1 - t.y0)
    area_c = (c["x1"] - c["x0"]) * (c["y1"] - c["y0"])
    coverage = inter / area_t if area_t > 0 else 0.0
    iou = inter / (area_t + area_c - inter)
    return iou, coverage


def load_targets(expected_json_path):
    root = json.loads(expected_json_path.read_text(encoding="utf-8"))
    targets = []
    for page in root.get("pages", []):
        page_no = page.get("page")
        for section in page.get("sections", []):
            for f in section.get("fields", []):
                name = f.get("label") or ""
                add_box(targets, page_no, f.get("labelBox"), "LABEL", name)
                add_box(targets, page_no, f.get("valueBox"), "VALUE", name)
                for o in f.get("options", []):
                    add_box(targets, page_no, o.get("box"), "OPTION", f"{name} [{o.get('name')}]")
                for it in f.get("items", []):
                    item_name = f"{name} #{it.get('index')}"
                    add_box(targets, page_no, it.get("labelBox"), "LABEL", item_name)
                    add_box(targets, page_no, it.get("valueBox"), "VALUE", item_name)
                grid = f.get("grid")
                if grid:
                    add_box(targets, page_no, grid.get("bbox"), "GRID", name)
                for slot in f.get("symptomNameSlots", []):
                    slot_name = f"{name} symptomName#{slot.get('index')}"
                    add_box(targets, page_no, slot.get("labelBox"), "LABEL", slot_name)
                    add_box(targets, page_no, slot.get("valueBox"), "VALUE", slot_name)
    return targets


def add_box(targets, page_no, box, category, name):
    if not box or len(box) != 4:
        return
    targets.append(Target(page_no, box, category, name))


if __name__ == "__main__":
    main()
