package syobon;

import java.io.DataInputStream;
import java.io.InputStream;
import java.util.Hashtable;
import java.util.Random;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;
import javax.microedition.lcdui.game.Sprite;

/**
 * Runtime layer standing in for the DxLib/SDL calls the original game makes.
 * Game code works in the original 480x420 pixel space; everything here draws
 * at half scale into a 240x210 view.
 */
class Rt {
    // reduction factor from the original 480x420 (2, 3 or 4), set per build in Cfg
    static final int K = Cfg.K;
    static final int VW = 480 / K, VH = 420 / K;
    static final boolean SMALL = K > 2;

    // original pixels -> view pixels, rounding down (also for negatives)
    static int sc(int x) {
        return x >= 0 ? x / K : -((-x + K - 1) / K);
    }

    // rounding up
    static int scu(int x) {
        return -sc(-x);
    }

    // ---- key ids used by the converted game code ----
    static final int KEY_INPUT_ESCAPE = 1, KEY_INPUT_LEFT = 2, KEY_INPUT_RIGHT = 3,
            KEY_INPUT_DOWN = 4, KEY_INPUT_UP = 5, KEY_INPUT_F1 = 6, KEY_INPUT_O = 7,
            KEY_INPUT_Z = 8, KEY_INPUT_RETURN = 9, KEY_INPUT_SPACE = 10,
            KEY_INPUT_1 = 11, KEY_INPUT_2 = 12, KEY_INPUT_3 = 13, KEY_INPUT_4 = 14,
            KEY_INPUT_5 = 15, KEY_INPUT_6 = 16, KEY_INPUT_7 = 17, KEY_INPUT_8 = 18,
            KEY_INPUT_9 = 19, KEY_INPUT_0 = 20;
    static final int DX_FONTTYPE_NORMAL = 0, DX_FONTTYPE_EDGE = 1;
    static final boolean TRUE = true;

    // game-level input state, filled by Cv each frame
    static boolean touchUi; // touch pad on screen: title says "tap" instead of "press 5"
    static boolean kLeft, kRight, kUp, kDown, kJump, kStart, kSuicide, kTitle, kFast;
    static boolean waitKeyPending;
    static int frameMs = 33;

    static boolean key(int k) {
        switch (k) {
            case KEY_INPUT_LEFT: return kLeft;
            case KEY_INPUT_RIGHT: return kRight;
            case KEY_INPUT_DOWN: return kDown;
            case KEY_INPUT_UP: return kUp || kJump;
            case KEY_INPUT_Z: return kJump;
            case KEY_INPUT_RETURN: return kStart;
            case KEY_INPUT_O: return kSuicide;
            case KEY_INPUT_F1: return kTitle;
            case KEY_INPUT_SPACE: return kFast;
        }
        return false; // number keys on the title are handled by the stage menu
    }

    static void ScreenFlip() { }

    static void WaitKey() { waitKeyPending = true; }

    static long GetNowCount() { return System.currentTimeMillis(); }

    static void wait2(long s, long e, int ft) { frameMs = ft; }

    static void end() { Main.instance.quit(); }

    static final Random rng = new Random();

    static int rnd(int n) {
        if (n <= 0) return 0;
        return (rng.nextInt() & 0x7fffffff) % n;
    }

    // ---- drawing ----
    static Graphics g;
    static boolean skipDraw;
    static int color;
    static int mirror;
    static int fontType;

    static int GetColor(int r, int gg, int b) { return (r << 16) | (gg << 8) | b; }

    static void setcolor(int r, int gg, int b) { color = (r << 16) | (gg << 8) | b; }
    static void setc0() { color = 0; }
    static void setc1() { color = 0xFFFFFF; }

    static void FillScreen() {
        if (skipDraw) return;
        g.setColor(color);
        g.fillRect(0, 0, VW, VH);
    }

    static void drawpixel(int a, int b) {
        if (skipDraw) return;
        g.setColor(color);
        g.fillRect(sc(a), sc(b), 1, 1);
    }

    static void drawline(int a, int b, int c, int d) {
        if (skipDraw) return;
        g.setColor(color);
        g.drawLine(sc(a), sc(b), sc(c), sc(d));
    }

    static void drawrect(int a, int b, int c, int d) {
        if (skipDraw) return;
        g.setColor(color);
        int x0 = sc(a), y0 = sc(b);
        g.drawRect(x0, y0, sc(a + c - 1) - x0, sc(b + d - 1) - y0);
    }

