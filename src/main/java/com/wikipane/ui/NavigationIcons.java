package com.wikipane.ui;
import java.awt.*;
import javax.swing.*;
/** Small scale-independent navigation glyphs with native disabled-state contrast. */
final class NavigationIcons implements Icon {
    enum Action { BACK, FORWARD, SEARCH, BROWSER }
    private final Action action;
    NavigationIcons(Action action) {
        this.action = action;
    }
    public int getIconWidth() {
        return 16;
    }
    public int getIconHeight() {
        return 16;
    }
    public void paintIcon(Component c, Graphics graphics, int x, int y) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.translate(x, y);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setStroke(new BasicStroke(1.6f));
        g.setColor(c.isEnabled() ? ReaderComponents.MUTED : new Color(85, 85, 85));
        switch (action) {
            case BACK:
                g.drawLine(12, 8, 3, 8);
                g.drawLine(3, 8, 8, 3);
                g.drawLine(3, 8, 8, 13);
                break;
            case FORWARD:
                g.drawLine(3, 8, 12, 8);
                g.drawLine(12, 8, 7, 3);
                g.drawLine(12, 8, 7, 13);
                break;
            case SEARCH:
                g.drawOval(2, 2, 8, 8);
                g.drawLine(9, 9, 14, 14);
                break;
            case BROWSER:
                g.drawLine(8, 3, 13, 3);
                g.drawLine(13, 3, 13, 8);
                g.drawLine(13, 3, 6, 10);
                g.drawLine(3, 4, 3, 13);
                g.drawLine(3, 13, 12, 13);
                break;
        }
        g.dispose();
    }
}
