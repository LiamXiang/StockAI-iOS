#!/usr/bin/env python3
"""解析 .ips 崩溃报告，打印异常类型与崩溃线程堆栈。"""
import json
import sys


def main():
    if len(sys.argv) < 2:
        print("usage: parse_ips.py <file.ips>")
        return
    txt = open(sys.argv[1], encoding="utf-8", errors="replace").read()
    for line in txt.split("\n"):
        line = line.strip()
        if not line:
            continue
        try:
            d = json.loads(line)
        except Exception:
            continue
        if not isinstance(d, dict) or "exception" not in d:
            continue
        print("EXC:", json.dumps(d.get("exception", {}), ensure_ascii=False))
        print("TERM:", json.dumps(d.get("termination", {}), ensure_ascii=False))
        print("ASI:", json.dumps(d.get("asi", {}), ensure_ascii=False))
        print("REPORT:", json.dumps(d.get("report", {}), ensure_ascii=False)[:300])
        ft = d.get("faultingThread")
        th = d.get("threads", [])
        im = d.get("usedImages", [])
        if isinstance(ft, int) and ft < len(th):
            for fr in th[ft].get("frames", [])[:25]:
                ix = fr.get("imageIndex")
                nm = im[ix].get("name", "?") if isinstance(ix, int) and ix < len(im) else "?"
                print("FRAME:", nm, "|", fr.get("symbol", ""), "+", fr.get("imageOffset", ""))
        break


if __name__ == "__main__":
    main()
