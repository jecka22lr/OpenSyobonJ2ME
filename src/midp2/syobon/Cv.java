package syobon;

import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;
import javax.microedition.lcdui.game.GameCanvas;
import javax.microedition.lcdui.game.Sprite;
import javax.microedition.rms.RecordStore;

/**
 * Game canvas: main loop, keys and touch pad, orientation, stretching and the
 * pause menu.
 */
final class Cv extends GameCanvas implements Runnable {
    // physical input bits
    private static final int P_UP = 1, P_DOWN = 2, P_LEFT = 4, P_RIGHT = 8, P_FIRE = 16,
            P_0 = 32, P_STAR = 64, P_POUND = 128;
    private static final int PAUSE = -1;

    // settings
    static int orient = 0;   // 0 auto, 1 portrait, 2 landscape (keys right), 3 landscape (keys left)
    static int stretch = 0;  // 0 auto, 1 off, 2 on
    static int touch = 0;    // 0 auto, 1 on, 2 off

    // layout, recomputed by layout()
    private int rot;             // 0 none, 2 content turned clockwise, 3 anticlockwise
    private int sw, sh;          // physical screen
    private int uw, uh;          // view size after scaling, before rotation
    private int px0, py0;        // top-left of the view on screen
    private int pw, ph;          // view size on screen (after rotation)
    private boolean scaled;
    private boolean pad;         // touch pad shown
    private int padY;            // top of the touch pad area

    private Image buf;
    private Graphics bufG;
    private int[] srcPix, rowBuf, colIdx, rowIdx;
    private static final int CHUNK = 8;

    private volatile boolean running = true;
    private volatile boolean sysPaused;
    private boolean menuOpen;
    private int menuSel;
    private boolean needClear = true;
    private boolean padDirty = true;

    private final int[] heldCodes = new int[12];
    private final int[] heldBits = new int[12];
    private int touchBits;
    private int pressCount, waitMark;
    private int lastBits, edgeBits;
    private boolean softPressed;
    private int tapX = -1, tapY;
    static int errCount;

    Cv() {
        super(false);
        setFullScreenMode(true);
    }

    // ---------------- keys ----------------
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
        if (isSoft(kc)) return 0;
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

    private static boolean isSoft(int kc) {
        return kc == -6 || kc == -7 || kc == -21 || kc == -22;
    }

