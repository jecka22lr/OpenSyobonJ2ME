package syobon;

import javax.microedition.lcdui.Display;
import javax.microedition.midlet.MIDlet;

public final class Main extends MIDlet {
    static Main instance;
    private Cv cv;

    public Main() {
        instance = this;
    }

    protected void startApp() {
        if (cv == null) {
            cv = new Cv();
            Display.getDisplay(this).setCurrent(cv);
            new Thread(cv).start();
        } else {
            Display.getDisplay(this).setCurrent(cv);
            cv.resumeFromSystem();
        }
    }

    protected void pauseApp() {
        if (cv != null) cv.pauseFromSystem();
    }

    protected void destroyApp(boolean unconditional) {
        if (cv != null) cv.stop();
        Snd.releaseAll();
    }

    void quit() {
        destroyApp(true);
        notifyDestroyed();
    }
}
