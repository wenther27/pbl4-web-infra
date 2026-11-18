import argparse
import re
from pathlib import Path
from urllib.parse import unquote_plus

import joblib
import yaml

from dataset import cinehub_vocab, prepare, swap_paths


def load_r8(rules_file):
    cfg = yaml.safe_load(Path(rules_file).read_text(encoding="utf-8"))
    groups = cfg["rules"]["ATTACK_SIGNATURE"]["signatures"]
    return [re.compile(p, re.IGNORECASE) for patterns in groups.values() for p in patterns]


def decode(s):
    for _ in range(2):
        d = unquote_plus(s)
        if d == s:
            break
        s = d
    return s


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
    return precision, recall, f1, fpr, tp, fp, fn, tn


def main():
    ap = argparse.ArgumentParser(description="So sanh R8 (regex) va ML-IDS tren cung tap test")
    ap.add_argument("--data", required=True)
    ap.add_argument("--extra-normal")
    ap.add_argument("--model", default="model/ids-model.joblib")
    ap.add_argument("--rules", default="../anomaly-detection/src/main/resources/rules.yaml")
    ap.add_argument("--threshold", type=float, default=0.9)
    args = ap.parse_args()

    _, test = prepare(args.data, args.extra_normal, verbose=False)
    model = joblib.load(args.model)
    patterns = load_r8(args.rules)

    y = test.label.tolist()
    proba = model.predict_proba(test.text)[:, 1]
    ml = [int(p >= args.threshold) for p in proba]
    r8 = [r8_hit(patterns, raw) for raw in test.raw]
    both = [int(a or b) for a, b in zip(ml, r8)]

    print(f"Tap test: {len(test)} request ({y.count(0)} normal, {y.count(1)} tan cong), "
          f"nguong ML = {args.threshold}\n")
    print(f"{'Phuong phap':<22}{'Precision':>10}{'Recall':>10}{'F1':>10}{'FP rate':>10}"
          f"{'TP':>8}{'FP':>8}{'FN':>8}")
    for name, pred in (("R8 regex", r8), ("R9 ML-IDS", ml), ("R8 + R9 ket hop", both)):
        p, r, f, fpr, tp, fp, fn, _ = metrics(y, pred)
        print(f"{name:<22}{p:>10.4f}{r:>10.4f}{f:>10.4f}{fpr:>10.4f}{tp:>8}{fp:>8}{fn:>8}")

    attacks = [i for i, v in enumerate(y) if v == 1]
    only_ml = [i for i in attacks if ml[i] and not r8[i]]
    only_r8 = [i for i in attacks if r8[i] and not ml[i]]
    print(f"\nTrong {len(attacks)} request tan cong: ca hai cung bat "
          f"{sum(1 for i in attacks if ml[i] and r8[i])}, chi ML bat {len(only_ml)}, "
          f"chi R8 bat {len(only_r8)}, ca hai bo sot {sum(1 for i in attacks if not ml[i] and not r8[i])}")

    print("\nVi du tan cong R8 bo sot nhung ML bat duoc (dung de demo):")
    for i in sorted(only_ml, key=lambda i: -proba[i])[:10]:
        print(f"  {proba[i]:.3f}  {test.raw.iloc[i][:140]}")

    paths, params = cinehub_vocab(test)
    moved = swap_paths(test, paths, params, seed=7)
    if len(moved):
        my = moved.label.tolist()
        mp = [int(p >= args.threshold) for p in model.predict_proba(moved.text)[:, 1]]
        mr = [r8_hit(patterns, raw) for raw in moved.raw]
        print(f"\nTap test chuyen sang duong dan CineHub ({len(moved)} request, vd: {moved.raw.iloc[0][:70]}):")
        for name, pred in (("R8 regex", mr), ("R9 ML-IDS", mp)):
            p, r, f, fpr, *_ = metrics(my, pred)
            print(f"  {name:<12} precision {p:.4f}  recall {r:.4f}  F1 {f:.4f}  FP rate {fpr:.4f}")

    print("\nChon nguong (tap test goc | tap test chuyen sang CineHub):")
    mproba = model.predict_proba(moved.text)[:, 1] if len(moved) else []
    for t in (0.5, 0.6, 0.7, 0.8, 0.9, 0.95):
        p, r, _, fpr, *_ = metrics(y, [int(v >= t) for v in proba])
        line = f"  nguong {t:.2f}: precision {p:.4f}  recall {r:.4f}  FP rate {fpr:.4f}"
        if len(moved):
            p2, r2, _, fpr2, *_ = metrics(moved.label.tolist(), [int(v >= t) for v in mproba])
            line += f"  |  precision {p2:.4f}  recall {r2:.4f}  FP rate {fpr2:.4f}"
        print(line)

    cine = test[test.source == "cinehub"]
    if len(cine):
        idx = [test.index.get_loc(k) for k in cine.index]
        fp_ml = sum(ml[i] for i in idx)
        fp_r8 = sum(r8[i] for i in idx)
        print(f"\nRieng {len(cine)} request binh thuong cua CineHub trong tap test: "
              f"ML bao nham {fp_ml} ({fp_ml / len(cine):.2%}), R8 bao nham {fp_r8} ({fp_r8 / len(cine):.2%})")


if __name__ == "__main__":
    main()