import sys
from pathlib import Path

import joblib

from dataset import score_requests
from features import values

THRESHOLD = 0.9

model = joblib.load("model/ids-model.joblib")
file = Path(sys.argv[1] if len(sys.argv) > 1 else "payloads.txt")
for line in file.read_text(encoding="utf-8").splitlines():
    line = line.strip()
    if not line or line.startswith("#"):
        continue
    p = score_requests(model, [values(line)])[0]
    print(f"{p:.3f}  {'ATTACK' if p >= THRESHOLD else 'normal'}  {line}")
