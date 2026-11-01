import org.recompile.mobile.*;
import java.io.*;
import javax.imageio.ImageIO;

/** Headless FreeJ2ME driver: runs a jar and follows a key/screenshot script. */
public class Harness {
    public static void main(String[] a) throws Exception {
        int w = Integer.parseInt(a[1]), h = Integer.parseInt(a[2]);
        Mobile.setPlatform(new MobilePlatform(w, h));
        Mobile.getPlatform().setPainter(new Runnable() { public void run() { } });
        String url = new File(a[0]).toURI().toURL().toString();
        if (!Mobile.getPlatform().loadJar(url)) { System.out.println("load failed"); System.exit(1); }
        new Thread(new Runnable() { public void run() { Mobile.getPlatform().runJar(); } }).start();
        BufferedReader r = new BufferedReader(new FileReader(a[3]));
        String l;
        while ((l = r.readLine()) != null) {
            l = l.trim();
            if (l.isEmpty() || l.startsWith("#")) continue;
            String[] p = l.split("\\s+");
            switch (p[0]) {
                case "wait": Thread.sleep(Integer.parseInt(p[1])); break;
                case "press": Mobile.getPlatform().keyPressed(Integer.parseInt(p[1])); break;
                case "release": Mobile.getPlatform().keyReleased(Integer.parseInt(p[1])); break;
                case "tap": {
                    int k = Integer.parseInt(p[1]);
                    Mobile.getPlatform().keyPressed(k); Thread.sleep(p.length > 2 ? Integer.parseInt(p[2]) : 80);
                    Mobile.getPlatform().keyReleased(k); break;
                }
                case "down": Mobile.getPlatform().pointerPressed(Integer.parseInt(p[1]), Integer.parseInt(p[2])); break;
                case "drag": Mobile.getPlatform().pointerDragged(Integer.parseInt(p[1]), Integer.parseInt(p[2])); break;
                case "up": Mobile.getPlatform().pointerReleased(Integer.parseInt(p[1]), Integer.parseInt(p[2])); break;
                case "shot": ImageIO.write(Mobile.getPlatform().getLCD(), "png", new File(p[1])); break;
            }
        }
        System.exit(0);
    }
}
