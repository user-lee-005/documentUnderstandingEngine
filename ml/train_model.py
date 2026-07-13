"""Trains the candidate keep/discard classifier on ml/data/training_candidates.csv.

Baseline model: Random Forest (see ml/README.md for why — safer than boosting on a small,
noisy-label dataset, and doesn't need GPU or feature scaling). Not a neural net, not an LLM.

Validation strategy:
  - >=2 samples in the CSV: leave-one-sample-out CV (train on all-but-one document, test on the
    held-out one). This is the only honest check — a random row split would leak one form's
    layout patterns into its own test set and overstate accuracy.
  - 1 sample only: falls back to stratified K-fold WITHIN that sample and prints a loud warning
    that this is not a real generalization test. Do not trust this number for a go/no-go call —
    it only confirms the code runs end-to-end.

Output: prints per-class precision/recall/F1 and feature importances, saves the model trained on
ALL rows to ml/models/candidate_filter.joblib, and exports the same model to
ml/models/candidate_filter.onnx for eventual Java-side inference (skipped with a warning if
skl2onnx isn't installed — the joblib file is still written either way).

Usage:
    python ml/train_model.py
        (reads ml/data/training_candidates.csv, writes ml/models/*)
"""
import argparse
import sys
from pathlib import Path

import numpy as np

try:
    import pandas as pd
except ImportError:
    print("Missing dependency. Run: pip install -r ml/requirements.txt", file=sys.stderr)
    raise

from sklearn.ensemble import RandomForestClassifier
from sklearn.metrics import classification_report
from sklearn.model_selection import StratifiedKFold

DEFAULT_INPUT = Path(__file__).resolve().parent / "data" / "training_candidates.csv"
DEFAULT_MODEL_DIR = Path(__file__).resolve().parent / "models"

# candidate_type and detector_id are categorical text — one-hot encoded below. Everything else
# here is already numeric (see extract_training_data.py's FEATURE_COLUMNS for what each means).
NUMERIC_FEATURES = [
    "raw_confidence", "x0", "y0", "x1", "y1", "width", "height", "center_x", "center_y",
    "has_text", "text_word_count", "text_char_length", "text_uppercase_ratio",
    "text_ends_colon", "text_ends_question", "text_ends_paren", "text_ends_underscore",
    "text_has_digit", "font_size",
    "attr_members", "attr_checked", "attr_filled", "attr_aggregate",
    "attr_rows", "attr_cols", "attr_row", "attr_col",
    "nearest_complementary_distance", "same_page_candidate_count",
    "row_band_count", "row_band_same_type_count",
]
CATEGORICAL_FEATURES = ["candidate_type", "detector_id"]
LABEL_COLUMN = "matched_gold"


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--input", type=Path, default=DEFAULT_INPUT)
    parser.add_argument("--model-dir", type=Path, default=DEFAULT_MODEL_DIR)
    args = parser.parse_args()

    if not args.input.exists():
        print(f"{args.input} not found — run ml/extract_training_data.py first", file=sys.stderr)
        sys.exit(1)

    df = pd.read_csv(args.input)
    print(f"loaded {len(df)} rows from {df['sample'].nunique()} sample(s): {sorted(df['sample'].unique())}")

    X, feature_names = build_features(df)
    y = df[LABEL_COLUMN].values

    validate(df, X, y, feature_names)

    print("\ntraining final model on ALL rows...")
    model = new_model()
    model.fit(X, y)
    report_feature_importance(model, feature_names)

    args.model_dir.mkdir(parents=True, exist_ok=True)
    save_model(model, feature_names, args.model_dir)


def new_model():
    # class_weight="balanced" matters here — the dataset is roughly 80/20 discard/keep (see
    # ml/README.md), so an unweighted model could just learn to always predict "discard" and
    # still score ~80% accuracy while being useless. Balancing reweights each class by its
    # inverse frequency so the minority (real fields) isn't ignored.
    return RandomForestClassifier(
        n_estimators=300,
        max_depth=8,
        min_samples_leaf=3,
        class_weight="balanced",
        random_state=0,
        n_jobs=-1,
    )


def build_features(df):
    numeric = df[NUMERIC_FEATURES].fillna(0)
    categorical = pd.get_dummies(df[CATEGORICAL_FEATURES], prefix=CATEGORICAL_FEATURES)
    combined = pd.concat([numeric, categorical], axis=1)
    return combined.values.astype(float), list(combined.columns)


def validate(df, X, y, feature_names):
    samples = sorted(df["sample"].unique())
    print(f"\n{'=' * 70}\nvalidation\n{'=' * 70}")

    if len(samples) < 2:
        print(f"WARNING: only 1 sample ({samples[0]}) in the dataset -- leave-one-document-out\n"
              f"cross-validation needs at least 2. Falling back to stratified K-fold WITHIN this\n"
              f"one document instead. That number below is NOT a real generalization estimate --\n"
              f"it shares this form's own layout between train and test folds, so it will look\n"
              f"better than the model will actually perform on a new, unseen form. Treat it only\n"
              f"as a smoke test that the pipeline runs end-to-end, and add more reviewed gold\n"
              f"samples before trusting any accuracy number from this script.\n")
        cv = StratifiedKFold(n_splits=5, shuffle=True, random_state=0)
        all_true, all_pred = [], []
        for train_idx, test_idx in cv.split(X, y):
            model = new_model()
            model.fit(X[train_idx], y[train_idx])
            all_true.extend(y[test_idx])
            all_pred.extend(model.predict(X[test_idx]))
        print(classification_report(all_true, all_pred, target_names=["discard", "keep"], zero_division=0))
        return

    print("leave-one-document-out cross-validation:")
    all_true, all_pred = [], []
    for held_out in samples:
        train_mask = (df["sample"] != held_out).values
        test_mask = (df["sample"] == held_out).values
        model = new_model()
        model.fit(X[train_mask], y[train_mask])
        pred = model.predict(X[test_mask])
        all_true.extend(y[test_mask])
        all_pred.extend(pred)
        print(f"\n--- held out: {held_out} ({test_mask.sum()} candidates) ---")
        print(classification_report(y[test_mask], pred, target_names=["discard", "keep"], zero_division=0))

    print(f"\n--- overall (all folds combined) ---")
    print(classification_report(all_true, all_pred, target_names=["discard", "keep"], zero_division=0))


def report_feature_importance(model, feature_names):
    importances = sorted(zip(feature_names, model.feature_importances_), key=lambda p: -p[1])
    print("\ntop 15 features by importance:")
    for name, importance in importances[:15]:
        print(f"  {name:35s} {importance:.4f}")


def save_model(model, feature_names, model_dir):
    import joblib
    joblib_path = model_dir / "candidate_filter.joblib"
    joblib.dump({"model": model, "feature_names": feature_names}, joblib_path)
    print(f"\nsaved {joblib_path}")

    try:
        from skl2onnx import convert_sklearn
        from skl2onnx.common.data_types import FloatTensorType
    except ImportError:
        print("skl2onnx not installed -- skipping ONNX export (joblib model above is still valid).\n"
              "Install with: pip install -r ml/requirements.txt")
        return

    onnx_model = convert_sklearn(
        model, initial_types=[("input", FloatTensorType([None, len(feature_names)]))],
        options={id(model): {"zipmap": False}},
    )
    onnx_path = model_dir / "candidate_filter.onnx"
    onnx_path.write_bytes(onnx_model.SerializeToString())
    print(f"saved {onnx_path} (for Java-side inference via onnxruntime -- not wired into the "
          f"pipeline yet, see ml/README.md)")


if __name__ == "__main__":
    main()
