import re
from urllib.parse import unquote_plus

_HOST = re.compile(r"^[a-z]+://[^/]+", re.IGNORECASE)
_PROTO = re.compile(r"\s+HTTP/\d(\.\d)?\s*$", re.IGNORECASE)
_DIGITS = re.compile(r"\d+")
_SAFE = re.compile(r"^[0-9.,:/ -]*$")
MAX_LEN = 500


def _unquote(s):
    try:
        return unquote_plus(s, errors="strict")
    except UnicodeDecodeError:
        return unquote_plus(s, encoding="latin-1")


def decode(s, rounds=2):
    for _ in range(rounds):
        d = _unquote(s)
        if d == s:
            break
        s = d
    return s


def split_url(path, query=""):
    path = "" if path is None else str(path)
    query = "" if query is None else str(query)
    path = _PROTO.sub("", _HOST.sub("", path.strip()))
    if "?" in path and not query:
        path, query = path.split("?", 1)
    return path, _PROTO.sub("", query)


def clean(value):
    v = _DIGITS.sub("0", decode(str(value)).lower()).strip()
    return "" if _SAFE.match(v) else v[:MAX_LEN]


def values(path, query=""):
    _, query = split_url(path, query)
    out = []
    for part in query.split("&"):
        if not part:
            continue
        v = clean(part.split("=", 1)[1] if "=" in part else part)
        if v:
            out.append(v)
    return out
