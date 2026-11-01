# Hand fixes applied on top of the mechanical conversion.
import sys, re
p = sys.argv[1]; s = open(p, encoding='utf-8').read()
def rep(a, b, count=1):
    global s
    n = s.count(a)
    if n != count: raise SystemExit('patch mismatch (%d): %r' % (n, a[:60]))
    s = s.replace(a, b)

rep('mgrap[t] = 0;', 'mgrap[t] = null;')
# background sizes are indexed by ntype, which reaches 100..102 for text
# markers; C read past the end harmlessly, Java needs the room
rep('static int[] ne = new int[nmax];', 'static int[] ne = new int[256];')
rep('static int[] nf = new int[nmax];', 'static int[] nf = new int[256];')
rep('''	DrawFormatString(230, 200, GetColor(255, 255, 255), " × %d",
			 nokori);''', '''	DrawFormatString(230, 200, GetColor(255, 255, 255), new StringBuffer(" × ").append(nokori).toString());''')
rep('''	str("TODO: Fix this encoding error...",240-6*20/2,xx[14]/100);\n''', '')
# title screen: phone keys
rep('''	str("Enterキーを押せ!!", 240 - 8 * 20 / 2, 250);''',
    '''	if (lang == EN) str("Syobon Action", 240 - 13 * 10 / 2, 60 + 60);
	if (touchUi) str("ジャンプをタッチ!!", 240 - 9 * 20 / 2, titleY(0));
	else str("5キーを押せ!!", 240 - 7 * 20 / 2, titleY(0));
	str(titleStageName(), 240 - 8 * 20 / 2, titleY(1));
	str("←→ ステージ選択", 240 - 8 * 20 / 2, titleY(2));''')
rep('''	if (key(KEY_INPUT_RETURN)) {
	    xx[0] = 1;
	}''', '''	titleSelect();
	if (key(KEY_INPUT_RETURN)) {
	    xx[0] = 1;
	}''')
# message box: use the full view width so 12px text fits
rep('fillrect(xx[1], xx[2], 360, tmsgy / 100);', 'fillrect(4, xx[2], 472, tmsgy / 100);')
rep('drawrect(xx[1], xx[2], 360, tmsgy / 100);', 'drawrect(4, xx[2], 472, tmsgy / 100);')
rep('fillrect(xx[1], xx[2] + tmsgy / 100, 360, xx[5]);', 'fillrect(4, xx[2] + tmsgy / 100, 472, xx[5]);')
rep('drawrect(xx[1], xx[2] + tmsgy / 100, 360, xx[5]);', 'drawrect(4, xx[2] + tmsgy / 100, 472, xx[5]);')

s = re.sub(r'Math\.abs\(ma <\s*xx\[8\] - anobia\[t\] \+\s*xx\[0\] \* 2\) < 3000', '(ma < xx[8] - anobia[t] + xx[0] * 2 || true) /* original: abs(bool) < 3000, always true */', s)
# The message text is only drawn on the single frame before WaitKey(); keep
# the full box and text up while the game waits for a key (the SDL port
# showed an empty box here).
rep('''    if (tmsgtype == 1 || tmsgtype == 2) {
	setc0();
	fillrect(4, xx[2], 472, tmsgy / 100);''', '''    boolean holdMsg = tmsgtype == 3 && tmsgtm >= 15;
    if (holdMsg) {
	setc0();
	fillrect(4, xx[2], 472, ((15 - 1) * 1200 + 1500) / 100);
	setc1();
	drawrect(4, xx[2], 472, ((15 - 1) * 1200 + 1500) / 100);
    }
    if (tmsgtype == 1 || tmsgtype == 2) {
	setc0();
	fillrect(4, xx[2], 472, tmsgy / 100);''')
rep('''    if (tmsgtype == 2) {

	setfont(20, 5);''', '''    if (tmsgtype == 2 || holdMsg) {
	setc1();
	setfont(20, 5);''')
rep('''    if (tmsgtype == 3) {
	xx[5] =''', '''    if (tmsgtype == 3 && !holdMsg) {
	xx[5] =''')
# route the message box through Rt.msgRect/msgFlush (layout per screen size)
s = re.sub(r'fillrect\(4, ([^,]+), 472, ', r'msgRect(true, \1, ', s)
s = re.sub(r'drawrect\(4, ([^,]+), 472, ', r'msgRect(false, \1, ', s)
i = s.index('static void ttmsg()'); i = s.index('{', i) + 1; depth = 1
while depth:
    if s[i] == '{': depth += 1
    elif s[i] == '}': depth -= 1
    i += 1
s = s[:i - 1] + '    msgFlush();\n' + s[i - 1:]
# no floating point (CLDC 1.0): the rotating fire bars use integer trig
s = re.sub(r'(-?tt \* xx\[26\]) \* Math\.(cos|sin)\(atm\[t\] \* pai / 180 / 2\)', r'f\2(\1, atm[t])', s)
rep('static double pai = 3.1415926535;\n', '')
rep('static double[] xd = new double[11];', 'static int[] xd = new int[11];')
if 'Math.sin' in s or 'Math.cos' in s or 'double' in s: raise SystemExit('floating point left in G.java')
extra = '''
static int titleNewBits;
static int titleSel;
static final String[] TITLE_ST = { "1-1", "1-2", "1-3", "1-4", "2-1", "2-2", "2-3", "2-4", "3-1", "?" };

static String titleStageName() {
    if (titleSel == 9) return "ステージ ？？？";
    return new StringBuffer(tr("ステージ ")).append(TITLE_ST[titleSel]).toString();
}

// replaces the PC number-key stage select: left/right cycles, 5 starts
static void titleSelect() {
    if ((titleNewBits & 4) != 0) titleSel = (titleSel + 9) % 10;
    if ((titleNewBits & 8) != 0) titleSel = (titleSel + 1) % 10;
    over = 0;
    if (titleSel < 9) {
        sta = TITLE_ST[titleSel].charAt(0) - '0';
        stb = TITLE_ST[titleSel].charAt(2) - '0';
        stc = 0;
    } else {
        sta = 1; stb = 1; stc = 0;
        over = 1;
    }
}
'''
s = s[:s.rindex('}')] + extra + '}\n'
open(p, 'w', encoding='utf-8').write(s)
