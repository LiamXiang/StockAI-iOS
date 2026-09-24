#!/usr/bin/env python3
"""解析 .ips 崩溃报告，打印异常/终止类型与崩溃线程堆栈。"""
import json
import sys


def main():
    if len(sys.argv) < 2:
        print("usage: parse_ips.py <file.ips>")
        return
    txt = open(sys.argv[1], encoding="utf-8", errors="replace").read()
    decoder = json.JSONDecoder()
    pos = 0
    n = len(txt)
    while pos < n:
        while pos < n and txt[pos] != "{":
            pos += 1
        if pos >= n:
            break
        try:
            d, end = decoder.raw_decode(txt, pos)
        except Exception:
            pos += 1
            continue
        pos = end
        if not isinstance(d, dict):
            continue
        if "exception" not in d and "termination" not in d and "faultingThread" not in d:
            continue
        print("BUG_TYPE:", d.get("bug_type", "?"))
        print("CAPTURE:", d.get("captureTime", ""))
        print("LAUNCH:", d.get("procLaunch", ""))
        print("EXC:", json.dumps(d.get("exception", {}), ensure_ascii=False))
        print("TERM:", json.dumps(d.get("termination", {}), ensure_ascii=False))
        print("ASI:", json.dumps(d.get("asi", {}), ensure_ascii=False)[:300])
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
        print("LEGACY:", json.dumps(d.get("legacyInfo", {}), ensure_ascii=False)[:200])
        break


if __name__ == "__main__":
    main()
