# k:1 pixel-art reduction used for the sprite redraw (k = 2, 3 or 4).
import collections
KEY = (153, 255, 255)
def lum(c): return (c[0] * 3 + c[1] * 6 + c[2]) / 10
DARK = 70

def reduce_rect(get, inside, x0, y0, x1, y1, k=2):
    """Reduce source pixels to target cells [x0,x1)x[y0,y1); returns
    dict (tx,ty) -> colour or None (transparent)."""
    a = {}; b = {}
    for ty in range(y0, y1):
        for tx in range(x0, x1):
            px = [get(sx, sy) for sy in range(ty * k, ty * k + k)
                  for sx in range(tx * k, tx * k + k) if inside(sx, sy)]
            op = [c for c in px if c != KEY]
            dark = [c for c in op if lum(c) < DARK]
            light = [c for c in op if lum(c) >= DARK] or op
            # A: any dark pixel wins (keeps features, heavy)
            ca = None
            if dark: ca = min(dark, key=lum)
            elif op and len(op) * 2 >= len(px): ca = collections.Counter(op).most_common(1)[0][0]
            # B: dark wins on the silhouette edge or when it fills a line's worth
            cb = None
            if dark and (len(dark) >= (k if k < 4 else len(px) // 3) or len(op) < len(px)): cb = min(dark, key=lum)
            elif op and len(op) * 2 >= len(px): cb = collections.Counter(light).most_common(1)[0][0]
            a[tx, ty] = ca; b[tx, ty] = cb
    # B, plus A's dark details where they sit fully inside the shape (eyes etc.)
    out = dict(b)
    def isdark(c): return c is not None and lum(c) < DARK
    for (tx, ty), ca in a.items():
        cb = b[tx, ty]
        if isdark(ca) and cb is not None and not isdark(cb):
            ok = True
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                n = b.get((tx + dx, ty + dy))
                if n is None or isdark(n): ok = False
            if ok: out[tx, ty] = ca
    return out
