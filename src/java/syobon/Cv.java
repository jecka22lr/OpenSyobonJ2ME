package syobon;

import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;
import javax.microedition.lcdui.game.GameCanvas;
import javax.microedition.lcdui.game.Sprite;
import javax.microedition.rms.RecordStore;

/**
 * Game canvas: owns the main loop, key mapping, orientation and the pause menu.
 */
final class Cv extends GameCanvas implements Runnable {
    // physical input bits
    private static final int P_UP = 1, P_DOWN = 2, P_LEFT = 4, P_RIGHT = 8, P_FIRE = 16,
            P_0 = 32, P_STAR = 64, P_POUND = 128;

    // orientation: 0 auto, 1 portrait, 2 landscape (keypad right), 3 landscape (keypad left)
    static int orient = 0;
    private int rot; // 0 none, 2 = content rotated clockwise, 3 = anticlockwise

    private volatile boolean running = true;
    private volatile boolean sysPaused;
    private boolean menuOpen;
    private int menuSel;
    private boolean needClear = true;
    private Image buf;
    private Graphics bufG;

    private final int[] heldCodes = new int[12];
    private final int[] heldBits = new int[12];
    private int pressCount, waitMark;
    private int lastBits, edgeBits;
    private boolean softPressed;
    private int sw, sh;
    static int errCount;

    Cv() {
        super(false);
        setFullScreenMode(true);
    }

    // ---------------- input ----------------
    private int bitsFor(int kc) {
        switch (kc) {
            case KEY_NUM2: return P_UP;
            case KEY_NUM8: return P_DOWN;
            case KEY_NUM4: return P_LEFT;
            case KEY_NUM6: return P_RIGHT;
            case KEY_NUM1: return P_UP | P_LEFT;
            case KEY_NUM3: return P_UP | P_RIGHT;
            case KEY_NUM7: return P_DOWN | P_LEFT;
            case KEY_NUM9: return P_DOWN | P_RIGHT;
            case KEY_NUM5: return P_FIRE;
            case KEY_NUM0: return P_0;
            case KEY_STAR: return P_STAR;
            case KEY_POUND: return P_POUND;
        }
        if (kc == -6 || kc == -7 || kc == -21 || kc == -22) return 0; // soft keys
        int ga = 0;
        try { ga = getGameAction(kc); } catch (Throwable t) { }
        switch (ga) {
            case UP: return P_UP;
            case DOWN: return P_DOWN;
            case LEFT: return P_LEFT;
            case RIGHT: return P_RIGHT;
            case FIRE: return P_FIRE;
        }
        return 0;
    }

    public void keyPressed(int kc) {
        pressCount++;
        if (kc == -6 || kc == -7 || kc == -21 || kc == -22) {
            softPressed = true;
            return;
        }
        int b = bitsFor(kc);
        synchronized (heldCodes) {
            for (int i = 0; i < heldCodes.length; i++) if (heldCodes[i] == kc) return;
            for (int i = 0; i < heldCodes.length; i++) {
                if (heldCodes[i] == 0) { heldCodes[i] = kc; heldBits[i] = b; edgeBits |= b; return; }
            }
        }
    }

    public void keyReleased(int kc) {
        synchronized (heldCodes) {
            for (int i = 0; i < heldCodes.length; i++) if (heldCodes[i] == kc) { heldCodes[i] = 0; heldBits[i] = 0; }
        }
    }

