# Pre-render every character used in the game's strings with Sazanami Gothic's
# embedded bitmaps (12px, or 10px for the small-screen builds) and store them as horizontal runs (see Font.java).
import sys, re, struct, glob
from PIL import Image, ImageDraw, ImageFont
ttf, out, size = sys.argv[1], sys.argv[2], int(sys.argv[3])
chars = set(chr(c) for c in range(0x20, 0x7f))
for f in sys.argv[4:]:
    src = open(f, encoding='utf-8').read()
    if f.endswith('.txt'):          # translation tables: every character counts
        chars.update(src.replace('\n', '').replace('\t', ''))
        continue
    for lit in re.findall(r'"((?:[^"\\]|\\.)*)"', src):
        chars.update(lit)
chars.discard('\\')
chars.add('\\')
font = ImageFont.truetype(ttf, size)
chars = sorted(c for c in chars if c not in (' ', '　'))
widths = []; starts = [0]; spans = bytearray()
for c in chars:
    w = int(round(font.getlength(c)))
    if w <= 0: w = size // 2
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
data = struct.pack('>BH', size, len(chars))
data += b''.join(struct.pack('>H', ord(c)) for c in chars)
data += bytes(widths)
data += b''.join(struct.pack('>i', s) for s in starts)
data += bytes(spans)
open(out, 'wb').write(data)
print(len(chars), 'glyphs', len(data), 'bytes')
