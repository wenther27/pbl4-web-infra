import argparse
import json
import time
from pathlib import Path

import joblib
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import roc_auc_score
from sklearn.pipeline import Pipeline

from benign import generate
from dataset import load_httpparams, prepare, score_requests
from features import values

THRESHOLDS = (0.5, 0.7, 0.8, 0.9, 0.95)
SAMPLES = [
    "/api/movies?page=1&size=12",
    "/api/movies?title=avengers&page=0&size=12",
    "/api/movies?title=l%E1%BA%ADt+m%E1%BA%B7t&page=0&size=20",
    "/api/movies?title=Spider-Man: No Way Home",
    "/api/admin/security/suspicious-ips?minutes=60",
    "/api/movies?title=<script>alert(1)</script>",
    "/api/movies?title=1' OR '1'='1",
    "/api/movies?title=<svg/onfocus=alert(1) autofocus>",
    "/api/movies?title=x','0');waitfor delay '0:0:15';--",
    "/api/movies?title=../../../../etc/passwd",
]


def build_model():
    return Pipeline([
        ("tfidf", TfidfVectorizer(analyzer="char", ngram_range=(1, 5), min_df=2,
                                  max_features=300_000, sublinear_tf=True)),
        ("clf", LogisticRegression(max_iter=3000, class_weight="balanced", C=4.0)),
    ])


def rates(y, scores, t):
    tp = sum(1 for a, s in zip(y, scores) if a == 1 and s >= t)
    fp = sum(1 for a, s in zip(y, scores) if a == 0 and s >= t)
    pos = sum(y)
    neg = len(y) - pos
    return tp / pos if pos else 0.0, fp / neg if neg else 0.0, tp, fp


def main():
    ap = argparse.ArgumentParser(description="Train ML-IDS (cham diem tung gia tri tham so)")
    ap.add_argument("--data", required=True, help="csic_database.csv")
    ap.add_argument("--params", default="data/HttpParamsDataset", help="thu muc HttpParamsDataset")
    ap.add_argument("--extra-normal", help="request binh thuong cua CineHub (export_normal.py)")
    ap.add_argument("--benign", type=int, default=6000, help="so gia tri binh thuong tong hop bang Faker")
    ap.add_argument("--threshold", type=float, default=0.9)
    ap.add_argument("--out", default="model")
    args = ap.parse_args()

    train, test_req = prepare(args.data, args.params, args.extra_normal, args.benign)
    model = build_model()
    t0 = time.time()
    model.fit(train.x, train.y)
    train_sec = time.time() - t0

    csic_y = test_req.label.tolist()
    csic_s = score_requests(model, test_req.vals.tolist())
    hp = load_httpparams(args.params, "test")
    hp_s = model.predict_proba(hp.x)[:, 1].tolist()
    held = generate(3000, seed=7)
    held_s = model.predict_proba(held)[:, 1].tolist()

    t0 = time.time()
    model.predict_proba(hp.x[:2000])
    ms_per_value = (time.time() - t0) / min(2000, len(hp)) * 1000

    print(f"\nDanh gia tren du lieu KHONG dung de train:")
    print(f"  A = CSIC tap test ({len(csic_y)} request, cham theo request = diem cao nhat cua cac tham so)")
    print(f"  B = HttpParams tap test ({len(hp)} gia tri, sqlmap/XSS/fuzzdb)")
    print(f"  C = {len(held)} gia tri binh thuong tong hop moi (chi do bao nham)\n")
    print(f"{'Nguong':<8}{'A recall':>10}{'A bao nham':>12}{'B recall':>10}{'B bao nham':>12}{'C bao nham':>12}")
    table = {}
    for t in THRESHOLDS:
        ar, af, _, _ = rates(csic_y, csic_s, t)
        br, bf, _, _ = rates(hp.y.tolist(), hp_s, t)
        cf = sum(s >= t for s in held_s) / len(held_s)
        table[str(t)] = {"csicRecall": round(ar, 4), "csicFpr": round(af, 4), "paramsRecall": round(br, 4),
                         "paramsFpr": round(bf, 4), "benignFpr": round(cf, 4)}
        print(f"{t:<8}{ar:>10.3f}{af:>12.3f}{br:>10.3f}{bf:>12.3f}{cf:>12.3f}")
    print(f"\nROC-AUC: CSIC {roc_auc_score(csic_y, csic_s):.4f}, HttpParams {roc_auc_score(hp.y, hp_s):.4f}")
    print(f"Thoi gian train: {train_sec:.1f}s, cham diem: {ms_per_value:.3f} ms/gia tri")

    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    joblib.dump(model, out / "ids-model.joblib", compress=3)
    metrics = {
        "version": 2,
        "features": "gia tri tham so, char n-gram 1-5",
        "datasets": ["CSIC 2010", "HttpParamsDataset", "Faker"] + (["CineHub"] if args.extra_normal else []),
        "trainValues": int(len(train)),
        "threshold": args.threshold,
        "byThreshold": table,
        "trainSeconds": round(train_sec, 1),
        "msPerValue": round(ms_per_value, 3),
        "trainedAt": time.strftime("%Y-%m-%d %H:%M:%S"),
    }
    (out / "metrics.json").write_text(json.dumps(metrics, indent=2, ensure_ascii=False), encoding="utf-8")
    print(f"Da luu {out / 'ids-model.joblib'} va {out / 'metrics.json'}")

    print(f"\nThu vai request (nguong {args.threshold}):")
    for s in SAMPLES:
        p = score_requests(model, [values(s)])[0]
        print(f"  {p:.3f}  {'ATTACK' if p >= args.threshold else 'normal'}  {s}")


if __name__ == "__main__":
    main()