    private int heldNow() {
        int b = 0;
        synchronized (heldCodes) {
            for (int i = 0; i < heldCodes.length; i++) b |= heldBits[i];
            b |= edgeBits; // a press+release inside one frame still counts once
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

    // rotate physical direction bits into game directions
    private int toGame(int b) {
        if (rot == 0) return b;
        int u = b & P_UP, d = b & P_DOWN, l = b & P_LEFT, r = b & P_RIGHT;
        int o = b & ~(P_UP | P_DOWN | P_LEFT | P_RIGHT);
        if (rot == 2) { // phone turned anticlockwise, keypad on the right
            if (u != 0) o |= P_LEFT;
            if (r != 0) o |= P_UP;
            if (d != 0) o |= P_RIGHT;
            if (l != 0) o |= P_DOWN;
        } else {
            if (u != 0) o |= P_RIGHT;
            if (l != 0) o |= P_UP;
            if (d != 0) o |= P_LEFT;
            if (r != 0) o |= P_DOWN;
        }
        return o;
    }

    // ---------------- layout ----------------
    private void layout() {
        sw = getWidth();
        sh = getHeight();
        int r;
        if (orient == 2) r = 2;
        else if (orient == 3) r = 3;
        else r = 0;
        if (r != rot) needClear = true;
        rot = r;
        if (rot != 0 && buf == null) {
            buf = Image.createImage(Rt.VW, Rt.VH);
            bufG = buf.getGraphics();
            bufG.setColor(0);
            bufG.fillRect(0, 0, Rt.VW, Rt.VH);
        }
        if (rot == 0 && buf != null) { buf = null; bufG = null; }
    }

    public void sizeChanged(int w, int h) {
        needClear = true;
    }

    // ---------------- loop ----------------
    public void run() {
        Graphics sg = getGraphics();
        sg.setColor(0);
        sg.fillRect(0, 0, getWidth(), getHeight());
        Font.draw(sg, "Loading...", 4, 4, 0xFFFFFF);
        flushGraphics();
        try {
            loadSettings();
            Font.load();
            G.loadg();
            Snd.init();
        } catch (Throwable t) {
            sg.setColor(0);
            sg.fillRect(0, 0, getWidth(), getHeight());
            sg.setColor(0xFFFFFF);
            sg.drawString(String.valueOf(t), 2, 2, Graphics.TOP | Graphics.LEFT);
            flushGraphics();
            return;
        }
        System.gc();
        layout();
        debugHook();
        int skipped = 0;
        long next = System.currentTimeMillis();
        while (running) {
            if (sysPaused) {
                sleep(100);
                next = System.currentTimeMillis();
                continue;
            }
            if (getWidth() != sw || getHeight() != sh) needClear = true;
            layout();
            int bits = heldNow();
            int newBits = bits & ~lastBits;
            lastBits = bits;

            if (softPressed) {
                softPressed = false;
                if (!menuOpen) openMenu(); else closeMenu();
                newBits = 0;
            }

            if (menuOpen) {
                menuInput(toGame(newBits));
                drawFrame(sg, false, true);
                sleep(50);
                next = System.currentTimeMillis();
                continue;
            }

            if (Rt.waitKeyPending) {
                if (pressCount != waitMark && newBits != 0) {
                    Rt.waitKeyPending = false;
                } else {
                    sleep(30);
                    next = System.currentTimeMillis();
                    continue;
                }
            }

            int gb = toGame(bits);
            Rt.kLeft = (gb & P_LEFT) != 0;
            Rt.kRight = (gb & P_RIGHT) != 0;
            Rt.kDown = (gb & P_DOWN) != 0;
            Rt.kUp = (gb & P_UP) != 0;
            Rt.kJump = (gb & (P_UP | P_FIRE)) != 0;
            Rt.kStart = (gb & P_FIRE) != 0;
            Rt.kSuicide = (gb & P_0) != 0;
            Rt.kTitle = (gb & P_STAR) != 0;
            Rt.kFast = (gb & P_POUND) != 0;
            G.titleNewBits = toGame(newBits);

            long now = System.currentTimeMillis();
            boolean skip = now > next + Rt.frameMs && skipped < 2;
            drawFrame(sg, skip, false);
            if (Rt.waitKeyPending) waitMark = pressCount;
            if (skip) skipped++; else skipped = 0;

            next += Rt.frameMs;
            now = System.currentTimeMillis();
            if (next > now) sleep((int) (next - now));
            else if (now - next > 500) next = now; // fell far behind, resync
            else Thread.yield();
        }
    }

    // desktop testing only: -Dsyobon.dbg=ending | msgN
    private void debugHook() {
        String d = null;
        try { d = System.getProperty("syobon.dbg"); } catch (Throwable t) { }
        if (d == null) return;
        if (d.equals("ending")) { G.ending = 1; G.maintm = 0; }
        if (d.startsWith("msg")) { dbgMsg = Integer.parseInt(d.substring(3)); }
    }
    private int dbgMsg = -1;

    private void drawFrame(Graphics sg, boolean skip, boolean menuOnly) {
        int vx, vy;
        if (needClear) {
            sg.setClip(0, 0, sw, sh);
            sg.setColor(0);
            sg.fillRect(0, 0, sw, sh);
            needClear = false;
        }
        if (rot == 0) {
            vx = (sw - Rt.VW) >> 1;
            vy = (sh - Rt.VH) >> 1;
            if (vx < 0) vx = 0;
            if (vy < 0) vy = 0;
            sg.translate(vx, vy);
            sg.setClip(0, 0, Rt.VW, Rt.VH);
            Rt.g = sg;
        } else {
            vx = (sw - Rt.VH) >> 1;
            vy = (sh - Rt.VW) >> 1;
            if (vx < 0) vx = 0;
            if (vy < 0) vy = 0;
            bufG.setClip(0, 0, Rt.VW, Rt.VH);
            Rt.g = bufG;
        }
        Rt.skipDraw = skip;
        if (!menuOnly) {
            try {
                if (dbgMsg >= 0 && G.mainZ == 1 && G.zxon > 0) {
                    G.tmsgtype = 1; G.tmsgtm = 15; G.tmsgy = 300 + dbgMsg - 1; G.tmsg = dbgMsg; dbgMsg = -1;
                }
                G.Mainprogram();
            } catch (RuntimeException e) {
                // the C original silently tolerated stray reads; keep going
                errCount++;
                System.out.println(String.valueOf(e));
                e.printStackTrace();
            }
        }
        Rt.skipDraw = false;
        if (menuOpen) drawMenu(Rt.g);
        if (rot == 0) {
            sg.translate(-sg.getTranslateX(), -sg.getTranslateY());
        } else if (!skip) {
            sg.setClip(0, 0, sw, sh);
            sg.drawRegion(buf, 0, 0, Rt.VW, Rt.VH, rot == 2 ? Sprite.TRANS_ROT90 : Sprite.TRANS_ROT270,
                    vx, vy, Graphics.TOP | Graphics.LEFT);
        }
        if (!skip) flushGraphics();
    }

    // ---------------- menu ----------------
    private static final String[] ORIENT_NAMES = { "Auto", "Portrait", "Landscape (keys right)", "Landscape (keys left)" };

    private int menuCount() { return 6; }

    private void openMenu() {
        menuOpen = true;
        menuSel = 0;
        Snd.pauseAll();
    }

    private void closeMenu() {
        menuOpen = false;
        clearKeys();
        lastBits = 0;
        saveSettings();
        needClear = true;
        Snd.resume();
    }

    private String menuLine(int i) {
        switch (i) {
            case 0: return "Resume";
            case 1: return cat("Screen: ", ORIENT_NAMES[orient]);
            case 2: return cat("Sound: ", Snd.enabled ? "On" : "Off");
            case 3:
                if (Snd.mixing) return "Mixing: supported";
                return cat("No mixing: ", Snd.noMixMode == 0 ? "music first" : "effects first");
            case 4: return "Back to title";
            case 5: return "Exit";
        }
        return "";
    }

    private static String cat(String a, String b) {
        return new StringBuffer(a).append(b).toString();
    }

    private void menuInput(int nb) {
        if ((nb & P_UP) != 0) menuSel = (menuSel + menuCount() - 1) % menuCount();
        if ((nb & P_DOWN) != 0) menuSel = (menuSel + 1) % menuCount();
        int dir = 0;
        if ((nb & P_LEFT) != 0) dir = -1;
        if ((nb & (P_RIGHT | P_FIRE)) != 0) dir = 1;
        if (dir == 0) return;
        switch (menuSel) {
            case 0: if ((nb & P_FIRE) != 0) closeMenu(); break;
            case 1:
                orient = (orient + 4 + dir) % 4;
                layout();
                needClear = true;
                break;
            case 2: Snd.enabled = !Snd.enabled; break;
            case 3: if (!Snd.mixing) Snd.noMixMode ^= 1; break;
            case 4:
                if ((nb & P_FIRE) != 0) {
                    G.mainZ = 100;
                    G.maintm = 0;
                    G.ending = 0;
                    Snd.haltBgm();
                    closeMenu();
                }
                break;
            case 5: if ((nb & P_FIRE) != 0) { saveSettings(); Main.instance.quit(); } break;
        }
    }

    private void drawMenu(Graphics g) {
        int n = menuCount();
        int h = n * 14 + 22;
        int y0 = (Rt.VH - h) >> 1;
        g.setClip(0, 0, Rt.VW, Rt.VH);
        g.setColor(0);
        g.fillRect(8, y0, Rt.VW - 16, h);
        g.setColor(0xFFFFFF);
        g.drawRect(8, y0, Rt.VW - 17, h - 1);
        Font.draw(g, "PAUSE", (Rt.VW - Font.width("PAUSE")) >> 1, y0 + 4, 0xFFFF80);
        for (int i = 0; i < n; i++) {
            int y = y0 + 20 + i * 14;
            if (i == menuSel) {
                g.setColor(0x4060C0);
                g.fillRect(12, y - 1, Rt.VW - 24, 14);
            }
            Font.draw(g, menuLine(i), 16, y, 0xFFFFFF);
        }
    }

    // ---------------- system pause ----------------
    public void hideNotify() {
        if (!menuOpen && running) {
            menuOpen = true;
            menuSel = 0;
        }
        Snd.pauseAll();
    }

    public void showNotify() {
        needClear = true;
    }

    void pauseFromSystem() {
        sysPaused = true;
        if (!menuOpen) { menuOpen = true; menuSel = 0; }
        Snd.pauseAll();
    }

    void resumeFromSystem() {
        sysPaused = false;
        needClear = true;
    }

    void stop() {
        running = false;
    }

    private static void sleep(int ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { }
    }

    // ---------------- settings ----------------
    private void loadSettings() {
        try {
            RecordStore rs = RecordStore.openRecordStore("syobon", true);
            if (rs.getNumRecords() > 0) {
                byte[] d = rs.getRecord(1);
                if (d != null && d.length >= 3) {
                    orient = d[0] & 3;
                    Snd.enabled = d[1] != 0;
                    Snd.noMixMode = d[2] & 1;
                }
            }
            rs.closeRecordStore();
        } catch (Throwable t) { }
    }

    private void saveSettings() {
        try {
            byte[] d = { (byte) orient, (byte) (Snd.enabled ? 1 : 0), (byte) Snd.noMixMode };
            RecordStore rs = RecordStore.openRecordStore("syobon", true);
            if (rs.getNumRecords() > 0) rs.setRecord(1, d, 0, d.length);
            else rs.addRecord(d, 0, d.length);
            rs.closeRecordStore();
        } catch (Throwable t) { }
    }
}
