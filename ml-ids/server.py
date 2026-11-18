import json
import os
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

import joblib

from dataset import score_requests
from features import values

MODEL_DIR = Path(os.environ.get("MODEL_DIR", "model"))
PORT = int(os.environ.get("PORT", "8000"))
MAX_BATCH = 5000
MAX_BODY = 5 * 1024 * 1024

model = joblib.load(MODEL_DIR / "ids-model.joblib")
metrics_file = MODEL_DIR / "metrics.json"
metrics = json.loads(metrics_file.read_text(encoding="utf-8")) if metrics_file.exists() else {}
stats = {"requests": 0, "scored": 0, "startedAt": time.strftime("%Y-%m-%d %H:%M:%S")}


def score(items):
    vals = []
    for it in items:
        if isinstance(it, str):
            vals.append(values(it))
        else:
            vals.append(values(it.get("path", ""), it.get("query", "") or ""))
    return [round(float(s), 4) for s in score_requests(model, vals)]


class Handler(BaseHTTPRequestHandler):
    def _send(self, code, body):
        data = json.dumps(body, ensure_ascii=False).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def do_GET(self):
        if self.path == "/health":
            self._send(200, {"status": "ok", "model": metrics, "stats": stats})
        else:
            self._send(404, {"error": "not found"})

    def do_POST(self):
        if self.path != "/predict":
            self._send(404, {"error": "not found"})
            return
        length = int(self.headers.get("Content-Length") or 0)
        if length <= 0 or length > MAX_BODY:
            self._send(413 if length > MAX_BODY else 400, {"error": "body rong hoac qua lon"})
            return
        try:
            body = json.loads(self.rfile.read(length))
            items = body.get("requests", [])
            if not isinstance(items, list) or len(items) > MAX_BATCH:
                raise ValueError(f"'requests' phai la mang toi da {MAX_BATCH} phan tu")
            scores = score(items)
        except (ValueError, AttributeError) as e:
            self._send(400, {"error": str(e)})
            return
        stats["requests"] += 1
        stats["scored"] += len(scores)
        self._send(200, {"scores": scores})

    def log_message(self, fmt, *args):
        pass


if __name__ == "__main__":
    print(f"ML-IDS san sang tren cong {PORT}, model v{metrics.get('version', 1)} "
          f"train luc {metrics.get('trainedAt', '?')}, nguong de xuat {metrics.get('threshold')}", flush=True)
    ThreadingHTTPServer(("0.0.0.0", PORT), Handler).serve_forever()
