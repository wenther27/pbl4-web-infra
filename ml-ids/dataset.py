import json
import random

import pandas as pd
from sklearn.model_selection import train_test_split

from features import normalize


def _pick(df, names):
    cols = {c.lower().strip(): c for c in df.columns}
    for n in names:
        if n in cols:
            return cols[n]
    return None


def _label(v):
    s = str(v).strip().lower()
    if s in ("1", "anomalous", "attack", "malicious", "bad"):
        return 1
    if s in ("0", "normal", "benign", "good", "valid"):
        return 0
    raise ValueError(f"Nhan la: {v!r}")


def load_csic(path):
    df = pd.read_csv(path, low_memory=False)
    url_col = _pick(df, ["url"])
    label_col = _pick(df, ["classification", "label", "class"])
    if url_col is None or label_col is None:
        raise SystemExit(f"File khong dung dinh dang CSIC. Cac cot hien co: {list(df.columns)}")
    content_col = _pick(df, ["content"])

    raws, texts = [], []
    for url, body in zip(df[url_col], df[content_col] if content_col else [""] * len(df)):
        url = "" if pd.isna(url) else str(url).strip()
        body = "" if pd.isna(body) else str(body).strip()
        path, _, query = url.partition("?")
        query = query.rsplit(" HTTP/", 1)[0]
        path = path.rsplit(" HTTP/", 1)[0]
        if body and not query:
            query = body
        raws.append(path + ("?" + query if query else ""))
        texts.append(normalize(path, query))
    return pd.DataFrame({
        "raw": raws,
        "text": texts,
        "label": [_label(v) for v in df[label_col]],
        "source": "csic",
    })


def load_extra_normal(path):
    raws, texts = [], []
    with open(path, encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            if line.startswith("{"):
                o = json.loads(line)
                p, q = o.get("path", ""), o.get("query", "") or ""
            else:
                p, _, q = line.partition("?")
            raws.append(p + ("?" + q if q else ""))
            texts.append(normalize(p, q))
    return pd.DataFrame({"raw": raws, "text": texts, "label": 0, "source": "cinehub"})


DEFAULT_PATHS = ["/api/movies", "/api/movies/0", "/api/showtimes", "/api/auth/login", "/api/bookings"]
DEFAULT_PARAMS = ["keyword", "page", "size", "id", "movieId", "genre", "date", "sort"]


def cinehub_vocab(df):
    cine = df[df.source == "cinehub"]
    paths = sorted({r.split("?", 1)[0] for r in cine.raw}) or DEFAULT_PATHS
    params = sorted({kv.split("=", 1)[0] for r in cine.raw if "?" in r
                     for kv in r.split("?", 1)[1].split("&") if "=" in kv}) or DEFAULT_PARAMS
    return paths, params


def swap_paths(df, paths, params=DEFAULT_PARAMS, seed=42):
    rng = random.Random(seed)
    rows = df[(df.source == "csic") & df.raw.str.contains("?", regex=False)].copy()
    raws = []
    for r in rows.raw:
        query = r.split("?", 1)[1]
        parts = []
        for kv in query.split("&"):
            value = kv.split("=", 1)[1] if "=" in kv else kv
            parts.append(rng.choice(params) + "=" + value)
        raws.append(rng.choice(paths) + "?" + "&".join(parts))
    rows["raw"] = raws
    rows["text"] = [normalize(*r.split("?", 1)) for r in raws]
    rows["source"] = "aug"
    return rows


def prepare(data, extra_normal=None, verbose=True, augment=True):
    df = load_csic(data)
    if verbose:
        print(f"CSIC: {len(df)} request ({(df.label == 0).sum()} normal, {(df.label == 1).sum()} tan cong)")
    if extra_normal:
        extra = load_extra_normal(extra_normal)
        if verbose:
            print(f"Them {len(extra)} request binh thuong cua CineHub")
        df = pd.concat([df, extra], ignore_index=True)

    df = df.drop_duplicates(subset=["text", "label"])
    conflict = df.groupby("text").label.nunique()
    df = df[~df.text.isin(conflict[conflict > 1].index)].reset_index(drop=True)
    if verbose:
        print(f"Sau khi bo trung lap / mau mau thuan: {len(df)} "
              f"({(df.label == 0).sum()} normal, {(df.label == 1).sum()} tan cong)")

    train, test = train_test_split(df, test_size=0.2, random_state=42, stratify=df.label)

    if augment:
        paths, params = cinehub_vocab(df)
        aug = swap_paths(train, paths, params)
        train = pd.concat([train, aug], ignore_index=True)
        if verbose:
            print(f"Tang cuong du lieu: them {len(aug)} mau CSIC doi sang {len(paths)} duong dan, "
                  f"{len(params)} ten tham so cua CineHub")
    return train, test