    static void fillrect(int a, int b, int c, int d) {
        if (skipDraw) return;
        if (c > 4000) c = 4000; // fill-the-screen calls pass 48000
        if (d > 4000) d = 4000;
        g.setColor(color);
        int x0 = sc(a), y0 = sc(b);
        g.fillRect(x0, y0, sc(a + c) - x0, sc(b + d) - y0);
    }

    static void drawarc(int a, int b, int c, int d) {
        if (skipDraw) return;
        g.setColor(color);
        int x0 = sc(a - c), y0 = sc(b - d);
        g.drawArc(x0, y0, sc(a + c) - x0, sc(b + d) - y0, 0, 360);
    }

    static void fillarc(int a, int b, int c, int d) {
        if (skipDraw) return;
        g.setColor(color);
        int x0 = sc(a - c), y0 = sc(b - d);
        g.fillArc(x0, y0, sc(a + c) - x0 + 1, sc(b + d) - y0 + 1, 0, 360);
    }

    // ---- images ----
    static Image[] sheets = new Image[10];
    // MIDP 1.0 has no drawRegion/transforms: pre-flipped copies of the sheets
    static Image[] sheetsM = new Image[10], sheetsV = new Image[10];
    static String[] sheetNames = { "player", "brock", "item", "teki", "haikei", "brock2", "omake", "omake2", "syobon3" };
    static boolean recordOnly; // build-time: record sprite rects without loading images

    static Img LoadGraph(String name) {
        int id = 0;
        while (!sheetNames[id].equals(name)) id++;
        Img im = new Img();
        im.sheet = id;
        if (!recordOnly) {
            try {
                sheets[id] = Image.createImage(new StringBuffer("/g/").append(name).append(".png").toString());
            } catch (Exception e) {
                throw new RuntimeException(name);
            }
            im.hw = sheets[id].getWidth();
            im.hh = sheets[id].getHeight();
            if (Cfg.MIDP1) { // flipped copies exist for all but the background and logo; upside-down only for enemies
                if (id != 4 && id != 8) sheetsM[id] = optImage(new StringBuffer("/g/").append(name).append("_m.png").toString());
                if (id == 3) sheetsV[id] = optImage("/g/teki_v.png");
            }
        }
        im.w = SHEET_W[id];
        im.h = SHEET_H[id];
        return im;
    }

    private static Image optImage(String path) {
        try { return Image.createImage(path); } catch (Exception e) { return null; }
    }

    static final int[] SHEET_W = { 169, 302, 268, 263, 298, 234, 265, 304, 378 };
    static final int[] SHEET_H = { 44, 105, 103, 49, 248, 103, 200, 132, 56 };

    static Img DerivationGraph(int x, int y, int w, int h, Img src) {
        Img im = new Img();
        im.sheet = src.sheet;
        im.w = w;
        im.h = h;
        im.hx = sc(x);
        im.hy = sc(y);
        im.hw = scu(x + w) - im.hx;
        im.hh = scu(y + h) - im.hy;
        if (recordOnly) {
            StringBuffer sb = new StringBuffer("RECT ");
            sb.append(im.sheet).append(' ').append(x).append(' ').append(y).append(' ').append(w).append(' ').append(h);
            System.out.println(sb.toString());
        }
        return im;
    }

    static void drawimage(Img m, int a, int b) {
        if (skipDraw || m == null) return;
        if (Cfg.MIDP1) {
            Image src = sheets[m.sheet];
            int sx = m.hx;
            if (mirror == 1 && sheetsM[m.sheet] != null) {
                src = sheetsM[m.sheet];
                sx = src.getWidth() - m.hx - m.hw;
            }
            blit(src, sx, m.hy, m.hw, m.hh, sc(a), sc(b));
            return;
        }
        g.drawRegion(sheets[m.sheet], m.hx, m.hy, m.hw, m.hh,
                mirror == 1 ? Sprite.TRANS_MIRROR : Sprite.TRANS_NONE,
                sc(a), sc(b), Graphics.TOP | Graphics.LEFT);
    }

