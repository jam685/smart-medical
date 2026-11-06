# -*- coding: utf-8 -*-
"""最小 Redis RESP 客户端：用于验证语义缓存孤儿清理（读 KEYS_SET、删除一个答案 key 模拟 TTL 过期）。"""
import socket
import sys

HOST, PORT = "127.0.0.1", 6379


def encode(*args):
    out = f"*{len(args)}\r\n".encode()
    for a in args:
        b = a.encode() if isinstance(a, str) else a
        out += f"${len(b)}\r\n".encode() + b + b"\r\n"
    return out


def read_line(f):
    line = f.readline()
    if not line.endswith(b"\r\n"):
        raise IOError("short read: %r" % line)
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
        s.sendall(encode("SMEMBERS", "sm:semcache:keys"))
        keys = read_reply(f) or []
        print("KEYS_BEFORE:", len(keys))
        if not keys:
            print("NO_KEYS")
            return
        victim = keys[0]
        print("VICTIM:", victim)
        s.sendall(encode("DEL", victim))
        print("DEL_RESULT:", read_reply(f))
        s.sendall(encode("SCARD", "sm:semcache:keys"))
        print("SET_SIZE_AFTER_DEL:", read_reply(f))
        s.sendall(encode("KEYS", "sm:semcache:lock:*"))
        locks = read_reply(f) or []
        print("LOCK_KEYS:", len(locks))


if __name__ == "__main__":
    sys.exit(main())
