import argparse
import json
import time
from pathlib import Path

import joblib
from sklearn.metrics import roc_auc_score

from benign import generate
from dataset import load_httpparams, prepare, score_requests
from evaluation import build_testset, evaluate, save_testset, summary
from features import values
from models import MODELS, build_model

THRESHOLDS = (0.5, 0.7, 0.8, 0.9, 0.95)
SAMPLES = [
    "/api/movies?page=1&size=12",
    "/api/movies?title=avengers&page=0&size=12",
    "/api/movies?title=l%E1%BA%ADt+m%E1%BA%B7t&page=0&size=20",
    "/api/movies?title=Spider-Man: No Way Home",
    "/api/movies?title=select",
    "/api/admin/security/suspicious-ips?minutes=60",
    "/api/movies?title=<script>alert(1)</script>",
    "/api/movies?title=1' OR '1'='1",
    "/api/movies?title=<svg/onfocus=alert(1) autofocus>",
    "/api/movies?title=x','0');waitfor delay '0:0:15';--",
    "/api/movies?title=1; exec xp_cmdshell 'dir'",
    "/api/movies?title=../../../../etc/passwd",
]


def calibrate(model, max_fpr):
    cal = sorted(model.predict_proba(generate(3000, seed=99))[:, 1])
    t = cal[min(int(len(cal) * (1 - max_fpr)), len(cal) - 1)]
    return round(float(t) + 0.0005, 3)


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
    ap.add_argument("--feedback", help="request admin danh dau bao nham (export_feedback.py)")
    ap.add_argument("--feedback-weight", type=int, default=10, help="moi mau bao nham duoc lap lai bao nhieu lan")
    ap.add_argument("--model", default="ens", choices=["lr", "nb", "rf", "ens"],
                    help="lr / nb / rf / ens (LR + RF), xem compare_models.py")
    ap.add_argument("--max-fpr", type=float, default=0.001,
                    help="nguong duoc chon tu dong de bao nham <= muc nay tren du lieu hieu chinh")
    ap.add_argument("--threshold", type=float, help="dat nguong bang tay (bo qua --max-fpr)")
    ap.add_argument("--out", default="model")
    args = ap.parse_args()

    train, test_req = prepare(args.data, args.params, args.extra_normal, args.benign,
                              feedback=args.feedback, feedback_weight=args.feedback_weight)
    model = build_model(args.model)
    print(f"\nModel: {MODELS[args.model]}")
    t0 = time.time()
    model.fit(train.x, train.y)
    train_sec = time.time() - t0
    threshold = args.threshold if args.threshold is not None else calibrate(model, args.max_fpr)
    thresholds = sorted(set(THRESHOLDS) | {threshold})
    print(f"Nguong: {threshold}" + ("" if args.threshold is not None else
                                    f" (tu dong: bao nham <= {args.max_fpr:.1%} tren 3000 gia tri hieu chinh)"))

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
    for t in thresholds:
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
        "version": 3,
        "model": MODELS[args.model],
        "features": "gia tri tham so, char n-gram 1-5",
        "datasets": ["CSIC 2010", "HttpParamsDataset", "Faker"] + (["CineHub"] if args.extra_normal else [])
                    + (["Bao nham (admin)"] if args.feedback else []),
        "trainValues": int(len(train)),
        "threshold": threshold,
        "byThreshold": table,
        "trainSeconds": round(train_sec, 1),
        "msPerValue": round(ms_per_value, 3),
        "trainedAt": time.strftime("%Y-%m-%d %H:%M:%S"),
    }
    testset = build_testset(test_req, args.params)
    save_testset(testset, out)
    metrics["evaluation"] = summary(evaluate(model, testset, threshold))
    ev = metrics["evaluation"]["overall"]
    print(f"Tap test {ev['total']} mau: accuracy {ev['accuracy']:.2%}, precision {ev['precision']:.2%}, "
          f"recall {ev['recall']:.2%}, F1 {ev['f1']:.3f}")
    (out / "metrics.json").write_text(json.dumps(metrics, indent=2, ensure_ascii=False), encoding="utf-8")
    print(f"Da luu {out / 'ids-model.joblib'} va {out / 'metrics.json'}")

    print(f"\nThu vai request (nguong {threshold}):")
    for s in SAMPLES:
        p = score_requests(model, [values(s)])[0]
        print(f"  {p:.3f}  {'ATTACK' if p >= threshold else 'normal'}  {s}")


if __name__ == "__main__":
    main()