    // upside-down draw centred on (a,b)
    static void DrawVertTurnGraph(int a, int b, Img m) {
        if (skipDraw || m == null) return;
        int dx = sc(a) - (m.hw >> 1), dy = sc(b) - (m.hh >> 1);
        if (Cfg.MIDP1) {
            Image src = sheetsV[m.sheet];
            if (src == null) { blit(sheets[m.sheet], m.hx, m.hy, m.hw, m.hh, dx, dy); return; }
            blit(src, m.hx, src.getHeight() - m.hy - m.hh, m.hw, m.hh, dx, dy);
            return;
        }
        g.drawRegion(sheets[m.sheet], m.hx, m.hy, m.hw, m.hh, Sprite.TRANS_MIRROR_ROT180,
                dx, dy, Graphics.TOP | Graphics.LEFT);
    }

    // MIDP 1.0 sprite draw: clip to the sprite, draw the whole sheet offset.
    // Rt.g is the offscreen view buffer here, so the normal clip is the view.
    private static void blit(Image img, int sx, int sy, int w, int h, int dx, int dy) {
        int x0 = dx < 0 ? 0 : dx, y0 = dy < 0 ? 0 : dy;
        int x1 = dx + w > VW ? VW : dx + w, y1 = dy + h > VH ? VH : dy + h;
        if (x1 <= x0 || y1 <= y0) return;
        g.setClip(x0, y0, x1 - x0, y1 - y0);
        g.drawImage(img, dx - sx, dy - sy, Graphics.TOP | Graphics.LEFT);
        g.setClip(0, 0, VW, VH);
    }

    // integer sin/cos for CLDC 1.0: m * cos(a/2 degrees), truncated like (int)
    static int fcos(int m, int a) { return m * cosHalfDeg(a) / 16384; }
    static int fsin(int m, int a) { return m * cosHalfDeg(a - 180) / 16384; }

    private static int cosHalfDeg(int a) {
        a %= 720;
        if (a < 0) a += 720;
        // Tab.COS holds cos for 0..90 degrees in half-degree steps, scaled by 16384
        if (a <= 180) return Tab.COS.charAt(a);
        if (a <= 360) return -Tab.COS.charAt(360 - a);
        if (a <= 540) return -Tab.COS.charAt(a - 360);
        return Tab.COS.charAt(720 - a);
    }

    // ---- language ----
    static final int JA = 0, EN = 1;
    static int lang = JA;
    private static Hashtable en;

    static void loadLang() {
        en = new Hashtable();
        try {
            DataInputStream in = new DataInputStream(res("/en.bin"));
            int n = in.readUnsignedShort();
            for (int i = 0; i < n; i++) {
                String k = in.readUTF();
                en.put(k, in.readUTF());
            }
            in.close();
        } catch (Exception e) { }
    }

    static String tr(String s) {
        if (lang != EN || en == null || s == null) return s;
        Object o = en.get(s);
        return o != null ? (String) o : s;
    }

    // ---- text ----
    static void ChangeFontType(int t) { fontType = t; }
    static void setfont(int a, int b) { }
    static void setfont(int a) { }

    static boolean noCentre;

    static void DrawFormatString(int a, int b, int c, String s) {
        int old = color;
        color = c;
        noCentre = true;
        str(s, a, b);
        noCentre = false;
        color = old;
    }

    static void str(String s, int a, int b) {
        if (skipDraw || s == null || s.length() == 0) return;
        String orig = s;
        s = tr(s);
        // The original centres most text by hand assuming 20px glyphs on a
        // 480px screen; recognise that and re-centre for our font.
        int ow = 0;
        for (int i = 0; i < orig.length(); i++) {
            char c = orig.charAt(i);
            ow += c < 0x100 || (c >= 0xFF61 && c <= 0xFF9F) ? 10 : 20;
        }
        int x = sc(a);
        int cx = a + ow / 2 - 240;
        if (!noCentre && cx > -24 && cx < 24 && ow > 40) x = (VW - Font.width(s)) >> 1;
        int y = sc(b);
        if (fontType == DX_FONTTYPE_EDGE) {
            for (int dy = -1; dy <= 1; dy++)
                for (int dx = -1; dx <= 1; dx++)
                    if (dx != 0 || dy != 0) Font.draw(g, s, x + dx, y + dy, 0);
        }
        Font.draw(g, s, x, y, color);
    }

    // title screen text rows, in original coordinates, spaced for our font
    static int titleY(int i) {
        if (!SMALL) return 250 + i * 32;
        return (VH / 2 - 3 + i * (Font.height() + 1)) * K;
    }

    static void DrawString(int a, int b, String s, int c) {
        int old = color;
        color = c;
        str(s, a, b);
        color = old;
    }

