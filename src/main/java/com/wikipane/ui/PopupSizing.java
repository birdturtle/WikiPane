package com.wikipane.ui;

import java.awt.*;
import javax.swing.SwingUtilities;
import javax.swing.JPopupMenu;

/** Keep chooser contents within both the client window and the usable monitor area. */
final class PopupSizing {
    private PopupSizing() {}
    static Dimension fit(Component invoker, int width, int height) {
        if (!invoker.isShowing() || invoker.getGraphicsConfiguration() == null)
            return new Dimension(width, height);
        Rectangle bounds = available(invoker);
        Point point = invoker.getLocationOnScreen();
        return fit(new Rectangle(point, invoker.getSize()), bounds, width, height);
    }
    private static Rectangle available(Component invoker) {
        GraphicsConfiguration display = invoker.getGraphicsConfiguration();
        Rectangle bounds = new Rectangle(display.getBounds());
        Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(display);
        bounds.x += insets.left; bounds.y += insets.top;
        bounds.width -= insets.left + insets.right; bounds.height -= insets.top + insets.bottom;
        Window owner = SwingUtilities.getWindowAncestor(invoker);
        if (owner != null) bounds = bounds.intersection(owner.getBounds());
        return bounds;
    }
    static void show(JPopupMenu popup, Component invoker) {
        Point point = invoker.getLocationOnScreen();
        Rectangle bounds = available(invoker);
        Dimension size = popup.getPreferredSize();
        int x = Math.max(bounds.x,Math.min(point.x,bounds.x+bounds.width-size.width));
        int y = point.y+invoker.getHeight();
        if (y+size.height > bounds.y+bounds.height) y = point.y-size.height;
        y = Math.max(bounds.y,Math.min(y,bounds.y+bounds.height-size.height));
        popup.show(invoker,x-point.x,y-point.y);
    }
    static Dimension fit(Rectangle invoker, Rectangle bounds, int width, int height) {
        int above = invoker.y - bounds.y;
        int below = bounds.y + bounds.height - invoker.y - invoker.height;
        return new Dimension(Math.max(1,Math.min(width,bounds.width-12)),
            Math.max(1,Math.min(height,Math.max(above,below)-12)));
    }
}
