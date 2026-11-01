package syobon;

import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;
import javax.microedition.rms.RecordStore;

/**
 * MIDP 1.0 main loop: the game draws into an offscreen view buffer, which the
 * canvas copies to the screen in paint(). Keys, pause menu and settings, no
 * rotation/stretch/touch pad (MIDP 1.0 has none of the calls behind them).
 */
final class Eng implements Runnable {
    static Eng inst;
    private static final int P_UP = 1, P_DOWN = 2, P_LEFT = 4, P_RIGHT = 8, P_FIRE = 16,
            P_0 = 32, P_STAR = 64, P_POUND = 128;

    private Canvas cv;
    private Image buf;
    private Graphics bufG;
    private volatile boolean running = true, sysPaused;
    private boolean menuOpen, needClear = true, loaded;
    private int menuSel;
    private final int[] heldCodes = new int[12], heldBits = new int[12];
    private int pressCount, waitMark, lastBits, edgeBits;
    private boolean softPressed;
    private String error;

    Eng() {
        inst = this;
    }

    void attach(Canvas c) {
        cv = c;
    }

    // ---------------- keys ----------------
    private int bitsFor(int kc, Canvas c) {
        switch (kc) {
            case Canvas.KEY_NUM2: return P_UP;
            case Canvas.KEY_NUM8: return P_DOWN;
            case Canvas.KEY_NUM4: return P_LEFT;
            case Canvas.KEY_NUM6: return P_RIGHT;
            case Canvas.KEY_NUM1: return P_UP | P_LEFT;
            case Canvas.KEY_NUM3: return P_UP | P_RIGHT;
            case Canvas.KEY_NUM7: return P_DOWN | P_LEFT;
            case Canvas.KEY_NUM9: return P_DOWN | P_RIGHT;
            case Canvas.KEY_NUM5: return P_FIRE;
            case Canvas.KEY_NUM0: return P_0;
            case Canvas.KEY_STAR: return P_STAR;
            case Canvas.KEY_POUND: return P_POUND;
        }
        int ga = 0;
        try { ga = c.getGameAction(kc); } catch (Throwable t) { }
        switch (ga) {
            case Canvas.UP: return P_UP;
            case Canvas.DOWN: return P_DOWN;
            case Canvas.LEFT: return P_LEFT;
            case Canvas.RIGHT: return P_RIGHT;
            case Canvas.FIRE: return P_FIRE;
        }
        return 0;
    }

    void keyPressed(int kc, Canvas c) {
        pressCount++;
        if (kc == -6 || kc == -7 || kc == -21 || kc == -22) { softPressed = true; return; }
        int b = bitsFor(kc, c);
        synchronized (heldCodes) {
            for (int i = 0; i < heldCodes.length; i++) if (heldCodes[i] == kc) return;
            for (int i = 0; i < heldCodes.length; i++) {
                if (heldCodes[i] == 0) { heldCodes[i] = kc; heldBits[i] = b; edgeBits |= b; return; }
            }
        }
    }

    void keyReleased(int kc) {
        synchronized (heldCodes) {
            for (int i = 0; i < heldCodes.length; i++) if (heldCodes[i] == kc) { heldCodes[i] = 0; heldBits[i] = 0; }
        }
    }

    // a tap acts as a press of the centre key (starts the game, picks menu rows)
    void pointerPressed(int x, int y) {
        pressCount++;
        synchronized (heldCodes) { edgeBits |= P_FIRE; }
    }

    void softKey() {
        pressCount++;
        softPressed = true;
    }

    private int heldNow() {
        int b = 0;
        synchronized (heldCodes) {
            for (int i = 0; i < heldCodes.length; i++) b |= heldBits[i];
            b |= edgeBits;
            edgeBits = 0;
        }
        return b;
    }

    private void clearKeys() {
        synchronized (heldCodes) {
            for (int i = 0; i < heldCodes.length; i++) { heldCodes[i] = 0; heldBits[i] = 0; }
            edgeBits = 0;
        }
    }

