package syobon;

import java.io.DataInputStream;
import java.io.InputStream;
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
    static final int VW = 240, VH = 210;

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
        g.fillRect(a >> 1, b >> 1, 1, 1);
    }

    static void drawline(int a, int b, int c, int d) {
        if (skipDraw) return;
        g.setColor(color);
        g.drawLine(a >> 1, b >> 1, c >> 1, d >> 1);
    }

    static void drawrect(int a, int b, int c, int d) {
        if (skipDraw) return;
        g.setColor(color);
        int x0 = a >> 1, y0 = b >> 1;
        g.drawRect(x0, y0, ((a + c - 1) >> 1) - x0, ((b + d - 1) >> 1) - y0);
    }

    static void fillrect(int a, int b, int c, int d) {
        if (skipDraw) return;
        if (c > 4000) c = 4000; // fill-the-screen calls pass 48000
        if (d > 4000) d = 4000;
        g.setColor(color);
        int x0 = a >> 1, y0 = b >> 1;
        g.fillRect(x0, y0, ((a + c) >> 1) - x0, ((b + d) >> 1) - y0);
    }

    static void drawarc(int a, int b, int c, int d) {
        if (skipDraw) return;
        g.setColor(color);
        g.drawArc((a - c) >> 1, (b - d) >> 1, c, d, 0, 360);
    }

    static void fillarc(int a, int b, int c, int d) {
        if (skipDraw) return;
        g.setColor(color);
        g.fillArc((a - c) >> 1, (b - d) >> 1, c + 1, d + 1, 0, 360);
    }

    // ---- images ----
    static Image[] sheets = new Image[10];
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
        }
        im.w = SHEET_W[id];
        im.h = SHEET_H[id];
        return im;
    }

    static final int[] SHEET_W = { 169, 302, 268, 263, 298, 234, 265, 304, 378 };
    static final int[] SHEET_H = { 44, 105, 103, 49, 248, 103, 200, 132, 56 };

    static Img DerivationGraph(int x, int y, int w, int h, Img src) {
        Img im = new Img();
        im.sheet = src.sheet;
        im.w = w;
        im.h = h;
        im.hx = x >> 1;
        im.hy = y >> 1;
        im.hw = ((x + w + 1) >> 1) - im.hx;
        im.hh = ((y + h + 1) >> 1) - im.hy;
        if (recordOnly) {
            StringBuffer sb = new StringBuffer("RECT ");
            sb.append(im.sheet).append(' ').append(x).append(' ').append(y).append(' ').append(w).append(' ').append(h);
            System.out.println(sb.toString());
        }
        return im;
    }

    static void drawimage(Img m, int a, int b) {
        if (skipDraw || m == null) return;
        g.drawRegion(sheets[m.sheet], m.hx, m.hy, m.hw, m.hh,
                mirror == 1 ? Sprite.TRANS_MIRROR : Sprite.TRANS_NONE,
                a >> 1, b >> 1, Graphics.TOP | Graphics.LEFT);
    }

    // upside-down draw centred on (a,b)
    static void DrawVertTurnGraph(int a, int b, Img m) {
        if (skipDraw || m == null) return;
        g.drawRegion(sheets[m.sheet], m.hx, m.hy, m.hw, m.hh, Sprite.TRANS_MIRROR_ROT180,
                (a >> 1) - (m.hw >> 1), (b >> 1) - (m.hh >> 1), Graphics.TOP | Graphics.LEFT);
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
        // The original centres most text by hand assuming 20px glyphs on a
        // 480px screen; recognise that and re-centre for our font.
        int ow = 0;
        for (int i = 0; i < s.length(); i++) ow += s.charAt(i) < 0x100 || (s.charAt(i) >= 0xFF61 && s.charAt(i) <= 0xFF9F) ? 10 : 20;
        int x = a >> 1;
        int cx = a + ow / 2 - 240;
        if (!noCentre && cx > -24 && cx < 24 && ow > 40) x = (VW - Font.width(s)) >> 1;
        int y = b >> 1;
        if (fontType == DX_FONTTYPE_EDGE) {
            for (int dy = -1; dy <= 1; dy++)
                for (int dx = -1; dx <= 1; dx++)
                    if (dx != 0 || dy != 0) Font.draw(g, s, x + dx, y + dy, 0);
        }
        Font.draw(g, s, x, y, color);
    }

    static void DrawString(int a, int b, String s, int c) {
        int old = color;
        color = c;
        str(s, a, b);
        color = old;
    }

    static void txmsg(String x, int a) {
        // message box text; box widened to the full view, see ttmsg patch
        if (skipDraw) return;
        Font.draw(g, x, 7, 22 + a * 12, color);
    }

    // ---- sound ----
    static void ot(int n) { Snd.se(n); }
    static void bgmchange(int n) { Snd.bgm(n); }
    static void stopBgm() { Snd.haltBgm(); }
    static void stopAllSe() { Snd.stopSe(); }
    static void StopSoundMem(int n) { Snd.stopSe(n); }

    // Class literals (X.class) are not valid in pre-1.5 class files, so look
    // resources up through the MIDlet instance's class.
    static InputStream res(String name) {
        return Main.instance.getClass().getResourceAsStream(name);
    }

    // ---- stage data ----
    static byte[][] loadStage(int n) {
        byte[][] d = new byte[17][1001];
        try {
            InputStream in = res(new StringBuffer("/st/s").append(n).append(".bin").toString());
            DataInputStream din = new DataInputStream(in);
            for (int r = 0; r < 17; r++) din.readFully(d[r]);
            din.close();
        } catch (Exception e) {
            throw new RuntimeException("stage");
        }
        return d;
    }
}
