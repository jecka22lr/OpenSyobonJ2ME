# Build res/en.bin from tools/en.txt: every Japanese literal in G.java whose
# trimmed text has an entry gets the English text, wearing the literal's own
# leading/trailing padding. Output: count, then (key, value) pairs written
# like DataOutputStream.writeUTF (2-byte length + UTF-8).
import sys, re, struct
table = {}
for l in open(sys.argv[1], encoding='utf-8'):
    l = l.rstrip('\n')
    if not l or l.startswith('#'): continue
    jp, en = l.split('\t')
    table[jp.strip(' 　')] = en
src = open(sys.argv[2], encoding='utf-8').read()
pairs = {}
missing = []
for lit in re.findall(r'"((?:[^"\\]|\\.)*)"', src):
    if not any(ord(c) > 127 for c in lit) or lit in pairs: continue
    core = lit.strip(' 　')
    if core not in table:
        if any('぀' <= c <= '鿿' for c in core): missing.append(lit)
        continue
    lead = lit[:len(lit) - len(lit.lstrip(' 　'))].replace('　', '  ')
    trail = lit[len(lit.rstrip(' 　')):].replace('　', '  ')
    en = table[core]
    pairs[lit] = lead + en.strip() + trail if en.strip() else en
if missing:
    print('untranslated:', missing)
def utf(s):
    b = s.encode('utf-8'); return struct.pack('>H', len(b)) + b
out = struct.pack('>H', len(pairs)) + b''.join(utf(k) + utf(v) for k, v in pairs.items())
open(sys.argv[3], 'wb').write(out)
print(len(pairs), 'translated strings')