    public void keyPressed(int kc) {
        pressCount++;
        if (isSoft(kc)) { softPressed = true; return; }
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
            b |= edgeBits | touchBits; // a press+release inside one frame still counts once
            edgeBits = 0;
        }
        return b;
    }

    private void clearKeys() {
        synchronized (heldCodes) {
            for (int i = 0; i < heldCodes.length; i++) { heldCodes[i] = 0; heldBits[i] = 0; }
            edgeBits = 0;
            touchBits = 0;
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

    // ---------------- touch ----------------
    // Pad layout (fractions of the pad area):
    //   left 55%:  upper 40% = jump-left / jump-right, lower 60% = left / right
    //   right 45%: upper 65% = jump, lower 35% = down | pause
    private int zone(int x, int y) {
        if (!pad || y < padY) return 0;
        int w = sw, h = sh - padY;
        int rx = x * 100 / w, ry = (y - padY) * 100 / h;
        if (rx < 55) {
            boolean leftCol = rx < 27;
            if (ry < 40) return leftCol ? (P_LEFT | P_FIRE) : (P_RIGHT | P_FIRE);
            return leftCol ? P_LEFT : P_RIGHT;
        }
        if (ry < 65) return P_FIRE;
        return rx < 80 ? P_DOWN : PAUSE;
    }

    public void pointerPressed(int x, int y) {
        pressCount++;
        int z = zone(x, y);
        if (z == PAUSE) { softPressed = true; return; }
        if (z == 0) { // tap on the picture: menu rows, or start on the title
            tapX = x;
            tapY = y;
            return;
        }
        synchronized (heldCodes) { touchBits = z; edgeBits |= z; }
        padDirty = true;
    }

    public void pointerDragged(int x, int y) {
        int z = zone(x, y);
        if (z == PAUSE) z = 0;
        synchronized (heldCodes) {
            if (z != touchBits) { touchBits = z; edgeBits |= z; padDirty = true; }
        }
    }

    public void pointerReleased(int x, int y) {
        synchronized (heldCodes) { touchBits = 0; }
        padDirty = true;
    }

    // screen point -> view coordinates (x in [0,VW), y in [0,VH)), or null
    private int[] toView(int x, int y) {
        int dx = x - px0, dy = y - py0;
        if (dx < 0 || dy < 0 || dx >= pw || dy >= ph) return null;
        int vx, vy;
        if (rot == 0) {
            vx = dx * Rt.VW / uw;
            vy = dy * Rt.VH / uh;
        } else {
            int rx = dx * Rt.VH / uh, ry = dy * Rt.VW / uw;
            if (rot == 2) { vx = ry; vy = Rt.VH - 1 - rx; }
            else { vx = Rt.VW - 1 - ry; vy = rx; }
        }
        return new int[] { vx, vy };
    }

    private void drawPad(Graphics g) {
        if (!pad) return;
        int w = sw, h = sh - padY;
        g.setClip(0, padY, sw, h);
        g.setColor(0x101820);
        g.fillRect(0, padY, sw, h);
        int tb = touchBits;
        int c1 = w * 27 / 100, c2 = w * 55 / 100, c3 = w * 80 / 100;
        int r1 = padY + h * 40 / 100, r2 = padY + h * 65 / 100;
        btn(g, 2, padY + 2, c1 - 4, r1 - padY - 4, tb == (P_LEFT | P_FIRE), 5);
        btn(g, c1 + 2, padY + 2, c2 - c1 - 4, r1 - padY - 4, tb == (P_RIGHT | P_FIRE), 6);
        btn(g, 2, r1 + 2, c1 - 4, sh - r1 - 4, tb == P_LEFT, 1);
        btn(g, c1 + 2, r1 + 2, c2 - c1 - 4, sh - r1 - 4, tb == P_RIGHT, 2);
        btn(g, c2 + 2, padY + 2, w - c2 - 4, r2 - padY - 4, tb == P_FIRE, 0);
        btn(g, c2 + 2, r2 + 2, c3 - c2 - 4, sh - r2 - 4, tb == P_DOWN, 3);
        btn(g, c3 + 2, r2 + 2, w - c3 - 4, sh - r2 - 4, false, 4);
        padDirty = false;
    }

    // icon: 0 jump, 1 left, 2 right, 3 down, 4 pause, 5 jump-left, 6 jump-right
    private void btn(Graphics g, int x, int y, int w, int h, boolean down, int icon) {
        g.setColor(down ? 0x5070D0 : 0x2A3A50);
        g.fillRoundRect(x, y, w, h, 10, 10);
        g.setColor(0x8090A8);
        g.drawRoundRect(x, y, w - 1, h - 1, 10, 10);
        int cx = x + w / 2, cy = y + h / 2;
        int s = Math.min(w, h) / 4;
        if (s < 4) s = 4;
        g.setColor(0xFFFFFF);
        switch (icon) {
            case 0: {
                String t = Rt.lang == Rt.EN ? "JUMP" : "ジャンプ";
                Font.draw(g, t, cx - Font.width(t) / 2, cy - Font.height() / 2, 0xFFFFFF);
                break;
            }
            case 1: g.fillTriangle(cx - s, cy, cx + s, cy - s, cx + s, cy + s); break;
            case 2: g.fillTriangle(cx + s, cy, cx - s, cy - s, cx - s, cy + s); break;
            case 3: g.fillTriangle(cx, cy + s, cx - s, cy - s, cx + s, cy - s); break;
            case 4:
                g.fillRect(cx - s / 2 - 2, cy - s / 2, 3, s);
                g.fillRect(cx + s / 2 - 1, cy - s / 2, 3, s);
                break;
            case 5:
                g.fillTriangle(cx - s, cy - s, cx + s / 2, cy - s, cx - s, cy + s / 2);
                break;
            case 6:
                g.fillTriangle(cx + s, cy - s, cx - s / 2, cy - s, cx + s, cy + s / 2);
                break;
        }
    }

    // ---------------- layout ----------------
    private void layout() {
        int nsw = getWidth(), nsh = getHeight();
        int r = orient == 2 ? 2 : orient == 3 ? 3 : 0;
        boolean wantPad = !Rt.SMALL && r == 0 && (touch == 1 || (touch == 0 && hasPointerEvents()))
                && nsh - Rt.VH >= 80;
        if (nsw == sw && nsh == sh && r == rot && wantPad == pad && !needClear) return;
        sw = nsw;
        sh = nsh;
        rot = r;
        pad = wantPad;
        Rt.touchUi = pad;
        int lw = rot == 0 ? sw : sh, lh = rot == 0 ? sh : sw; // screen as the picture sees it
        int padH = pad ? Math.max(90, lh * 30 / 100) : 0;
        int s = 1000;
        if (stretch != 1) {
            int fit = Math.min(lw * 1000 / Rt.VW, (lh - padH) * 1000 / Rt.VH);
            if (stretch == 2 ? fit > 1000 : fit >= 1400) s = fit;
        }
        uw = Rt.VW * s / 1000;
        uh = Rt.VH * s / 1000;
        scaled = s != 1000;
        pw = rot == 0 ? uw : uh;
        ph = rot == 0 ? uh : uw;
        px0 = Math.max(0, (sw - pw) >> 1);
        if (pad) {
            py0 = Math.max(0, (sh - padH - ph) >> 1);
            padY = Math.max(py0 + ph, sh - padH);
        } else {
            py0 = Math.max(0, (sh - ph) >> 1);
        }
        boolean needBuf = rot != 0 || scaled;
        if (needBuf && buf == null) {
            buf = Image.createImage(Rt.VW, Rt.VH);
            bufG = buf.getGraphics();
            bufG.setColor(0);
            bufG.fillRect(0, 0, Rt.VW, Rt.VH);
        } else if (!needBuf) {
            buf = null;
            bufG = null;
        }
        if (scaled) buildTables(); else { srcPix = null; rowBuf = null; colIdx = null; rowIdx = null; }
        needClear = true;
        padDirty = true;
    }

    // index tables so that screen pixel (dx,dy) of the view shows
    // srcPix[colIdx[dx] + rowIdx[dy]]
    private void buildTables() {
        int vw = Rt.VW, vh = Rt.VH;
        srcPix = null;
        System.gc();
        srcPix = new int[vw * vh];
        rowBuf = new int[pw * CHUNK];
        colIdx = new int[pw];
        rowIdx = new int[ph];
        for (int dx = 0; dx < pw; dx++) {
            if (rot == 0) colIdx[dx] = dx * vw / uw;
            else {
                int rx = dx * vh / uh;
                colIdx[dx] = rot == 2 ? (vh - 1 - rx) * vw : rx * vw;
            }
        }
        for (int dy = 0; dy < ph; dy++) {
            if (rot == 0) rowIdx[dy] = (dy * vh / uh) * vw;
            else {
                int ry = dy * vw / uw;
                rowIdx[dy] = rot == 2 ? ry : vw - 1 - ry;
            }
        }
    }

    private void blitScaled(Graphics sg) {
        buf.getRGB(srcPix, 0, Rt.VW, 0, 0, Rt.VW, Rt.VH);
        for (int y0 = 0; y0 < ph; y0 += CHUNK) {
            int rows = Math.min(CHUNK, ph - y0);
            int o = 0;
            for (int r = 0; r < rows; r++) {
                int base = rowIdx[y0 + r];
                for (int dx = 0; dx < pw; dx++) rowBuf[o++] = srcPix[base + colIdx[dx]];
            }
            sg.drawRGB(rowBuf, 0, pw, px0, py0 + y0, pw, rows, false);
        }
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
            Rt.loadLang();
            G.loadg();
            Snd.init();
            Rt.sndOk = true;
            dropMixIfNoMusic();
        } catch (Throwable t) {
            sg.setColor(0);
            sg.fillRect(0, 0, getWidth(), getHeight());
            sg.setColor(0xFFFFFF);
            sg.drawString(String.valueOf(t), 2, 2, Graphics.TOP | Graphics.LEFT);
            flushGraphics();
            return;
        }
        System.gc();
        needClear = true;
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
            layout();
            int bits = heldNow();
            int newBits = bits & ~lastBits;
            lastBits = bits;

            if (softPressed) {
                softPressed = false;
                if (!menuOpen) openMenu(); else closeMenu();
                newBits = 0;
            }

            int[] tap = null;
            if (tapX >= 0) {
                tap = toView(tapX, tapY);
                tapX = -1;
            }

            if (menuOpen) {
                if (tap != null) menuTap(tap[0], tap[1]);
                else menuInput(toGame(newBits));
                if (menuOpen) drawFrame(sg, false, true);
                sleep(50);
                next = System.currentTimeMillis();
                continue;
            }

            if (Rt.waitKeyPending) {
                if (pressCount != waitMark) {
                    Rt.waitKeyPending = false;
                } else {
                    if (padDirty) { drawPad(sg); flushGraphics(); }
                    sleep(30);
                    next = System.currentTimeMillis();
                    continue;
                }
            }

            // tapping the picture on the title or lives screen starts the game
            if (tap != null && G.mainZ == 100) newBits |= P_FIRE;

            int gb = toGame(bits | (tap != null && G.mainZ == 100 ? P_FIRE : 0));
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
        if (needClear) {
            sg.setClip(0, 0, sw, sh);
            sg.setColor(0);
            sg.fillRect(0, 0, sw, sh);
            needClear = false;
            padDirty = true;
        }
        boolean direct = rot == 0 && !scaled;
        if (direct) {
            sg.translate(px0, py0);
            sg.setClip(0, 0, Rt.VW, Rt.VH);
            Rt.g = sg;
        } else {
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
        if (direct) {
            sg.translate(-sg.getTranslateX(), -sg.getTranslateY());
        } else if (!skip) {
            sg.setClip(0, 0, sw, sh);
            if (scaled) blitScaled(sg);
            else sg.drawRegion(buf, 0, 0, Rt.VW, Rt.VH, rot == 2 ? Sprite.TRANS_ROT90 : Sprite.TRANS_ROT270,
                    px0, py0, Graphics.TOP | Graphics.LEFT);
        }
        if (!skip) {
            if (padDirty) drawPad(sg);
            flushGraphics();
        }
    }

    // ---------------- menu ----------------
    private static final int M_RESUME = 0, M_SCREEN = 1, M_STRETCH = 2, M_TOUCH = 3, M_LANG = 4,
            M_SOUND = 5, M_MIX = 6, M_TITLE = 7, M_EXIT = 8;
    // the 128/208 builds leave out stretch and touch so the menu fits
    // (the music/effects preference only matters when there is music)
    private static int[] MENU = Rt.SMALL
            ? new int[] { M_RESUME, M_SCREEN, M_LANG, M_SOUND, M_MIX, M_TITLE, M_EXIT }
            : new int[] { M_RESUME, M_SCREEN, M_STRETCH, M_TOUCH, M_LANG, M_SOUND, M_MIX, M_TITLE, M_EXIT };

    private static void dropMixIfNoMusic() {
        if (Snd.hasBgm) return;
        int[] m = new int[MENU.length - 1];
        int j = 0;
        for (int i = 0; i < MENU.length; i++) if (MENU[i] != M_MIX) m[j++] = MENU[i];
        MENU = m;
    }

    private static final String[] ORIENT_NAMES = Rt.SMALL
            ? new String[] { "Auto", "Portrait", "Land. keys R", "Land. keys L" }
            : new String[] { "Auto", "Portrait", "Landscape (keys right)", "Landscape (keys left)" };
    private static final String[] AUTO_OFF_ON = { "Auto", "Off", "On" };

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

    private String menuLine(int id) {
        switch (id) {
            case M_RESUME: return "Resume";
            case M_SCREEN: return cat("Screen: ", ORIENT_NAMES[orient]);
            case M_STRETCH: return cat("Stretch: ", AUTO_OFF_ON[stretch]);
            case M_TOUCH: return cat("Touch pad: ", AUTO_OFF_ON[touch == 0 ? 0 : touch == 1 ? 2 : 1]);
            case M_LANG: return Rt.lang == Rt.EN ? "Language: English" : "Language: 日本語";
            case M_SOUND: return cat("Sound: ", Snd.enabled ? "On" : "Off");
            case M_MIX:
                if (Snd.mixing) return Rt.SMALL ? "Mixing: yes" : "Mixing: supported";
                if (Rt.SMALL) return cat("Prefer: ", Snd.noMixMode == 0 ? "music" : "effects");
                return cat("No mixing: ", Snd.noMixMode == 0 ? "music first" : "effects first");
            case M_TITLE: return Rt.SMALL ? "Title" : "Back to title";
            case M_EXIT: return "Exit";
        }
        return "";
    }

    private static String cat(String a, String b) {
        return new StringBuffer(a).append(b).toString();
    }

    private void menuInput(int nb) {
        int n = MENU.length;
        if ((nb & P_UP) != 0) menuSel = (menuSel + n - 1) % n;
        if ((nb & P_DOWN) != 0) menuSel = (menuSel + 1) % n;
        int dir = 0;
        if ((nb & P_LEFT) != 0) dir = -1;
        if ((nb & (P_RIGHT | P_FIRE)) != 0) dir = 1;
        if (dir != 0) menuAct(MENU[menuSel], dir, (nb & P_FIRE) != 0);
    }

    private void menuAct(int id, int dir, boolean fire) {
        switch (id) {
            case M_RESUME: if (fire) closeMenu(); break;
            case M_SCREEN:
                orient = (orient + 4 + dir) % 4;
                needClear = true;
                layout();
                break;
            case M_STRETCH:
                stretch = (stretch + 3 + dir) % 3;
                needClear = true;
                layout();
                break;
            case M_TOUCH:
                touch = (touch + 3 + dir) % 3;
                needClear = true;
                layout();
                break;
            case M_LANG: Rt.lang ^= 1; padDirty = true; break;
            case M_SOUND: Snd.enabled = !Snd.enabled; break;
            case M_MIX: if (!Snd.mixing) Snd.noMixMode ^= 1; break;
            case M_TITLE:
                if (fire) {
                    G.mainZ = 100;
                    G.maintm = 0;
                    G.ending = 0;
                    Snd.haltBgm();
                    closeMenu();
                }
                break;
            case M_EXIT: if (fire) { saveSettings(); Main.instance.quit(); } break;
        }
    }

    // menu geometry, shared by drawing and tapping
    private int rowH() { return Font.height() + 2; }
    private int menuM() { return Rt.SMALL ? 2 : 8; }
    private int menuH() { return MENU.length * rowH() + rowH() + 8; }
    private int menuY0() { return Math.max(0, (Rt.VH - menuH()) >> 1); }

    private void menuTap(int vx, int vy) {
        int m = menuM(), y0 = menuY0();
        if (vx < m || vx >= Rt.VW - m || vy < y0 || vy >= y0 + menuH()) { closeMenu(); return; }
        int i = (vy - (y0 + rowH() + 5)) / rowH();
        if (i < 0 || i >= MENU.length) return;
        menuSel = i;
        menuAct(MENU[i], 1, true);
    }

    private void drawMenu(Graphics g) {
        int n = MENU.length;
        int rh = rowH(), m = menuM(), h = menuH(), y0 = menuY0();
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
            Font.draw(g, menuLine(MENU[i]), m + 6, y, 0xFFFFFF);
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
        String loc = null;
        try { loc = System.getProperty("microedition.locale"); } catch (Throwable t) { }
        Rt.lang = loc != null && loc.startsWith("ja") ? Rt.JA : Rt.EN;
        try {
            RecordStore rs = RecordStore.openRecordStore("syobon", true);
            if (rs.getNumRecords() > 0) {
                byte[] d = rs.getRecord(1);
                if (d != null && d.length >= 3) {
                    orient = d[0] & 3;
                    Snd.enabled = d[1] != 0;
                    Snd.noMixMode = d[2] & 1;
                }
                if (d != null && d.length >= 6) {
                    Rt.lang = d[3] & 1;
                    stretch = (d[4] & 0xFF) % 3;
                    touch = (d[5] & 0xFF) % 3;
                }
            }
            rs.closeRecordStore();
        } catch (Throwable t) { }
    }

    private void saveSettings() {
        try {
            byte[] d = { (byte) orient, (byte) (Snd.enabled ? 1 : 0), (byte) Snd.noMixMode,
                    (byte) Rt.lang, (byte) stretch, (byte) touch };
            RecordStore rs = RecordStore.openRecordStore("syobon", true);
            if (rs.getNumRecords() > 0) rs.setRecord(1, d, 0, d.length);
            else rs.addRecord(d, 0, d.length);
            rs.closeRecordStore();
        } catch (Throwable t) { }
    }
}
