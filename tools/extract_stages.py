# Pull stagedatex[17][1001] initializers out of main.cpp into binary files,
# replacing each with a loadStage(n) call. Writes the remaining code to logic.cpp.
import re, sys, os
src = open(sys.argv[1], encoding='utf-8', errors='replace').read()
outdir = sys.argv[2]
os.makedirs(outdir, exist_ok=True)
pat = re.compile(r'byte\s+stagedatex\s*\[17\]\s*\[1001\]\s*=\s*\{')
out = []; pos = 0; n = 0; maxv = 0
while True:
    m = pat.search(src, pos)
    if not m: break
    out.append(src[pos:m.start()])
    i = m.end(); depth = 1; body_start = i
    while depth:
        c = src[i]
        if c == '{': depth += 1
        elif c == '}': depth -= 1
        elif c == '/' and src[i+1] == '/':
            i = src.index('\n', i)
        elif c == '/' and src[i+1] == '*':
            i = src.index('*/', i) + 1
        i += 1
    body = src[body_start:i-1]
    body = re.sub(r'//[^\n]*', '', body); body = re.sub(r'/\*.*?\*/', '', body, flags=re.S)
    rows = re.findall(r'\{([^{}]*)\}', body)
    assert len(rows) <= 17, len(rows)
    data = bytearray(17 * 1001)
    for r, row in enumerate(rows):
        vals = [int(v) for v in row.replace('\n', ' ').split(',') if v.strip()]
        assert len(vals) <= 1001
        for c, v in enumerate(vals):
            assert 0 <= v <= 255; maxv = max(maxv, v)
            data[r * 1001 + c] = v
    open(os.path.join(outdir, 's%d.bin' % n), 'wb').write(data)
    j = src.index(';', i)
    out.append('stagedatex = loadStage(%d);' % n)
    pos = j + 1; n += 1
out.append(src[pos:])
open(sys.argv[3], 'w', encoding='utf-8').write(''.join(out))
print('stages', n, 'max tile', maxv)
