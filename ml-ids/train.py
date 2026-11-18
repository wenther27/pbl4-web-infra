import argparse
import json
import time
from pathlib import Path

import joblib
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import classification_report, confusion_matrix, roc_auc_score
from sklearn.pipeline import Pipeline

from dataset import prepare
from features import normalize

SAMPLES = [
    "/api/movies?page=1&size=12",
    "/api/movies?title=avengers&page=0&size=12",
    "/api/movies?title=l%E1%BA%ADt+m%E1%BA%B7t&page=0&size=20",
    "/api/movies?title=<script>alert(1)</script>",
    "/api/movies?title=1' OR '1'='1",
    "/api/movies?title=<svg/onfocus=alert(1) autofocus>",
    "/../../etc/passwd",
]


def build_model():
    return Pipeline([
        ("tfidf", TfidfVectorizer(analyzer="char", ngram_range=(3, 5), min_df=2,
                                  max_features=200_000, sublinear_tf=True)),
        ("clf", LogisticRegression(max_iter=2000, class_weight="balanced", C=4.0)),
    ])


def main():
    ap = argparse.ArgumentParser(description="Train ML-IDS tren CSIC 2010")
    ap.add_argument("--data", required=True, help="duong dan csic_database.csv")
    ap.add_argument("--extra-normal", help="request binh thuong cua CineHub (file cua export_normal.py)")
    ap.add_argument("--out", default="model")
    ap.add_argument("--threshold", type=float, default=0.5)
    ap.add_argument("--no-augment", action="store_true", help="tat tang cuong du lieu")
    args = ap.parse_args()

    train, test = prepare(args.data, args.extra_normal, augment=not args.no_augment)
    model = build_model()

    t0 = time.time()
    model.fit(train.text, train.label)
    train_sec = time.time() - t0

    proba = model.predict_proba(test.text)[:, 1]
    pred = (proba >= args.threshold).astype(int)
    t0 = time.time()
    model.predict_proba(test.text[:1000])
    ms_per_req = (time.time() - t0) / min(1000, len(test)) * 1000

    report = classification_report(test.label, pred, target_names=["normal", "attack"],
                                   digits=4, output_dict=True)
    cm = confusion_matrix(test.label, pred).tolist()
    auc = roc_auc_score(test.label, proba)

    print()
    print(classification_report(test.label, pred, target_names=["normal", "attack"], digits=4))
    print(f"Confusion matrix [[TN, FP], [FN, TP]]: {cm}")
    print(f"ROC-AUC: {auc:.4f}")
    print(f"Thoi gian train: {train_sec:.1f}s, du doan: {ms_per_req:.3f} ms/request")

    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    joblib.dump(model, out / "ids-model.joblib", compress=3)
    metrics = {
        "dataset": Path(args.data).name,
        "extraNormal": bool(args.extra_normal),
        "augment": not args.no_augment,
        "train": int(len(train)),
        "test": int(len(test)),
        "threshold": args.threshold,
        "accuracy": round(report["accuracy"], 4),
        "precision": round(report["attack"]["precision"], 4),
        "recall": round(report["attack"]["recall"], 4),
        "f1": round(report["attack"]["f1-score"], 4),
        "rocAuc": round(auc, 4),
        "confusionMatrix": cm,
        "trainSeconds": round(train_sec, 1),
        "msPerRequest": round(ms_per_req, 3),
        "trainedAt": time.strftime("%Y-%m-%d %H:%M:%S"),
    }
    (out / "metrics.json").write_text(json.dumps(metrics, indent=2), encoding="utf-8")
    print(f"\nDa luu {out / 'ids-model.joblib'} va {out / 'metrics.json'}")

    print("\nThu vai request:")
    for s in SAMPLES:
        p = model.predict_proba([normalize(s)])[0, 1]
        print(f"  {p:.3f}  {'ATTACK' if p >= args.threshold else 'normal'}  {s}")


if __name__ == "__main__":
    main()