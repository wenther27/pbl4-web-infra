import argparse
import json
import re
from pathlib import Path

import joblib
import pandas as pd
import yaml

from benign import generate
from dataset import prepare, score_requests
from features import clean, decode


def load_r8(rules_file):
    cfg = yaml.safe_load(Path(rules_file).read_text(encoding="utf-8"))
    groups = cfg["rules"]["ATTACK_SIGNATURE"]["signatures"]
    return [re.compile(p, re.IGNORECASE) for patterns in groups.values() for p in patterns]


def r8_hit(patterns, raw):
    target = decode(raw)
    return int(any(p.search(target) for p in patterns))


def metrics(y, pred):
    tp = sum(1 for a, b in zip(y, pred) if a == 1 and b == 1)
    fp = sum(1 for a, b in zip(y, pred) if a == 0 and b == 1)
    fn = sum(1 for a, b in zip(y, pred) if a == 1 and b == 0)
    tn = sum(1 for a, b in zip(y, pred) if a == 0 and b == 0)
    precision = tp / (tp + fp) if tp + fp else 0
    recall = tp / (tp + fn) if tp + fn else 0
    f1 = 2 * precision * recall / (precision + recall) if precision + recall else 0
    fpr = fp / (fp + tn) if fp + tn else 0
    return precision, recall, f1, fpr, tp, fp, fn


def table(title, y, r8, ml):
    both = [int(a or b) for a, b in zip(r8, ml)]
    print(f"\n{title}")
    print(f"  {'Phuong phap':<18}{'Precision':>10}{'Recall':>9}{'F1':>8}{'Bao nham':>10}{'TP':>7}{'FP':>6}{'FN':>7}")
    for name, pred in (("R8 regex", r8), ("R9 ML-IDS", ml), ("R8 + R9", both)):
        p, r, f, fpr, tp, fp, fn = metrics(y, pred)
        print(f"  {name:<18}{p:>10.4f}{r:>9.4f}{f:>8.4f}{fpr:>10.4f}{tp:>7}{fp:>6}{fn:>7}")


def main():
    ap = argparse.ArgumentParser(description="So sanh R8 (regex) va R9 (ML-IDS) tren du lieu khong dung de train")
    ap.add_argument("--data", required=True)
    ap.add_argument("--params", default="data/HttpParamsDataset")
    ap.add_argument("--extra-normal")
    ap.add_argument("--model", default="model/ids-model.joblib")
    ap.add_argument("--rules", default="../anomaly-detection/src/main/resources/rules.yaml")
    ap.add_argument("--threshold", type=float, help="mac dinh: nguong trong model/metrics.json")
    args = ap.parse_args()

    _, test_req = prepare(args.data, args.params, args.extra_normal, benign=0, verbose=False)
    model = joblib.load(args.model)
    patterns = load_r8(args.rules)
    metrics_file = Path(args.model).with_name("metrics.json")
    saved = json.loads(metrics_file.read_text(encoding="utf-8")).get("threshold") if metrics_file.exists() else None
    t = args.threshold or saved or 0.9

    y = test_req.label.tolist()
    ml_s = score_requests(model, test_req.vals.tolist())
    table(f"A. CSIC 2010 - {len(y)} request (nguong ML {t})", y,
          [r8_hit(patterns, raw) for raw in test_req.raw], [int(s >= t) for s in ml_s])
    only_ml = [i for i in range(len(y)) if y[i] == 1 and ml_s[i] >= t and not r8_hit(patterns, test_req.raw.iloc[i])]
    print("  Vi du R8 bo sot, ML bat duoc:")
    for i in sorted(only_ml, key=lambda i: -ml_s[i])[:5]:
        print(f"    {ml_s[i]:.3f}  {decode(test_req.raw.iloc[i])[:110]!r}")

    hp = pd.read_csv(Path(args.params) / "payload_test.csv")
    hp = hp[hp.payload.astype(str).map(clean) != ""]
    hy = [int(l == "anom") for l in hp.label]
    hs = model.predict_proba([clean(p) for p in hp.payload.astype(str)])[:, 1]
    table(f"B. HttpParamsDataset - {len(hy)} gia tri", hy,
          [r8_hit(patterns, str(p)) for p in hp.payload], [int(s >= t) for s in hs])
    for kind in ("sqli", "xss", "path-traversal", "cmdi"):
        sub = hp.attack_type == kind
        if sub.any():
            ml_r = (hs[sub.values] >= t).mean()
            r8_r = sum(r8_hit(patterns, str(p)) for p in hp.payload[sub]) / sub.sum()
            print(f"    {kind:<16} R8 bat {r8_r:6.1%}   ML bat {ml_r:6.1%}")

    held = generate(3000, seed=7)
    hs2 = model.predict_proba(held)[:, 1]
    r8_fp = sum(r8_hit(patterns, v) for v in held) / len(held)
    print(f"\nC. {len(held)} gia tri binh thuong tong hop: R8 bao nham {r8_fp:.2%}, ML bao nham {(hs2 >= t).mean():.2%}")


if __name__ == "__main__":
    main()
