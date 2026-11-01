# 2:1 pixel-art reduction used for the sprite redraw.
import collections
KEY = (153, 255, 255)
def lum(c): return (c[0] * 3 + c[1] * 6 + c[2]) / 10
DARK = 70

def _blocks(get, x0, y0, x1, y1, inside):
    for hy in range(y0, y1):
        for hx in range(x0, x1):
            px = []
            for sy in (hy * 2, hy * 2 + 1):
                for sx in (hx * 2, hx * 2 + 1):
                    if inside(sx, sy): px.append(get(sx, sy))
            yield hx, hy, px

def reduce_rect(get, inside, x0, y0, x1, y1):
    """returns dict (hx,hy)->colour or None"""
    a = {}; b = {}
    for hx, hy, px in _blocks(get, x0, y0, x1, y1, inside):
        op = [c for c in px if c != KEY]
        dark = [c for c in op if lum(c) < DARK]
        light = [c for c in op if lum(c) >= DARK] or op
        # rule A: any dark wins (keeps features, heavy)
        ca = None
        if dark: ca = min(dark, key=lum)
        elif op and len(op) * 2 >= len(px): ca = collections.Counter(op).most_common(1)[0][0]
        # rule B: dark wins on silhouette edges or when it dominates
        cb = None
        if dark and (len(dark) >= 2 or len(op) < len(px)): cb = min(dark, key=lum)
        elif op and len(op) * 2 >= len(px): cb = collections.Counter(light).most_common(1)[0][0]
        a[hx, hy] = ca; b[hx, hy] = cb
    out = dict(b)
    def isdark(c): return c is not None and lum(c) < DARK
    for (hx, hy), ca in a.items():
        cb = b[hx, hy]
        if isdark(ca) and cb is not None and not isdark(cb):
            ok = True
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                n = b.get((hx + dx, hy + dy))
                if n is None or isdark(n): ok = False
            if ok: out[hx, hy] = ca
    return out
