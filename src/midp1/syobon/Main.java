package syobon;

import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Display;
import javax.microedition.midlet.MIDlet;

/** MIDP 1.0 entry point: uses Nokia's FullCanvas when the phone has it. */
public final class Main extends MIDlet {
    static Main instance;
    private Canvas cv;
    private Eng eng;

    public Main() {
        instance = this;
    }

    protected void startApp() {
        if (cv == null) {
            eng = new Eng();
            try {
                Class.forName("com.nokia.mid.ui.FullCanvas");
                cv = (Canvas) Class.forName("syobon.CvN").newInstance();
            } catch (Throwable t) {
                cv = new Cv1();
            }
            eng.attach(cv);
            Display.getDisplay(this).setCurrent(cv);
            new Thread(eng).start();
        } else {
            Display.getDisplay(this).setCurrent(cv);
            eng.resumeFromSystem();
        }
    }

    protected void pauseApp() {
        if (eng != null) eng.pauseFromSystem();
    }

    protected void destroyApp(boolean unconditional) {
        if (eng != null) eng.stop();
        if (Cfg.SOUND) { if (Rt.sndOk) Snd.releaseAll(); }
    }

    void quit() {
        destroyApp(true);
        notifyDestroyed();
    }
}
