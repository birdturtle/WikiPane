package com.wikipane.ui;

import static com.wikipane.view.CompositionAudit.Outcome.*;

import com.wikipane.view.CompositionAudit;
import java.awt.*;
import javax.swing.*;
import javax.swing.text.View;

/**
 * Bounded, read-only geometry checks after actual layout. No article parsing, IO or repeated layout trials.
 */
public final class SidebarLayoutValidator {
    private SidebarLayoutValidator() {}
    public static int inspect(JComponent root, CompositionAudit audit) {
        if (!SwingUtilities.isEventDispatchThread())
            throw new IllegalStateException("Layout inspection requires EDT");
        if (root.getWidth() <= 0)
            return 0;
        int[] budget = {1024}, count = {0};
        inspect(root, audit, budget, count, "reader", root.isShowing());
        if (budget[0] <= 0)
            audit.add("VISUAL_CHECK_BUDGET", "reader", WARNING,
                "Inspection limited to 1024 materialized components; no claim about unmeasured content.");
        audit.measured(root.getWidth(), count[0]);
        return count[0];
    }
    private static void inspect(Container parent, CompositionAudit audit, int[] budget, int[] count,
        String source, boolean visibleOnly) {
        if (parent instanceof JComponent) {
            Object unit = ((JComponent) parent).getClientProperty("wiki.source");
            if (unit instanceof String)
                source = (String) unit;
        }
        for (Component child : parent.getComponents()) {
            if (--budget[0] < 0)
                return;
            if (!child.isVisible() || child.getWidth() <= 0 || child.getHeight() <= 0)
                continue;
            if (visibleOnly && child instanceof JComponent && ((JComponent) child).getVisibleRect().isEmpty())
                continue;
            count[0]++;
            if (child.getX() < 0 || child.getX() + child.getWidth() > parent.getWidth() + 1)
                audit.add("HORIZONTAL_OVERFLOW", source, WARNING,
                    "Measured child exceeds its parent at " + parent.getWidth()
                        + " px; browser fallback remains available.");
            if (child instanceof JEditorPane) {
                JEditorPane text = (JEditorPane) child;
                View view = text.getUI().getRootView(text);
                Insets inset = text.getInsets();
                int available = Math.max(1, text.getWidth() - inset.left - inset.right);
                view.setSize(available, Math.max(1, text.getHeight() - inset.top - inset.bottom));
                int needed = (int) Math.ceil(view.getPreferredSpan(View.Y_AXIS)) + inset.top + inset.bottom;
                if (needed > child.getHeight() + 2) {
                    Object earlier = text.getClientProperty("reader.requiredHeight");
                    Dimension floor = new Dimension(text.getWidth(), needed);
                    if (!floor.equals(earlier)) {
                        text.putClientProperty("reader.requiredHeight", floor);
                        text.revalidate();
                        parent.revalidate();
                        audit.add("TEXT_HEIGHT", source, REPAIRED,
                            "Text height reserved at measured width of " + text.getWidth() + " px.");
                    } else
                        audit.add("TEXT_HEIGHT_UNRESOLVED", source, WARNING,
                            "Parent still clips text after its measured height was reserved; browser "
                                + "fallback remains available.");
                }
            }
            if (child instanceof Container)
                inspect((Container) child, audit, budget, count, source, visibleOnly);
        }
    }
}
