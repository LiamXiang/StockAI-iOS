#!/usr/bin/env python3
"""解析 .ips 崩溃报告，打印异常/终止类型与崩溃线程堆栈。"""
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
        if not isinstance(d, dict):
            continue
        # 只处理主崩溃行（有 exception 或 termination 或 faultingThread 的）
        if "exception" not in d and "termination" not in d and "faultingThread" not in d:
            continue
        print("BUG_TYPE:", d.get("bug_type", "?"))
        print("EXC:", json.dumps(d.get("exception", {}), ensure_ascii=False))
        print("TERM:", json.dumps(d.get("termination", {}), ensure_ascii=False))
        print("ASI:", json.dumps(d.get("asi", {}), ensure_ascii=False)[:200])
        print("RES:", json.dumps(d.get("reason", ""), ensure_ascii=False))
        print("SIG:", d.get("signal", {}))
        ft = d.get("faultingThread")
        th = d.get("threads", [])
        im = d.get("usedImages", [])
        if isinstance(ft, int) and ft < len(th):
            print("FAULTING_THREAD:", ft, " name:", th[ft].get("name", ""), "queue:", th[ft].get("queue", ""))
            for fr in th[ft].get("frames", [])[:30]:
                ix = fr.get("imageIndex")
                nm = im[ix].get("name", "?") if isinstance(ix, int) and ix < len(im) else "?"
                print("FRAME:", nm, "|", fr.get("symbol", ""), "+", fr.get("imageOffset", ""))
        break


if __name__ == "__main__":
    main()
