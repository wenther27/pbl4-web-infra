import argparse
import json
import os
import urllib.request

QUERY = """
{
  "size": 10000,
  "_source": ["path", "query", "clientIp", "status"],
  "sort": [ { "@timestamp": "desc" } ],
  "query": { "bool": {
    "filter": [
      { "term":  { "instance": "lb" } },
      { "term":  { "event": "request" } },
      { "range": { "@timestamp": { "gte": "now-%dm" } } },
      { "range": { "status": { "lt": 400 } } }
    ],
    "must_not": [
      { "term": { "clientIp": "203.0.113.0/24" } }
    ]
  }}
}
"""


def main():
    ap = argparse.ArgumentParser(description="Lay request binh thuong cua CineHub tu Elasticsearch")
    ap.add_argument("--minutes", type=int, default=60)
    ap.add_argument("--out", default="data/cinehub-normal.jsonl")
    ap.add_argument("--es", default=os.environ.get("ES_URL", "http://host.docker.internal:9200"))
    args = ap.parse_args()

    req = urllib.request.Request(
        f"{args.es}/weblogs-*/_search",
        data=(QUERY % args.minutes).encode("utf-8"),
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=30) as resp:
        hits = json.load(resp)["hits"]["hits"]

    seen = set()
    rows = []
    for h in hits:
        s = h["_source"]
        key = (s.get("path", ""), s.get("query", "") or "")
        if key[0] and key not in seen:
            seen.add(key)
            rows.append({"path": key[0], "query": key[1]})

    os.makedirs(os.path.dirname(args.out) or ".", exist_ok=True)
    with open(args.out, "w", encoding="utf-8") as f:
        for r in rows:
            f.write(json.dumps(r, ensure_ascii=False) + "\n")
    print(f"Doc {len(hits)} request trong {args.minutes} phut qua -> {len(rows)} request khac nhau -> {args.out}")


if __name__ == "__main__":
    main()