    // ---------------- loop ----------------
    public void run() {
        try {
            buf = Image.createImage(Rt.VW, Rt.VH);
            bufG = buf.getGraphics();
            loadSettings();
            Font.load();
            Rt.loadLang();
            G.loadg();
            if (Cfg.SOUND) {
                try {
                    Class.forName("javax.microedition.media.Manager");
                    Snd.init();
                    Rt.sndOk = true;
                } catch (Throwable t) { }
                if (Rt.sndOk) Snd.enabled = sndSetting;
            }
            loaded = true;
        } catch (Throwable t) {
            error = String.valueOf(t);
            cv.repaint();
            return;
        }
        System.gc();
        int skipped = 0;
        long next = System.currentTimeMillis();
        while (running) {
            if (sysPaused) {
                sleep(100);
                next = System.currentTimeMillis();
                continue;
            }
            int bits = heldNow();
            int newBits = bits & ~lastBits;
            lastBits = bits;

            if (softPressed) {
                softPressed = false;
                if (!menuOpen) openMenu(); else closeMenu();
                newBits = 0;
            }

            if (menuOpen) {
                menuInput(newBits);
                if (menuOpen) {
                    drawMenu(bufG);
                    show();
                }
                sleep(60);
                next = System.currentTimeMillis();
                continue;
            }

            if (Rt.waitKeyPending) {
                if (pressCount != waitMark) Rt.waitKeyPending = false;
                else {
                    sleep(30);
                    next = System.currentTimeMillis();
                    continue;
                }
            }

            Rt.kLeft = (bits & P_LEFT) != 0;
            Rt.kRight = (bits & P_RIGHT) != 0;
            Rt.kDown = (bits & P_DOWN) != 0;
            Rt.kUp = (bits & P_UP) != 0;
            Rt.kJump = (bits & (P_UP | P_FIRE)) != 0;
            Rt.kStart = (bits & P_FIRE) != 0;
            Rt.kSuicide = (bits & P_0) != 0;
            Rt.kTitle = (bits & P_STAR) != 0;
            Rt.kFast = (bits & P_POUND) != 0;
            G.titleNewBits = newBits;

            long now = System.currentTimeMillis();
            // slow phones: keep the game at full speed, skip up to 3 pictures in 4
            boolean skip = now > next + Rt.frameMs && skipped < 3;
            bufG.setClip(0, 0, Rt.VW, Rt.VH);
            Rt.g = bufG;
            Rt.skipDraw = skip;
            try {
                G.Mainprogram();
            } catch (RuntimeException e) {
                System.out.println(String.valueOf(e));
            }
            Rt.skipDraw = false;
            if (!skip) show();
            if (Rt.waitKeyPending) waitMark = pressCount;
            if (skip) skipped++; else skipped = 0;

            next += Rt.frameMs;
            now = System.currentTimeMillis();
            if (next > now) sleep((int) (next - now));
            else if (now - next > 500) next = now;
            else Thread.yield();
        }
    }

    private void show() {
        cv.repaint();
        cv.serviceRepaints();
    }

    void paint(Graphics g, int w, int h) {
        int vx = (w - Rt.VW) >> 1, vy = (h - Rt.VH) >> 1;
        if (vx < 0) vx = 0;
        if (vy < 0) vy = 0;
        g.setClip(0, 0, w, h);
        if (needClear || !loaded) {
            g.setColor(0);
            g.fillRect(0, 0, w, h);
            needClear = false;
        }
        if (!loaded) {
            g.setColor(0xFFFFFF);
            g.drawString(error != null ? error : "Loading...", 2, 2, Graphics.TOP | Graphics.LEFT);
            needClear = true;
            return;
        }
        g.drawImage(buf, vx, vy, Graphics.TOP | Graphics.LEFT);
    }

    void showNotify() { needClear = true; }

    void hideNotify() {
        if (running && loaded && !menuOpen) { menuOpen = true; menuSel = 0; }
        if (Cfg.SOUND) { if (Rt.sndOk) Snd.pauseAll(); }
    }

    void pauseFromSystem() {
        sysPaused = true;
        hideNotify();
    }

    void resumeFromSystem() {
        sysPaused = false;
        needClear = true;
    }

    void stop() { running = false; }

    private static void sleep(int ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { }
    }

    // ---------------- menu ----------------
    private static final int M_RESUME = 0, M_LANG = 1, M_SOUND = 2, M_MIX = 3, M_TITLE = 4, M_EXIT = 5;
    private int[] items() {
        if (Cfg.SOUND) {
            if (Rt.sndOk && !Snd.mixing && Snd.hasBgm) return new int[] { M_RESUME, M_LANG, M_SOUND, M_MIX, M_TITLE, M_EXIT };
            if (Rt.sndOk) return new int[] { M_RESUME, M_LANG, M_SOUND, M_TITLE, M_EXIT };
        }
        return new int[] { M_RESUME, M_LANG, M_TITLE, M_EXIT };
    }

    private void openMenu() {
        menuOpen = true;
        menuSel = 0;
        if (Cfg.SOUND) { if (Rt.sndOk) Snd.pauseAll(); }
    }

