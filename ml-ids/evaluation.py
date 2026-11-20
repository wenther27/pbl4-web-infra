import json
import time
from pathlib import Path

import pandas as pd
from sklearn.metrics import roc_auc_score

from benign import generate
from dataset import _label, score_requests
from features import clean

TYPE_NAMES = {"sqli": "SQL injection", "xss": "XSS", "cmdi": "Command injection",
              "path-traversal": "Path traversal", "norm": "Bình thường"}


def confusion(y, scores, threshold):
    tp = fp = tn = fn = 0
    for label, s in zip(y, scores):
        hit = s >= threshold
        if label == 1 and hit:
            tp += 1
        elif label == 1:
            fn += 1
        elif hit:
            fp += 1
        else:
            tn += 1
    total = tp + fp + tn + fn
    precision = tp / (tp + fp) if tp + fn and tp + fp else None
    recall = tp / (tp + fn) if tp + fn else None
    f1 = (2 * precision * recall / (precision + recall)
          if precision is not None and recall is not None and precision + recall else None)
    out = {"total": total, "tp": tp, "fp": fp, "tn": tn, "fn": fn,
           "accuracy": (tp + tn) / total if total else None,
           "precision": precision, "recall": recall, "f1": f1,
           "fpr": fp / (fp + tn) if fp + tn else None}
    try:
        out["auc"] = roc_auc_score(y, scores) if 0 < sum(y) < len(y) else None
    except ValueError:
        out["auc"] = None
    return {k: (round(v, 4) if isinstance(v, float) else v) for k, v in out.items()}


def build_testset(test_req, params_folder, benign_n=3000, benign_seed=7):
    csic = [{"v": list(vals), "y": int(label), "raw": raw[:200]}
            for raw, vals, label in zip(test_req.raw, test_req.vals, test_req.label)]
    df = pd.read_csv(Path(params_folder) / "payload_test.csv")
    params = []
    for p, label, kind in zip(df.payload.astype(str), df.label, df.get("attack_type", ["?"] * len(df))):
        v = clean(p)
        if v:
            params.append({"v": [v], "y": _label(label), "type": str(kind), "raw": p[:200]})
    benign = [{"v": [v], "y": 0, "raw": v[:200]} for v in generate(benign_n, seed=benign_seed)]
    return {
        "createdAt": time.strftime("%Y-%m-%d %H:%M:%S"),
        "note": "Khong dung de train: CSIC 20% tach theo request (random_state 42), "
                "HttpParams payload_test.csv, Faker seed 7",
        "sets": [
            {"key": "csic", "name": "CSIC 2010", "unit": "request", "items": csic},
            {"key": "params", "name": "HttpParamsDataset", "unit": "giá trị", "items": params},
            {"key": "benign", "name": "Input bình thường (Faker)", "unit": "giá trị", "items": benign},
        ],
    }


def evaluate(model, testset, threshold, examples=3):
    started = time.time()
    sets, all_y, all_s = [], [], []
    for ts in testset["sets"]:
        items = ts["items"]
        y = [it["y"] for it in items]
        scores = [float(s) for s in score_requests(model, [it["v"] for it in items])]
        all_y.extend(y)
        all_s.extend(scores)
        result = {"key": ts["key"], "name": ts["name"], "unit": ts["unit"], **confusion(y, scores, threshold)}

        if any("type" in it for it in items):
            by_type = {}
            for it, s in zip(items, scores):
                t = by_type.setdefault(it.get("type", "?"), {"total": 0, "correct": 0, "label": it["y"]})
                t["total"] += 1
                t["correct"] += int((s >= threshold) == (it["y"] == 1))
            result["byType"] = [{"type": k, "name": TYPE_NAMES.get(k, k), "attack": v["label"] == 1,
                                 "total": v["total"], "correct": v["correct"],
                                 "rate": round(v["correct"] / v["total"], 4)}
                                for k, v in sorted(by_type.items(), key=lambda kv: -kv[1]["total"])]

        missed = sorted((s, it["raw"]) for it, s in zip(items, scores) if it["y"] == 1 and s < threshold)
        false_alarm = sorted(((s, it["raw"]) for it, s in zip(items, scores) if it["y"] == 0 and s >= threshold),
                             reverse=True)
        result["missed"] = [{"score": round(s, 3), "text": r[:120]} for s, r in missed[-examples:][::-1]]
        result["falseAlarms"] = [{"score": round(s, 3), "text": r[:120]} for s, r in false_alarm[:examples]]
        sets.append(result)

    return {"threshold": threshold, "seconds": round(time.time() - started, 2),
            "evaluatedAt": time.strftime("%Y-%m-%d %H:%M:%S"),
            "overall": confusion(all_y, all_s, threshold), "sets": sets}


def save_testset(testset, out):
    path = Path(out) / "testset.json"
    path.write_text(json.dumps(testset, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    return path


def summary(result):
    return {"threshold": result["threshold"], "evaluatedAt": result["evaluatedAt"],
            "overall": result["overall"],
            "sets": [{k: v for k, v in s.items() if k not in ("missed", "falseAlarms")} for s in result["sets"]]}
