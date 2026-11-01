# Pre-render every character used in the game's strings with Sazanami Gothic's
# 12px embedded bitmaps and store them as horizontal runs (see Font.java).
import sys, re, struct, glob
from PIL import Image, ImageDraw, ImageFont
ttf, out = sys.argv[1], sys.argv[2]
chars = set(chr(c) for c in range(0x20, 0x7f))
for f in sys.argv[3:]:
    src = open(f, encoding='utf-8').read()
    for lit in re.findall(r'"((?:[^"\\]|\\.)*)"', src):
        chars.update(lit)
chars.discard('\\')
chars.add('\\')
font = ImageFont.truetype(ttf, 12)
chars = sorted(c for c in chars if c not in (' ', '　'))
widths = []; starts = [0]; spans = bytearray()
for c in chars:
    w = int(round(font.getlength(c)))
    if w <= 0: w = 6
    widths.append(w)
    im = Image.new('1', (16, 16), 0); d = ImageDraw.Draw(im); d.fontmode = '1'
    d.text((0, 0), c, font=font, fill=1)
    n = 0
    for y in range(16):
        x = 0
        while x < 16:
            if im.getpixel((x, y)):
                x0 = x
                while x < 16 and im.getpixel((x, y)): x += 1
                spans += bytes([y, x0, x - x0]); n += 1
            else:
                x += 1
    starts.append(starts[-1] + n)
data = struct.pack('>H', len(chars))
data += b''.join(struct.pack('>H', ord(c)) for c in chars)
data += bytes(widths)
data += b''.join(struct.pack('>i', s) for s in starts)
data += bytes(spans)
open(out, 'wb').write(data)
print(len(chars), 'glyphs', len(data), 'bytes')
