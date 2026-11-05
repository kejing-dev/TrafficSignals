#!/usr/bin/env python3
"""B24 check on an officer close-up: python3 tools/officer_light.py SHOT.png [x0 y0 x1 y1]
Finds the officer's box (or takes it), prints how much of him is burnt out: share of pixels with all of R, G, B > 245,
the largest connected patch of them, and the brightest area (the 99th percentile luminance). Blocker: a large patch."""
import sys
from collections import deque

from PIL import Image


def main():
    im = Image.open(sys.argv[1]).convert("RGB")
    if len(sys.argv) >= 6:
        box = tuple(int(v) for v in sys.argv[2:6])
    else:
        w, h = im.size
        box = (int(w * 0.34), int(h * 0.30), int(w * 0.66), int(h * 0.66))
    c = im.crop(box)
    w, h = c.size
    px = c.load()
    hot = [[all(v > 245 for v in px[x, y]) for x in range(w)] for y in range(h)]
    total = sum(map(sum, hot))
    seen = [[False] * w for _ in range(h)]
    best = 0
    for y in range(h):
        for x in range(w):
            if hot[y][x] and not seen[y][x]:
                n = 0
                q = deque([(x, y)])
                seen[y][x] = True
                while q:
                    a, b = q.popleft()
                    n += 1
                    for u, v in ((a + 1, b), (a - 1, b), (a, b + 1), (a, b - 1)):
                        if 0 <= u < w and 0 <= v < h and hot[v][u] and not seen[v][u]:
                            seen[v][u] = True
                            q.append((u, v))
                best = max(best, n)
    lum = sorted(0.2126 * px[x, y][0] + 0.7152 * px[x, y][1] + 0.0722 * px[x, y][2] for y in range(h) for x in range(w))
    print(f"box={box} size={w}x{h} hot>245={total} ({100 * total / (w * h):.2f}%) largestPatch={best}px lumP99={lum[int(len(lum) * 0.99)]:.0f} lumMax={lum[-1]:.0f}")


main()