    // ---- hint-block message box ----
    // The box is drawn relative to the view rather than through the game's
    // scale, so its text stays readable on every build.
    static void msgRect(boolean fill, int y, int h) {
        if (skipDraw) return;
        if (!SMALL) { // 240 wide: the full-width box from the first build
            if (fill) fillrect(4, y, 472, h); else drawrect(4, y, 472, h);
            return;
        }
        int full = VH - 4;                       // box height when fully open
        int y0 = 2 + (y - 40) * full / 183;
        int hh = h * full / 183;
        g.setColor(color);
        if (fill) g.fillRect(1, y0, VW - 2, hh);
        else g.drawRect(1, y0, VW - 3, hh - 1);
    }

    private static final String[] msgLines = new String[12];
    private static final int[] msgRows = new int[12];
    private static int msgN;

    static void txmsg(String x, int a) {
        if (skipDraw) return;
        x = tr(x);
        if (!compactMsg()) {
            Font.draw(g, x, 7, 22 + a * 12, color);
            return;
        }
        if (msgN < msgLines.length) { msgLines[msgN] = x; msgRows[msgN] = a; msgN++; }
    }

    // Small screens: drop the space padding, wrap long lines, and keep the
    // right-aligned signatures ("ちく より") on the right.
    static boolean compactMsg() {
        return SMALL || lang == EN;
    }

    static void msgFlush() {
        if (!compactMsg() || skipDraw) { msgN = 0; return; }
        int lh = Font.height() + 1;
        int maxW = VW - (SMALL ? 8 : 16);
        int y = SMALL ? 4 : 24;
        int left = SMALL ? 4 : 8;
        for (int i = 0; i < msgN; i++) {
            String s = msgLines[i];
            int lead = 0;
            while (lead < s.length() && (s.charAt(lead) == ' ' || s.charAt(lead) == '\u3000')) lead++;
            int end = s.length();
            while (end > lead && (s.charAt(end - 1) == ' ' || s.charAt(end - 1) == '\u3000')) end--;
            if (end <= lead) continue;
            s = s.substring(lead, end);
            boolean right = lead >= 12;
            while (s.length() > 0) {
                int n = s.length();
                while (n > 1 && Font.width(s.substring(0, n)) > maxW) n--;
                if (n < s.length()) { // prefer breaking at a space (English)
                    int sp = s.lastIndexOf(' ', n);
                    if (sp > 0) n = sp;
                }
                String part = s.substring(0, n);
                int x = right ? VW - left - Font.width(part) : left;
                Font.draw(g, part, x, y, color);
                y += lh;
                s = s.substring(n);
                while (s.length() > 0 && s.charAt(0) == ' ') s = s.substring(1);
            }
        }
        msgN = 0;
    }

    // ---- sound ----
    // Snd needs MMAPI; builds without sound never reference it, and MIDP 1.0
    // builds only touch it once Snd.init found MMAPI on the phone
    static boolean sndOk;
    static void ot(int n) { if (Cfg.SOUND) { if (sndOk) Snd.se(n); } }
    static void bgmchange(int n) { if (Cfg.SOUND) { if (sndOk) Snd.bgm(n); } }
    static void stopBgm() { if (Cfg.SOUND) { if (sndOk) Snd.haltBgm(); } }
    static void stopAllSe() { if (Cfg.SOUND) { if (sndOk) Snd.stopSe(); } }
    static void StopSoundMem(int n) { if (Cfg.SOUND) { if (sndOk) Snd.stopSe(n); } }

    // Class literals (X.class) are not valid in pre-1.5 class files, so look
    // resources up through the MIDlet instance's class.
    static InputStream res(String name) {
        return Main.instance.getClass().getResourceAsStream(name);
    }

    // ---- stage data ----
    static byte[][] loadStage(int n) {
        byte[][] d = new byte[17][1001];
        try {
            // all stages live in one file (fewer jar entries, better compression)
            DataInputStream din = new DataInputStream(res("/st.bin"));
            int skip = n * 17 * 1001;
            while (skip > 0) {
                int k = (int) din.skip(skip);
                if (k <= 0) { din.readByte(); k = 1; }
                skip -= k;
            }
            for (int r = 0; r < 17; r++) din.readFully(d[r]);
            din.close();
        } catch (Exception e) {
            throw new RuntimeException("stage");
        }
        return d;
    }
}
