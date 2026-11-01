# Mechanical C++ -> Java translation of Open Syobon Action's game logic.
# Output: G.java with all globals and functions as static members.
import re, sys
logic = open(sys.argv[1], encoding='utf-8').read()
header = open(sys.argv[2], encoding='utf-8').read()
loadg = open(sys.argv[3], encoding='utf-8').read()

def strip_funcs(src, names):
    # remove whole function definitions by name
    for n in names:
        m = re.search(r'\n[A-Za-z_][\w \*]*\b' + n + r'\s*\([^)]*\)\s*\{', src)
        if not m: print('nofunc', n); continue
        i = m.end(); depth = 1
        while depth:
            c = src[i]
            if c == '{': depth += 1
            elif c == '}': depth -= 1
            elif src.startswith('//', i): i = src.index('\n', i) - 1
            elif src.startswith('/*', i): i = src.index('*/', i) + 1
            elif c == '"':
                i += 1
                while src[i] != '"':
                    if src[i] == '\\': i += 1
                    i += 1
            i += 1
        src = src[:m.start()] + '\n' + src[i:]
    return src

# drop block comments (keep line comments - harmless) but carefully not inside strings
def drop_block_comments(s):
    out = []; i = 0
    while i < len(s):
        if s[i] == '"':
            j = i + 1
            while s[j] != '"':
                if s[j] == '\\': j += 1
                j += 1
            out.append(s[i:j+1]); i = j + 1
        elif s.startswith('//', i):
            j = s.find('\n', i); j = len(s) if j < 0 else j
            i = j
        elif s.startswith('/*', i):
            i = s.index('*/', i) + 2
        else:
            out.append(s[i]); i += 1
    return ''.join(out)

logic = drop_block_comments(logic)
loadg = drop_block_comments(loadg)
header = drop_block_comments(header)

logic = re.sub(r'#include[^\n]*', '', logic)
logic = strip_funcs(logic, ['main', 'deinit', 'loadimage', 'loadimage', 'drawimage', 'drawimage',
    'str', 'setfont', 'setfont', 'ot', 'bgmchange', 'setcolor', 'setc0', 'setc1', 'drawpixel',
    'drawline', 'drawrect', 'fillrect', 'drawarc', 'fillarc', 'FillScreen', 'wait2', 'rand', 'txmsg'])

# loadg: keep body of loadg() only
m = re.search(r'void loadg\(void\)\s*\{', loadg)
body = loadg[m.end():]
body = body[:body.index('extern bool sound')]
body = body[:body.rindex('}')]
loadg_java = 'static int x1;\nstatic void loadg() {' + body + '}\n'

