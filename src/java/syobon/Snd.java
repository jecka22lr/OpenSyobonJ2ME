package syobon;

import java.io.InputStream;
import javax.microedition.media.Manager;
import javax.microedition.media.Player;
import javax.microedition.media.control.VolumeControl;

/**
 * WAV playback through MMAPI. Many Series 40 phones can only play one sampled
 * sound at a time ("supports.mixing" false); then BGM wins and only the big
 * jingles (which stop the music in the original anyway) get through, unless
 * the player picks "SE only" in the options.
 */
final class Snd {
    static boolean enabled = true;
    static boolean mixing;
    static int noMixMode; // 0 = music + jingles, 1 = sound effects only
    static boolean hasBgm = true;

    private static Player bgmP;
    private static int bgmCur;
    private static final Player[] seP = new Player[19];
    private static Player seBig;
    private static final long[] seUse = new long[19];
    private static long useClock;
    private static final int CACHE = 4;

    // SE ids that interrupt the music in the original game
    private static boolean isJingle(int n) {
        return n == 11 || n == 12 || n == 16 || n == 17;
    }

    // short effects are kept realized so they play without a load hitch
    private static boolean isSmall(int n) {
        return !(n == 11 || n == 12 || n == 16 || n == 17);
    }

    static void init() {
        String m = System.getProperty("supports.mixing");
        mixing = m != null && m.equals("true");
        hasBgm = Rt.res("/a/b1.wav") != null;
    }

    private static Player make(String name) {
        try {
            InputStream in = Rt.res(name);
            if (in == null) return null;
            Player p = Manager.createPlayer(in, "audio/x-wav");
            p.realize();
            p.prefetch();
            return p;
        } catch (Throwable t) {
            return null;
        }
    }

    private static void vol(Player p, int v) {
        try {
            VolumeControl vc = (VolumeControl) p.getControl("VolumeControl");
            if (vc != null) vc.setLevel(v);
        } catch (Throwable t) { }
    }

    private static String nm(String pre, int n) {
        return new StringBuffer("/a/").append(pre).append(n).append(".wav").toString();
    }

    static void bgm(int n) {
        stopBgm();
        bgmCur = n;
        if (!enabled || !hasBgm) return;
        if (!mixing && noMixMode == 1) return;
        bgmP = make(nm("b", n));
        if (bgmP == null) return;
        try {
            bgmP.setLoopCount(-1);
            vol(bgmP, n == 2 ? 70 : 85);
            bgmP.start();
        } catch (Throwable t) { }
    }

    static void haltBgm() {
        bgmCur = 0;
        stopBgm();
    }

    static void stopBgm() {
        if (bgmP != null) {
            try { bgmP.close(); } catch (Throwable t) { }
            bgmP = null;
        }
    }

    static void se(int n) {
        if (!enabled || n <= 0 || n >= 19) return;
        if (!mixing) {
            if (noMixMode == 0 && hasBgm) {
                if (!isJingle(n)) return;
                stopBgm();
            }
        }
        try {
            if (isSmall(n)) {
                if (seP[n] == null) {
                    // keep at most CACHE realized players; S40 limits them
                    int cnt = 0, oldest = -1; long ot = Long.MAX_VALUE;
                    for (int i = 0; i < 19; i++) if (seP[i] != null) { cnt++; if (seUse[i] < ot) { ot = seUse[i]; oldest = i; } }
                    if (cnt >= CACHE && oldest >= 0) { try { seP[oldest].close(); } catch (Throwable t) { } seP[oldest] = null; }
                    seP[n] = make(nm("s", n));
                }
                seUse[n] = ++useClock;
                Player p = seP[n];
                if (p == null) return;
                p.stop();
                p.setMediaTime(0);
                p.start();
            } else {
                if (seBig != null) { try { seBig.close(); } catch (Throwable t) { } }
                seBig = make(nm("s", n));
                if (seBig != null) seBig.start();
            }
        } catch (Throwable t) { }
    }

    static void stopSe(int n) {
        try {
            if (n > 0 && n < 19 && seP[n] != null) seP[n].stop();
            if (isJingle(n) && seBig != null) { seBig.close(); seBig = null; }
        } catch (Throwable t) { }
    }

    static void stopSe() {
        for (int i = 0; i < 19; i++) {
            if (seP[i] != null) { try { seP[i].stop(); } catch (Throwable t) { } }
        }
        if (seBig != null) { try { seBig.close(); } catch (Throwable t) { } seBig = null; }
    }

    static void pauseAll() {
        stopSe();
        stopBgm();
    }

    static void resume() {
        if (bgmCur != 0) bgm(bgmCur);
    }

    static void releaseAll() {
        stopSe();
        stopBgm();
        for (int i = 0; i < 19; i++) {
            if (seP[i] != null) { try { seP[i].close(); } catch (Throwable t) { } seP[i] = null; }
        }
    }
}
