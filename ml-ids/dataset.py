import json
from pathlib import Path

import pandas as pd
from sklearn.model_selection import train_test_split

from benign import generate
from features import clean, split_url, values


def _pick(df, names):
    cols = {c.lower().strip(): c for c in df.columns}
    for n in names:
        if n in cols:
            return cols[n]
    return None


def _label(v):
    s = str(v).strip().lower()
    if s in ("1", "anomalous", "attack", "anom"):
        return 1
    if s in ("0", "normal", "norm"):
        return 0
    raise ValueError(f"Nhan la: {v!r}")


def load_csic(path):
    df = pd.read_csv(path, low_memory=False)
    url_col = _pick(df, ["url"])
    label_col = _pick(df, ["classification", "label", "class"])
    if url_col is None or label_col is None:
        raise SystemExit(f"File khong dung dinh dang CSIC. Cac cot hien co: {list(df.columns)}")
    content_col = _pick(df, ["content"])

    rows = []
    bodies = df[content_col] if content_col else [""] * len(df)
    for url, body, label in zip(df[url_col], bodies, df[label_col]):
        url = "" if pd.isna(url) else str(url)
        body = "" if pd.isna(body) else str(body).strip()
        path, query = split_url(url)
        if body and not query:
            query = body
        vals = tuple(values(path, query))
        if vals:
            rows.append((path + "?" + query, vals, _label(label)))
    req = pd.DataFrame(rows, columns=["raw", "vals", "label"])
    req = req.drop_duplicates(subset=["vals", "label"])
    conflict = req.groupby("vals").label.nunique()
    return req[~req.vals.isin(conflict[conflict > 1].index)].reset_index(drop=True)


def load_httpparams(folder, split):
    df = pd.read_csv(Path(folder) / f"payload_{split}.csv")
    rows = [(clean(p), _label(l)) for p, l in zip(df.payload.astype(str), df.label)]
    return pd.DataFrame([r for r in rows if r[0]], columns=["x", "y"])


def load_extra_normal(path):
    out = []
    with open(path, encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            if line.startswith("{"):
                o = json.loads(line)
                out.extend(values(o.get("path", ""), o.get("query", "") or ""))
                out.extend(values("", o.get("body", "") or ""))
            else:
                out.extend(values(line))
    return out


def _strip_prefix(v, normal):
    for k in range(min(len(v), 40), 2, -1):
        if v[:k] in normal:
            return v[k:].strip()
    return v


def prepare(csic, params, extra_normal=None, benign=6000, verbose=True, feedback=None, feedback_weight=10):
    req = load_csic(csic)
    train_req, test_req = train_test_split(req, test_size=0.2, random_state=42, stratify=req.label)

    normal = {v for vals in train_req[train_req.label == 0].vals for v in vals}
    x, y, parts = [], [], {}

    def add(name, items, label):
        parts[name] = len(items)
        x.extend(items)
        y.extend([label] * len(items))

    add("CSIC binh thuong", [v for vals in train_req[train_req.label == 0].vals for v in vals], 0)
    attacks = []
    for vals in train_req[train_req.label == 1].vals:
        for v in vals:
            if v not in normal:
                v = _strip_prefix(v, normal)
                if clean(v):
                    attacks.append(v)
    add("CSIC tan cong (da tach payload)", attacks, 1)

    hp = load_httpparams(params, "train")
    add("HttpParams binh thuong", hp[hp.y == 0].x.tolist(), 0)
    add("HttpParams tan cong", hp[hp.y == 1].x.tolist(), 1)
    if extra_normal:
        add("CineHub binh thuong", load_extra_normal(extra_normal), 0)
    if benign:
        add("Gia tri binh thuong tong hop (Faker)", generate(benign), 0)
    if feedback:
        fb = load_extra_normal(feedback)
        add(f"Bao nham do admin danh dau (x{feedback_weight})", fb * feedback_weight, 0)

    train = pd.DataFrame({"x": x, "y": y})
    conflict = train.groupby("x").y.nunique()
    train = train[~train.x.isin(conflict[conflict > 1].index)]
    if verbose:
        print("Du lieu train (moi mau = 1 gia tri tham so):")
        for k, n in parts.items():
            print(f"  {k:<38}{n:>7}")
        print(f"  => {len(train)} mau ({(train.y == 0).sum()} binh thuong, {(train.y == 1).sum()} tan cong)")
    return train, test_req


def score_requests(model, vals_list):
    uniq = sorted({v for vals in vals_list for v in vals})
    scores = dict(zip(uniq, model.predict_proba(uniq)[:, 1])) if uniq else {}
    return [max((scores[v] for v in vals), default=0.0) for vals in vals_list]
