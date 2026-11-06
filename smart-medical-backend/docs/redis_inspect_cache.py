# -*- coding: utf-8 -*-
"""检查语义缓存条目：分类、TTL、答案长度（验证 PR-3 的分类 TTL）。"""
import json
import socket

HOST, PORT = "127.0.0.1", 6379
ANSWER_PREFIX = "sm:semcache:ans:"


def encode(*args):
    out = f"*{len(args)}\r\n".encode()
    for a in args:
        b = a.encode() if isinstance(a, str) else a
        out += f"${len(b)}\r\n".encode() + b + b"\r\n"
    return out


def read_line(f):
    line = f.readline()
    if not line.endswith(b"\r\n"):
        raise IOError("short read")
    return line[:-2]


def read_reply(f):
    line = read_line(f)
    t, rest = line[:1], line[1:]
    if t == b"+":
        return rest.decode()
    if t == b"-":
        raise RuntimeError(rest.decode())
    if t == b":":
        return int(rest)
    if t == b"$":
        n = int(rest)
        if n == -1:
            return None
        data = f.read(n)
        f.read(2)
        return data.decode("utf-8", "replace")
    if t == b"*":
        n = int(rest)
        if n == -1:
            return None
        return [read_reply(f) for _ in range(n)]
    raise RuntimeError("unknown reply type: %r" % t)


def main():
    with socket.create_connection((HOST, PORT), timeout=5) as s:
        f = s.makefile("rb")

        def cmd(*args):
            s.sendall(encode(*args))
            return read_reply(f)

        keys = cmd("KEYS", ANSWER_PREFIX + "*") or []
        print("TOTAL_KEYS:", len(keys))
        for key in keys:
            raw = cmd("GET", key)
            ttl = cmd("TTL", key)
            category = "?"
            qlen = 0
            if raw:
                try:
                    obj = json.loads(raw)
                    category = obj.get("category", "(none)")
                    qlen = len(obj.get("answer", ""))
                except Exception:
                    pass
            print("KEY=%s | category=%s | ttl_seconds=%s | ttl_days=%.1f | answer_len=%d"
                  % (key, category, ttl, (ttl or 0) / 86400.0, qlen))


if __name__ == "__main__":
    main()
