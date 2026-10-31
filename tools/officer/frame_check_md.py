"""Writes build/frame-check.md (design verification.frameCheck): every one of the 43 steps measured on the exported GLB
(tools/officer/check_officer.py -> measured.json) against the design's direction vectors and the official text.

  python3 tools/officer/frame_check_md.py <measured.json> <render_dir> <out.md> <sheet_dir>
"""
import json
import math
import os
import subprocess
import sys

MEAS, RENDERS, OUT, SHEETS = sys.argv[1:5]
os.makedirs(SHEETS, exist_ok=True)
m = json.load(open(MEAS))
design = json.load(open(os.path.join(os.path.dirname(OUT), "..", "design-package.json")))
texts = {g["id"]: [s["text"] for s in g["steps"]] for g in design["gestures"]["list"]}
names = {g["id"]: g["zh"] for g in design["gestures"]["list"]}

D, U, F, L, R, B = (0, -1, 0), (0, 1, 0), (0, 0, 1), (1, 0, 0), (-1, 0, 0), (0, 0, -1)
REST_L, REST_R = (D, D, D, R), (D, D, D, L)
up135 = (0, .71, .71)


def st(l=None, r=None, nk=None):
    return {"l": l, "r": r, "neck": nk}


REST = st(REST_L, REST_R, 0)
T = {
    "stop": [st(l=(up135, up135, U, F), nk=0), REST],
    "straight": [st(l=(L, L, L, F), nk=45), st(r=(R, R, R, F), nk=-45), st(r=(F, L, L, B), nk=45), st(r=(F, F, F, L)), st(r=(F, L, L, B)), st(r=REST_R), REST],
    "left": [st(r=(F, F, U, F), nk=45), st(l=((-.38, -.71, .59),) * 3 + (R,)), st(l=REST_L), st(l=((-.38, -.71, .59),) * 3 + (R,)), st(l=REST_L), REST],
    "leftWait": [st(l=((.71, -.71, 0),) * 3 + (D,), nk=45), st(l=((.26, -.97, 0),) * 3 + (D,)), st(l=((.71, -.71, 0),) * 3 + (D,)), st(l=((.26, -.97, 0),) * 3 + (D,)), REST],
    "right": [st(l=(F, F, U, F), nk=-45), st(r=((.38, -.71, .59),) * 3 + (L,)), st(r=REST_R), st(r=((.38, -.71, .59),) * 3 + (L,)), st(r=REST_R), REST],
    "laneChange": [st(r=(F, F, F, L), nk=0), st(r=((.71, 0, .71),) * 3 + ((.71, 0, -.71),)), st(r=(F, F, F, L)), st(r=((.71, 0, .71),) * 3 + ((.71, 0, -.71),)), REST],
    "slow": [st(r=((-.71, 0, .71),) * 3 + (D,), nk=-45), st(r=((-.5, -.71, .5),) * 3 + (D,)), st(r=((-.71, 0, .71),) * 3 + (D,)), st(r=((-.5, -.71, .5),) * 3 + (D,)), REST],
    "pullOver": [st(r=((0, -.71, .71),) * 3 + (L,), nk=0), st(l=(up135, up135, U, F)), st(r=((.5, -.71, .5),) * 3 + (L,)), st(r=((0, -.71, .71),) * 3 + (L,)), st(r=((.5, -.71, .5),) * 3 + (L,)), st(r=((0, -.71, .71),) * 3 + (L,)), REST],
}


def norm(v):
    n = math.sqrt(sum(x * x for x in v)) or 1
    return [x / n for x in v]


def ang(a, b):
    a, b = norm(a), norm(b)
    return math.degrees(math.acos(max(-1, min(1, sum(x * y for x, y in zip(a, b))))))


def perp(n, h):
    n, h = norm(n), norm(h)
    d = sum(x * y for x, y in zip(n, h))
    return [x - d * y for x, y in zip(n, h)]


lines = [
    "# 交警手势 · 逐节核对（frame check）",
    "",
    "研发站产物（设计包 verification.frameCheck）。量的是**导出后的运行时 GLB**（`app/src/main/assets/officer.glb`，不是烘焙会话）：",
    "每节在 `officer_clips.json` 的定格时刻取骨骼，算大臂 / 手掌方向、掌心法线、头部偏转，和设计包的方向向量比；角度按原文「与身体成 X 度」= 手臂与躯干竖直向下的夹角。",
    "阈值：大臂 ≤ 10°、手掌 ≤ 15°、掌心 ≤ 35°（只比与手掌垂直的分量）、头偏 ±8°。官方图解本机拿不到（吉林省公安厅转载页 404），按原文文字核角度；图解对照留给真机 / 用户复看。",
    "渲染图在 `build/frame-check/<手势>.png`（每节一列：上正面、下侧面）。",
    "",
    "| 手势 | 节 | 原文 | 大臂与身体 | 误差 大臂 / 手 / 掌心 | 头偏（+左） | 结论 |",
    "|---|---|---|---|---|---|---|",
]
total = ok = 0
for g, steps in T.items():
    state = {"l": REST_L, "r": REST_R, "neck": 0}
    meas = m.get(g, [])
    for k, s in enumerate(steps):
        for key in ("l", "r", "neck"):
            if s.get(key) is not None:
                state[key] = s[key]
        me = meas[k]
        errs = []
        cells = []
        bodyang = []
        for side in ("l", "r"):
            u, f, h, n = state[side]
            mu, mh, mp = me[side]["upper"], me[side]["hand"], me[side]["palm"]
            eu, eh = ang(u, mu), ang(h, mh)
            ep = ang(perp(n, h), perp(mp, mh)) if (abs(sum(norm(n)[i] * norm(h)[i] for i in range(3))) < 0.95) else 0
            moving = u != D or h != D
            if moving:
                bodyang.append(("左臂" if side == "l" else "右臂") + " %d°" % me[side]["upperToDown"])
                cells.append("%s %.0f/%.0f/%.0f" % ("左" if side == "l" else "右", eu, eh, ep))
            errs.append(eu <= 10 and eh <= 15 and (ep <= 35))
        head_ok = abs(me["headYawDeg"] - state["neck"]) <= 8
        good = all(errs) and head_ok
        total += 1
        ok += good
        lines.append("| %s | %d | %s | %s | %s | %d（设计 %d） | %s |" % (names[g], k + 1, texts[g][k], "、".join(bodyang) or "立正", "；".join(cells) or "立正", me["headYawDeg"], state["neck"], "一致" if good else "**偏差**"))
    # contact sheet: fronts on top, sides below
    files = ["%s/%s_%d_front.png" % (RENDERS, g, i + 1) for i in range(len(steps))] + ["%s/%s_%d_side.png" % (RENDERS, g, i + 1) for i in range(len(steps))]
    subprocess.run(["python3", os.path.join(os.path.dirname(__file__), "..", "contact.py"), os.path.join(SHEETS, g + ".png"), str(len(steps)), "180"] + files, check=True, stdout=subprocess.DEVNULL)
lines.insert(6, "**结果：%d / %d 节一致。**" % (ok, total))
lines.insert(7, "")
open(OUT, "w").write("\n".join(lines) + "\n")
print("frame-check", ok, "/", total)
