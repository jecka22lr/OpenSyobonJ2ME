# Build half-scale sprite sheets. Every sprite rect used by loadg() is
# reduced on its own, aligned the same way Rt.DerivationGraph addresses it.
# Reduction keeps 1px dark outlines (darkest pixel in a 2x2 block wins when
# it is outline-dark), otherwise the most common opaque colour.
import sys, collections
sys.path.insert(0, sys.path[0])
from reduce import reduce_rect
from PIL import Image
SRC = sys.argv[1]; OUT = sys.argv[2]; RECTS = sys.argv[3]
names = ["player", "brock", "item", "teki", "haikei", "brock2", "omake", "omake2", "syobon3"]
KEY = (153, 255, 255)
# hand-drawn half-size cat head for the player frames
CAT_HEAD = [
    "....#.....#....",
    "...#o#...#o#...",
    "...#oo###oo#...",
    "..#ooooooooo#..",
    "..#oo#ooo#oo#..",
    "..#oo#ooo#oo#..",
    "..#ooooooooo#..",
    "..#ooo#o#ooo#..",
    "..#oooo#oooo#..",
]
rects = collections.defaultdict(list)
for l in open(RECTS):
    p = l.split()
    if p and p[0] == 'RECT':
        s, x, y, w, h = map(int, p[1:]); rects[s].append((x, y, w, h))

def lum(c): return (c[0] * 3 + c[1] * 6 + c[2]) / 10

def reduce_block(px):
    op = [c for c in px if c != KEY]
    if not op: return None
    dark = [c for c in op if lum(c) < 70]
    if dark: return min(dark, key=lum)
    if len(op) * 2 < len(px): return None
    return collections.Counter(op).most_common(1)[0][0]

for sid, nm in enumerate(names):
    im = Image.open('%s/%s.PNG' % (SRC, nm)).convert('RGB')
    W, H = im.size; pix = im.load()
    hw, hh = (W + 1) // 2, (H + 1) // 2
    out = Image.new('RGB', (hw, hh), KEY); op = out.load()
    rs = rects.get(sid)
    if not rs:
        # whole image (title logo): plain box filter, no transparency
        out = im.resize((hw, hh), Image.LANCZOS)
    else:
        need_w = max([ (x + w + 1) // 2 for (x, y, w, h) in rs] + [hw])
        need_h = max([ (y + h + 1) // 2 for (x, y, w, h) in rs] + [hh])
        if (need_w, need_h) != (hw, hh):
            out = Image.new('RGB', (need_w, need_h), KEY); op = out.load()
        for (x, y, w, h) in rs:
            x0, y0 = x // 2, y // 2
            x1, y1 = (x + w + 1) // 2, (y + h + 1) // 2
            d = reduce_rect(lambda a, b: pix[a, b],
                            lambda a, b, x=x, y=y, w=w, h=h: x <= a < x + w and y <= b < y + h and a < W and b < H,
                            x0, y0, x1, y1)
            for k, c in d.items(): op[k] = c if c else KEY
        if nm == 'player':
            for fx in (0, 31, 62, 93, 124):
                hx = fx // 2
                for ry, row in enumerate(CAT_HEAD):
                    for rx, ch in enumerate(row):
                        op[hx + rx, ry] = {'.': KEY, '#': (0, 0, 0), 'o': (255, 255, 255)}[ch]
    q = out.quantize(colors=255, method=Image.MEDIANCUT, dither=Image.NONE) if rs else out.quantize(colors=128, dither=Image.NONE)
    if rs:
        # mark the key colour transparent
        pal = q.getpalette()
        idx = None
        for i in range(len(pal) // 3):
            if tuple(pal[i*3:i*3+3]) == KEY: idx = i; break
        if idx is not None:
            q.save('%s/%s.png' % (OUT, nm), optimize=True, transparency=idx)
            continue
    q.save('%s/%s.png' % (OUT, nm), optimize=True)
    print(nm)
