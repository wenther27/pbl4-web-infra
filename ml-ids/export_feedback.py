import argparse
import json
import os
import urllib.error
import urllib.request

QUERY = '{"size": 10000, "_source": ["path", "query", "body", "ip", "by", "@timestamp"]}'


def main():
    ap = argparse.ArgumentParser(description="Lay cac request admin danh dau bao nham tu Elasticsearch")
    ap.add_argument("--out", default="data/feedback.jsonl")
    ap.add_argument("--es", default=os.environ.get("ES_URL", "http://host.docker.internal:9200"))
    args = ap.parse_args()

    req = urllib.request.Request(f"{args.es}/ids-feedback*/_search", data=QUERY.encode("utf-8"),
                                 headers={"Content-Type": "application/json"}, method="POST")
    with urllib.request.urlopen(req, timeout=30) as resp:
        hits = json.load(resp)["hits"]["hits"]

    seen = set()
    rows = []
    for h in hits:
        s = h["_source"]
        key = (s.get("path", ""), s.get("query", "") or "", s.get("body", "") or "")
        if key not in seen:
            seen.add(key)
            rows.append({"path": key[0], "query": key[1], "body": key[2]})

    os.makedirs(os.path.dirname(args.out) or ".", exist_ok=True)
    with open(args.out, "w", encoding="utf-8") as f:
        for r in rows:
            f.write(json.dumps(r, ensure_ascii=False) + "\n")
    print(f"{len(hits)} phan hoi bao nham -> {len(rows)} request khac nhau -> {args.out}")
    for r in rows[:10]:
        print(f"  {r['path']}?{r['query']} {('[body] ' + r['body']) if r['body'] else ''}")


if __name__ == "__main__":
    main()
