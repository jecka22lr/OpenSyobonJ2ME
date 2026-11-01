package syobon;

import java.io.DataInputStream;
import javax.microedition.lcdui.Graphics;

/**
 * 10/12px bitmap font (Sazanami Gothic glyphs, pre-rendered at build time) drawn
 * as horizontal pixel runs, so any colour costs no extra memory.
 * File layout: size, n, char[n], width[n], spanStart[n+1], spans (y,x,len bytes).
 */
final class Font {
    private static char[] chars;
    private static byte[] widths;
    private static int[] start;
    private static byte[] spans;
    private static int size = 12;

    static int height() { return size; }

    static void load() throws Exception {
        DataInputStream in = new DataInputStream(Rt.res("/f.bin"));
        size = in.readUnsignedByte();
        int n = in.readUnsignedShort();
        chars = new char[n];
        widths = new byte[n];
        start = new int[n + 1];
        for (int i = 0; i < n; i++) chars[i] = in.readChar();
        in.readFully(widths);
        for (int i = 0; i <= n; i++) start[i] = in.readInt();
        spans = new byte[start[n] * 3];
        in.readFully(spans);
        in.close();
    }

    private static int find(char c) {
        int lo = 0, hi = chars.length - 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            char m = chars[mid];
            if (m < c) lo = mid + 1;
            else if (m > c) hi = mid - 1;
            else return mid;
        }
        return -1;
    }

    private static int adv(char c, int idx) {
        if (idx >= 0) return widths[idx];
        if (c < 0x100 || (c >= 0xFF61 && c <= 0xFF9F)) return size / 2;
        return size;
    }

    static int width(String s) {
        if (chars == null) return s.length() * 6;
        int w = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            w += adv(c, find(c));
        }
        return w;
    }

    static void draw(Graphics g, String s, int x, int y, int color) {
        if (chars == null) { // before load: fall back to the system font
            g.setColor(color);
            g.drawString(s, x, y, Graphics.TOP | Graphics.LEFT);
            return;
        }
        g.setColor(color);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            int idx = find(c);
            if (idx >= 0) {
                int e = start[idx + 1] * 3;
                for (int p = start[idx] * 3; p < e; p += 3) {
                    g.fillRect(x + spans[p + 1], y + spans[p], spans[p + 2], 1);
                }
            }
            x += adv(c, idx);
        }
    }
}
