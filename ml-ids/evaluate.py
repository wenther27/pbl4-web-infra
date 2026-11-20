import argparse
import json
from pathlib import Path

import joblib
from sklearn.model_selection import train_test_split

from dataset import load_csic
from evaluation import build_testset, evaluate, save_testset, summary


def pct(v):
    return "   -   " if v is None else f"{v * 100:6.2f}%"


def print_result(r):
    print(f"Nguong {r['threshold']} - cham {r['overall']['total']} mau trong {r['seconds']} giay\n")
    print(f"{'Tap test':<28}{'So mau':>8}{'Accuracy':>10}{'Precision':>11}{'Recall':>9}{'F1':>8}{'AUC':>8}"
          f"{'TP':>7}{'FP':>6}{'TN':>7}{'FN':>6}")
    for s in r["sets"] + [{"name": "TONG", **r["overall"]}]:
        f1 = "   -" if s["f1"] is None else f"{s['f1']:.3f}"
        auc = "   -" if s["auc"] is None else f"{s['auc']:.4f}"
        print(f"{s['name']:<28}{s['total']:>8}{pct(s['accuracy']):>10}{pct(s['precision']):>11}"
              f"{pct(s['recall']):>9}{f1:>8}{auc:>8}{s['tp']:>7}{s['fp']:>6}{s['tn']:>7}{s['fn']:>6}")
    for s in r["sets"]:
        for t in s.get("byType", []):
            print(f"  {s['name']} - {t['name']:<20} dung {t['correct']}/{t['total']} ({t['rate']:.2%})")


def main():
    ap = argparse.ArgumentParser(description="Tao tap test (khong dung de train) va do do chinh xac cua model hien tai")
    ap.add_argument("--data", default="data/csic_database.csv")
    ap.add_argument("--params", default="data/HttpParamsDataset")
    ap.add_argument("--model-dir", default="model")
    ap.add_argument("--threshold", type=float, help="mac dinh lay nguong trong metrics.json")
    args = ap.parse_args()

    out = Path(args.model_dir)
    metrics_file = out / "metrics.json"
    metrics = json.loads(metrics_file.read_text(encoding="utf-8"))
    threshold = args.threshold if args.threshold is not None else metrics["threshold"]

    req = load_csic(args.data)
    _, test_req = train_test_split(req, test_size=0.2, random_state=42, stratify=req.label)
    testset = build_testset(test_req, args.params)
    path = save_testset(testset, out)
    print(f"Da luu tap test {path} ({sum(len(s['items']) for s in testset['sets'])} mau)\n")

    model = joblib.load(out / "ids-model.joblib")
    result = evaluate(model, testset, threshold)
    print_result(result)

    metrics["evaluation"] = summary(result)
    metrics_file.write_text(json.dumps(metrics, indent=2, ensure_ascii=False), encoding="utf-8")
    print(f"\nDa ghi ket qua vao {metrics_file} (muc 'evaluation')")


if __name__ == "__main__":
    main()