    private void closeMenu() {
        menuOpen = false;
        clearKeys();
        lastBits = 0;
        saveSettings();
        needClear = true;
        if (Cfg.SOUND) { if (Rt.sndOk) Snd.resume(); }
    }

    private String line(int id) {
        switch (id) {
            case M_RESUME: return "Resume";
            case M_LANG: return Rt.lang == Rt.EN ? "Language: English" : "Language: 日本語";
            case M_SOUND: if (Cfg.SOUND) return Snd.enabled ? "Sound: On" : "Sound: Off"; break;
            case M_MIX: if (Cfg.SOUND) return Snd.noMixMode == 0 ? "Prefer: music" : "Prefer: effects"; break;
            case M_TITLE: return "Title";
            case M_EXIT: return "Exit";
        }
        return "";
    }

    private void menuInput(int nb) {
        int[] it = items();
        int n = it.length;
        if (menuSel >= n) menuSel = 0;
        if ((nb & P_UP) != 0) menuSel = (menuSel + n - 1) % n;
        if ((nb & P_DOWN) != 0) menuSel = (menuSel + 1) % n;
        if ((nb & (P_LEFT | P_RIGHT | P_FIRE)) == 0) return;
        boolean fire = (nb & P_FIRE) != 0;
        switch (it[menuSel]) {
            case M_RESUME: if (fire) closeMenu(); break;
            case M_LANG: Rt.lang ^= 1; break;
            case M_SOUND: if (Cfg.SOUND) Snd.enabled = !Snd.enabled; break;
            case M_MIX: if (Cfg.SOUND) Snd.noMixMode ^= 1; break;
            case M_TITLE:
                if (fire) {
                    G.mainZ = 100;
                    G.maintm = 0;
                    G.ending = 0;
                    Rt.stopBgm();
                    closeMenu();
                }
                break;
            case M_EXIT: if (fire) { saveSettings(); Main.instance.quit(); } break;
        }
    }

    private void drawMenu(Graphics g) {
        int[] it = items();
        int n = it.length;
        int rh = Font.height() + 2, m = 2;
        int h = n * rh + rh + 8;
        int y0 = (Rt.VH - h) >> 1;
        if (y0 < 0) y0 = 0;
        g.setClip(0, 0, Rt.VW, Rt.VH);
        g.setColor(0);
        g.fillRect(m, y0, Rt.VW - 2 * m, h);
        g.setColor(0xFFFFFF);
        g.drawRect(m, y0, Rt.VW - 2 * m - 1, h - 1);
        Font.draw(g, "PAUSE", (Rt.VW - Font.width("PAUSE")) >> 1, y0 + 3, 0xFFFF80);
        for (int i = 0; i < n; i++) {
            int y = y0 + rh + 6 + i * rh;
            if (i == menuSel) {
                g.setColor(0x4060C0);
                g.fillRect(m + 2, y - 1, Rt.VW - 2 * m - 4, rh);
            }
            Font.draw(g, line(it[i]), m + 6, y, 0xFFFFFF);
        }
    }

    // ---------------- settings ----------------
    private boolean sndSetting = true;

    private void loadSettings() {
        String loc = null;
        try { loc = System.getProperty("microedition.locale"); } catch (Throwable t) { }
        Rt.lang = loc != null && loc.startsWith("ja") ? Rt.JA : Rt.EN;
        try {
            RecordStore rs = RecordStore.openRecordStore("syobon1", true);
            if (rs.getNumRecords() > 0) {
                byte[] d = rs.getRecord(1);
                if (d != null && d.length >= 3) {
                    Rt.lang = d[0] & 1;
                    sndSetting = d[1] != 0;
                    if (Cfg.SOUND) Snd.noMixMode = d[2] & 1;
                }
            }
            rs.closeRecordStore();
        } catch (Throwable t) { }
    }

    private void saveSettings() {
        try {
            boolean snd = sndSetting;
            int mix = 0;
            if (Cfg.SOUND) { if (Rt.sndOk) snd = Snd.enabled; mix = Snd.noMixMode; }
            byte[] d = { (byte) Rt.lang, (byte) (snd ? 1 : 0), (byte) mix };
            RecordStore rs = RecordStore.openRecordStore("syobon1", true);
            if (rs.getNumRecords() > 0) rs.setRecord(1, d, 0, d.length);
            else rs.addRecord(d, 0, d.length);
            rs.closeRecordStore();
        } catch (Throwable t) { }
    }
}
