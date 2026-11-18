import sys
from pathlib import Path

import joblib

from features import normalize

model = joblib.load("model/ids-model.joblib")
file = Path(sys.argv[1] if len(sys.argv) > 1 else "payloads.txt")
for line in file.read_text(encoding="utf-8").splitlines():
    line = line.strip()
    if not line or line.startswith("#"):
        continue
    path, _, query = line.partition("?")
    p = model.predict_proba([normalize(path, query)])[0, 1]
    print(f"{p:.3f}  {'ATTACK' if p >= 0.9 else ('nghi ngo' if p >= 0.5 else 'normal')}  {line}")
