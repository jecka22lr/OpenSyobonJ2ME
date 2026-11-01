package syobon;

import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Graphics;

/** Plain MIDP 1.0 canvas; the pause menu sits behind a "Menu" command. */
final class Cv1 extends Canvas implements CommandListener {
    Cv1() {
        addCommand(new Command("Menu", Command.SCREEN, 1));
        setCommandListener(this);
    }

    public void commandAction(Command c, Displayable d) { Eng.inst.softKey(); }
    protected void paint(Graphics g) { Eng.inst.paint(g, getWidth(), getHeight()); }
    public void keyPressed(int k) { Eng.inst.keyPressed(k, this); }
    public void keyReleased(int k) { Eng.inst.keyReleased(k); }
    public void pointerPressed(int x, int y) { Eng.inst.pointerPressed(x, y); }
    public void showNotify() { Eng.inst.showNotify(); }
    public void hideNotify() { Eng.inst.hideNotify(); }
}
