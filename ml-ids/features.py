import re
from urllib.parse import unquote_plus

_HOST = re.compile(r"^[a-z]+://[^/]+", re.IGNORECASE)
_PROTO = re.compile(r"\s+HTTP/\d(\.\d)?\s*$", re.IGNORECASE)
_DIGITS = re.compile(r"\d+")


def _decode(s, rounds=2):
    for _ in range(rounds):
        d = unquote_plus(s)
        if d == s:
            break
        s = d
    return s


def normalize(path, query=""):
    path = "" if path is None else str(path)
    query = "" if query is None else str(query)
    path = _PROTO.sub("", _HOST.sub("", path.strip()))
    if "?" in path and not query:
        path, query = path.split("?", 1)
    text = path + ("?" + query if query else "")
    text = _decode(text).lower()
    text = _DIGITS.sub("0", text)
    return text[:2000]