def conv(s):
    s = re.sub(r'\bSDL_Surface\s*\*\s*', 'Img ', s)
    s = re.sub(r'\bstring\b', 'String', s)
    s = re.sub(r'\bint \(([^()]*)\)', r'(int) (\1)', s)
    s = re.sub(r'\blong\s*\(GetNowCount\(\)\)', 'GetNowCount()', s)
    s = re.sub(r'\bot\(oto\[(\d+)\]\)', r'ot(\1)', s)
    s = re.sub(r'\bbgmchange\(otom\[(\d+)\]\)', r'bgmchange(\1)', s)
    s = re.sub(r'CheckSoundMem\(oto\[\d+\]\)', '0', s)
    s = re.sub(r'StopSoundMem\(oto\[(\d+)\]\);', r'StopSoundMem(\1);', s)
    s = re.sub(r'Mix_HaltChannel\(-1\);', 'stopAllSe();', s)
    s = re.sub(r'Mix_HaltMusic\(\);', 'stopBgm();', s)
    s = re.sub(r'SetFontThickness\([^)]*\);', '', s)
    s = re.sub(r'SetFontSize\([^)]*\);', '', s)
    s = re.sub(r'\|\|\s*SDL_JoystickGetButton\([^)]*\)', '', s)
    s = re.sub(r'CheckHitKey\((KEY_INPUT_\w+)\)\s*==\s*1', r'key(\1)', s)
    s = re.sub(r'CheckHitKey\((KEY_INPUT_\w+)\)\s*==\s*0', r'!key(\1)', s)
    s = re.sub(r'CheckHitKey\((KEY_INPUT_\w+)\)', r'key(\1)', s)
    s = re.sub(r'\bfma\b', 'fmaZ', s)
    s = re.sub(r'\bstime\b', 'stimeZ', s)
    s = re.sub(r'(?<![\w.])abs\(', 'Math.abs(', s)
    s = re.sub(r'(?<![\w.])sin\(', 'Math.sin(', s)
    s = re.sub(r'(?<![\w.])cos\(', 'Math.cos(', s)
    s = re.sub(r'(?<![\w.])rand\(', 'rnd(', s)
    s = re.sub(r'\bend\(\)', 'end()', s)
    s = re.sub(r'->w\b', '.w', s); s = re.sub(r'->h\b', '.h', s)
    s = re.sub(r'\bif \(grap\[t\]\[x1\]\)', 'if (grap[t][x1] != null)', s)
    s = re.sub(r'(otom\[\d+\]) = LoadMusicMem\([^)]*\);', '', s)
    s = re.sub(r'(oto\[\d+\]) = LoadSoundMem\([^)]*\);', '', s)
    s = re.sub(r'LoadGraph\("res/(\w+)\.PNG"\)', r'LoadGraph("\1")', s)
    s = re.sub(r'\bbyte\b', 'byte', s)
    s = re.sub(r'\bNULL\b', 'null', s)
    return s

logic = conv(logic); loadg_java = conv(loadg_java)

# function definitions -> static methods
logic = re.sub(r'(?m)^(void|int)\s*\n?\s*(\w+)\s*\(', r'static \1 \2(', logic)
# forward declarations in header removed when parsing header

# ---- header globals ----
fields = []
for line in header.split(';'):
    l = line.strip()
    l = re.sub(r'#[^\n]*\n?', '', l).strip()
    l = re.sub(r'using namespace std', '', l).strip()
    if not l: continue
    if '(' in l and not '=' in l: continue      # prototype
    if l.startswith('#'): continue
    m = re.match(r'(int|double|byte|string|long|Uint32|Img|SDL_Surface\s*\*|Mix_Music\s*\*|Mix_Chunk\s*\*)\s*(.*)', l, re.S)
    if not m: print('HDR?', repr(l)); continue
    typ, rest = m.group(1), m.group(2)
    typ = {'string': 'String', 'Uint32': 'int', 'long': 'long'}.get(typ, typ)
    if 'SDL_Surface' in typ: typ = 'Img'
    if 'Mix_' in typ: continue
    # split on commas not inside brackets
    parts = re.split(r',(?![^\[]*\])', rest)
    for p in parts:
        p = ' '.join(p.split())
        if 'SDL_Surface' in typ or typ == 'Img':
            p = p.lstrip('*').strip()
        mm = re.match(r'\*?\s*(\w+)((?:\[[^\]]+\])*)\s*(?:=\s*(.*))?$', p)
        if not mm: print('PART?', p); continue
        name, dims, init = mm.groups()
        name = {'fma': 'fmaZ', 'stime': 'stimeZ'}.get(name, name)
        if name in ('color','gfxcolor','mirror'): continue
        if dims:
            ds = re.findall(r'\[([^\]]+)\]', dims)
            fields.append('static %s%s %s = new %s%s;' % (typ, '[]' * len(ds), name, typ, ''.join('[%s]' % d for d in ds)))
        else:
            fields.append('static %s %s%s;' % (typ, name, (' = ' + init) if init else ''))
defines = re.findall(r'#define\s+(\w+)\s+(\d+)', header)
consts = ['static final int %s = %s;' % d for d in defines]

java = ('package syobon;\n\nfinal class G extends Rt {\n'
        + '\n'.join(consts) + '\n' + '\n'.join(fields) + '\n'
        + 'static byte[][] stagedatex;\n'
        + loadg_java + '\n' + logic + '\n}\n')
open(sys.argv[4], 'w', encoding='utf-8').write(java)
