package syobon;

import com.nokia.mid.ui.FullCanvas;
import javax.microedition.lcdui.Graphics;

/** Full-screen canvas for Nokia phones (Nokia UI API); loaded by name. */
public final class CvN extends FullCanvas {
    public CvN() { }

    protected void paint(Graphics g) { Eng.inst.paint(g, getWidth(), getHeight()); }
    public void keyPressed(int k) { Eng.inst.keyPressed(k, this); }
    public void keyReleased(int k) { Eng.inst.keyReleased(k); }
    public void pointerPressed(int x, int y) { Eng.inst.pointerPressed(x, y); }
    public void showNotify() { Eng.inst.showNotify(); }
    public void hideNotify() { Eng.inst.hideNotify(); }
}
