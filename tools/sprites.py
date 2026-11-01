# Build the reduced sprite sheets for one scale (K = 2, 3 or 4).
# Every sprite rect used by loadg() is reduced on its own, addressed exactly
# the way Rt.DerivationGraph does (floor(x/K) .. ceil((x+w)/K)), so sprites
# never bleed into each other. The reduction rule lives in reduce.py; on top
# of it the most visible sprites are hand-drawn for the smaller scales.
#   usage: sprites.py <orig res dir> <out dir> <rects.txt> <K>
import sys, collections
sys.path.insert(0, sys.path[0])
from reduce import reduce_rect, KEY
from PIL import Image
SRC, OUT, RECTS = sys.argv[1], sys.argv[2], sys.argv[3]
K = int(sys.argv[4]) if len(sys.argv) > 4 else 2
names = ["player", "brock", "item", "teki", "haikei", "brock2", "omake", "omake2", "syobon3"]

def fl(x): return x // K
def cl(x): return -((-x) // K)

# ---- hand-drawn pieces ----
# '.' transparent, '#' black, 'o' white, 'M' the block's main colour,
# 'Y' question-block yellow
CAT_HEAD = {
    2: ["....#.....#....",
        "...#o#...#o#...",
        "...#oo###oo#...",
        "..#ooooooooo#..",
        "..#oo#ooo#oo#..",
        "..#oo#ooo#oo#..",
        "..#ooooooooo#..",
        "..#ooo#o#ooo#..",
        "..#oooo#oooo#.."],
    3: ["..#.....#.",
        ".#o#...#o#",
        ".#oo###oo#",
        "#oooooooo#",
        "#oo#oo#oo#",
        "#oooooooo#",
        "#ooo##ooo#"],
}
# whole 8x9 player frames for K=4, keyed by source x of the frame
CAT4 = {
    124: [".#....#.", "#o#..#o#", "#oo##oo#", "#o#oo#o#", "#oooooo#", ".#oooo#.", ".#oooo#.", ".#o##o#.", ".##..##."],
    62:  [".#....#.", "#o#..#o#", "#oo##oo#", "#o#oo#o#", "#oooooo#", ".#oooo#.", ".#oooo#.", "#o#..#o#", "##....##"],
    93:  [".#....#.", "#o#..#o#", "#oo##oo#", "#o#oo#o#", "#oooooo#", ".#oooo#.", ".#oooo#.", "..#oo#..", "..####.."],
    31:  [".#....#.", "#o#..#o#", "#oo##oo#", "#o#oo#o#", "#oooooo#", "#oooooo#", ".#oooo#.", "#o#..#o#", "##....##"],
    0:   [".#....#.", "#o#..#o#", "#oo##oo#", "#o#oo#o#", "#oooooo#", ".#o##o#.", ".#oooo#.", ".#o##o#.", ".##..##."],
}
TILE4 = {  # brock.PNG column -> 8x8 pattern
    1: ["########", "MMM#MMMM", "MMM#MMMM", "########", "M#MMMM#M", "M#MMMM#M", "########", "MMM#MMMM"],
    2: ["########", "#YYYYYY#", "#YY##YY#", "#YYYY#Y#", "#YYY#YY#", "#YYYYYY#", "#YYY#YY#", "########"],
    3: ["########", "#MMMMMM#", "#MMMMMM#", "#MMMMMM#", "#MMMMMM#", "#MMMMMM#", "#MMMMMM#", "########"],
    4: ["########", "#MMMMMM#", "#M####M#", "#M#MM#M#", "#M#MM#M#", "#M####M#", "#MMMMMM#", "########"],
    5: ["########", "#MMMM#M#", "#MMMM#M#", "#MMM#MM#", "####MMM#", "#MMM#MM#", "#MMM#MM#", "########"],
    6: ["########", "#MM#MMM#", "#MM#####", "#MM#MMM#", "#MM#MMM#", "#MM#####", "#MM#MMM#", "########"],
}
BLOB4 = ["..####..", ".#oooo#.", "#o#oo#o#", "#oooooo#", "#oo##oo#", "#oooooo#", ".#oooo#.", "..####.."]

def paint(op, x0, y0, w, h, pat, pal):
    ph, pw = len(pat), len(pat[0])
    for j in range(h):
        for i in range(w):
            ch = pat[j * ph // h][i * pw // w]
            op[x0 + i, y0 + j] = pal.get(ch, KEY)

BASE = {'.': KEY, '#': (0, 0, 0), 'o': (255, 255, 255), 'Y': (204, 204, 102)}

rects = collections.defaultdict(list)
for l in open(RECTS):
    p = l.split()
    if p and p[0] == 'RECT':
        s, x, y, w, h = map(int, p[1:]); rects[s].append((x, y, w, h))

for sid, nm in enumerate(names):
    im = Image.open('%s/%s.PNG' % (SRC, nm)).convert('RGB')
    W, H = im.size; pix = im.load()
    rs = rects.get(sid)
    if not rs:
        # whole image (the title logo): smooth resize, no transparency
        out = im.resize((cl(W), cl(H)), Image.LANCZOS)
        out.quantize(colors=128, dither=Image.NONE).save('%s/%s.png' % (OUT, nm), optimize=True)
        continue
    need_w = max([cl(x + w) for (x, y, w, h) in rs] + [cl(W)])
    need_h = max([cl(y + h) for (x, y, w, h) in rs] + [cl(H)])
    out = Image.new('RGB', (need_w, need_h), KEY); op = out.load()
    for (x, y, w, h) in rs:
        d = reduce_rect(lambda a, b: pix[a, b],
                        lambda a, b, x=x, y=y, w=w, h=h: x <= a < x + w and y <= b < y + h and a < W and b < H,
                        fl(x), fl(y), cl(x + w), cl(y + h), K)
        for k, c in d.items(): op[k] = c if c else KEY

    if nm == 'player':
        for fx in (0, 31, 62, 93, 124):
            if K == 4:
                paint(op, fl(fx), 0, cl(fx + 30) - fl(fx), cl(36), CAT4[fx], BASE)
            elif K in CAT_HEAD:
                for ry, row in enumerate(CAT_HEAD[K]):
                    for rx, ch in enumerate(row):
                        op[fl(fx) + rx, ry] = BASE[ch]
    if nm == 'brock' and K == 4:
        for r in range(3):
            main = pix[33 * 3 + 15, 33 * r + 15]
            pal = dict(BASE); pal['M'] = main
            for t, pat in TILE4.items():
                x, y = 33 * t, 33 * r
                paint(op, fl(x), fl(y), cl(x + 30) - fl(x), cl(y + 30) - fl(y), pat, pal)
    if nm == 'teki' and K == 4:
        paint(op, 0, 0, cl(30), cl(30), BLOB4, BASE)

    q = out.quantize(colors=255, method=Image.MEDIANCUT, dither=Image.NONE)
    pal = q.getpalette(); idx = None
    for i in range(len(pal) // 3):
        if tuple(pal[i * 3:i * 3 + 3]) == KEY: idx = i; break
    if idx is not None:
        q.save('%s/%s.png' % (OUT, nm), optimize=True, transparency=idx)
    else:
        q.save('%s/%s.png' % (OUT, nm), optimize=True)
