import argparse
import json
import os
import random
import time
import urllib.error
import urllib.request
from datetime import datetime, timezone
from pathlib import Path
from urllib.parse import quote

import pandas as pd

from benign import generate

KINDS = ("sqli", "xss", "path-traversal", "cmdi")
ALERTS_QUERY = """
{
  "size": 1000,
  "_source": ["rule", "target", "message", "@timestamp"],
  "query": { "bool": { "filter": [
    { "terms": { "rule": ["ATTACK_SIGNATURE", "ML_IDS"] } },
    { "range": { "@timestamp": { "gte": "%s" } } }
  ]}}
}
"""


def send(lb, path, ip):
    req = urllib.request.Request(lb + path, headers={"X-Forwarded-For": ip, "User-Agent": "pbl4-live-eval"})
    try:
        with urllib.request.urlopen(req, timeout=10) as res:
            return res.status
    except urllib.error.HTTPError as e:
        return e.code
    except Exception:
        return 0


def build_plan(params, n_attack_ips, n_normal_ips, per_ip, seed):
    rng = random.Random(seed)
    hp = pd.read_csv(Path(params) / "payload_test.csv")
    hp = hp[(hp.label == "anom") & (hp.payload.astype(str).str.len() <= 300)]
    plan = []
    per_kind = max(1, n_attack_ips // len(KINDS))
    n = 0
    for kind in KINDS:
        pool = hp[hp.attack_type == kind].payload.astype(str).tolist()
        for _ in range(per_kind):
            ip = f"203.0.113.{100 + n}"
            n += 1
            for p in rng.sample(pool, min(per_ip, len(pool))):
                plan.append(("attack", kind, ip, p))
    normal_values = generate(n_normal_ips * per_ip, seed=seed + 1000)
    for i in range(n_normal_ips):
        ip = f"198.51.100.{10 + i}"
        for v in normal_values[i * per_ip:(i + 1) * per_ip]:
            plan.append(("normal", "normal", ip, v))
    rng.shuffle(plan)
    return plan


def fetch_alerts(es, since):
    req = urllib.request.Request(f"{es}/alerts-*/_search", data=(ALERTS_QUERY % since).encode("utf-8"),
                                 headers={"Content-Type": "application/json"}, method="POST")
    with urllib.request.urlopen(req, timeout=30) as res:
        return [h["_source"] for h in json.load(res)["hits"]["hits"]]


def main():
    ap = argparse.ArgumentParser(description="Danh gia R8 va R9 tren he thong that (CHI chay voi LB local cua minh)")
    ap.add_argument("--lb", default=os.environ.get("LB_URL", "http://host.docker.internal:8080"))
    ap.add_argument("--es", default=os.environ.get("ES_URL", "http://host.docker.internal:9200"))
    ap.add_argument("--params", default="data/HttpParamsDataset")
    ap.add_argument("--attack-ips", type=int, default=40)
    ap.add_argument("--normal-ips", type=int, default=40)
    ap.add_argument("--per-ip", type=int, default=3)
    ap.add_argument("--rps", type=float, default=15)
    ap.add_argument("--wait", type=int, default=45, help="so giay cho detector xu ly sau khi gui xong")
    ap.add_argument("--seed", type=int, default=2026)
    args = ap.parse_args()

    if not any(h in args.lb for h in ("localhost", "127.0.0.1", "host.docker.internal")):
        raise SystemExit("Chi duoc chay voi LB local cua nhom (localhost / host.docker.internal).")

    plan = build_plan(args.params, args.attack_ips, args.normal_ips, args.per_ip, args.seed)
    started = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%S.000Z")
    print(f"Gui {len(plan)} request ({sum(p[0] == 'attack' for p in plan)} tan cong tu "
          f"{len({p[2] for p in plan if p[0] == 'attack'})} IP, {sum(p[0] == 'normal' for p in plan)} binh thuong tu "
          f"{len({p[2] for p in plan if p[0] == 'normal'})} IP) toi {args.lb} ...")
    statuses = {}
    gap = 1.0 / args.rps
    for i, (group, kind, ip, value) in enumerate(plan, 1):
        st = send(args.lb, "/api/movies?title=" + quote(value, safe="") + "&page=0&size=12", ip)
        statuses[st] = statuses.get(st, 0) + 1
        if i % 50 == 0:
            print(f"  da gui {i}/{len(plan)}")
        time.sleep(gap)
    print(f"Ma HTTP nhan duoc: {dict(sorted(statuses.items()))}")
    if statuses.get(0):
        print("  Canh bao: co request khong ket noi duoc LB (ma 0) - kiem tra he thong da chay chua.")

    print(f"Cho {args.wait} giay de log vao ES va detector cham diem ...")
    time.sleep(args.wait)
    alerts = fetch_alerts(args.es, started)
    by_rule = {"ATTACK_SIGNATURE": set(), "ML_IDS": set()}
    for a in alerts:
        by_rule.setdefault(a["rule"], set()).add(a["target"])

    ips = {}
    for group, kind, ip, _ in plan:
        ips[ip] = (group, kind)

    print("\nKet qua theo IP (1 IP = 1 ke tan cong / 1 nguoi dung):")
    print(f"  {'Nhom':<16}{'So IP':>7}{'R8 bat':>10}{'R9 bat':>10}{'R8 hoac R9':>13}")
    rows = [(k, [ip for ip, (g, kk) in ips.items() if kk == k]) for k in KINDS]
    rows.append(("TONG tan cong", [ip for ip, (g, _) in ips.items() if g == "attack"]))
    rows.append(("Binh thuong", [ip for ip, (g, _) in ips.items() if g == "normal"]))
    for name, group_ips in rows:
        r8 = sum(ip in by_rule["ATTACK_SIGNATURE"] for ip in group_ips)
        r9 = sum(ip in by_rule["ML_IDS"] for ip in group_ips)
        both = sum(ip in by_rule["ATTACK_SIGNATURE"] or ip in by_rule["ML_IDS"] for ip in group_ips)
        total = len(group_ips) or 1
        print(f"  {name:<16}{len(group_ips):>7}{r8:>5} ({r8 / total:4.0%}){r9:>4} ({r9 / total:4.0%}){both:>6} ({both / total:4.0%})")
    print("  (dong 'Binh thuong' la bao nham: cang thap cang tot)")

    false_ml = [a for a in alerts if a["rule"] == "ML_IDS" and ips.get(a["target"], ("", ""))[0] == "normal"]
    if false_ml:
        print("\nCanh bao R9 bao nham:")
        for a in false_ml[:5]:
            print("  " + a["message"][:160])
    print("\nXong. Nho bam Reset tren demo panel (hoac go chan IP) vi R8 tu dong chan cac IP tan cong 15 phut.")


if __name__ == "__main__":
    main()