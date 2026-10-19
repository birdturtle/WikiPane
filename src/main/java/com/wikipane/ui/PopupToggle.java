package com.wikipane.ui;

import java.awt.AWTEvent;
import java.awt.EventQueue;
import java.awt.event.MouseEvent;
import java.util.function.Supplier;
import javax.swing.*;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;

/** Keeps an invoker click a close action even when Swing cancels the popup before button release. */
final class PopupToggle {
    static void bind(JButton button, JPopupMenu popup, Runnable open) {
        bind(button, popup, open, EventQueue::getCurrentEvent);
    }

    static void bind(JButton button, JPopupMenu popup, Runnable open, Supplier<AWTEvent> currentEvent) {
        boolean[] dismissedOnPress = {false};
        boolean[] pressed = {false};
        popup.addPopupMenuListener(new PopupMenuListener() {
            public void popupMenuWillBecomeVisible(PopupMenuEvent event) { dismissedOnPress[0] = false; }
            public void popupMenuWillBecomeInvisible(PopupMenuEvent event) {}
            public void popupMenuCanceled(PopupMenuEvent event) {
                AWTEvent current = currentEvent.get();
                if (!(current instanceof MouseEvent)) return;
                MouseEvent mouse = (MouseEvent) current;
                if (mouse.getID() != MouseEvent.MOUSE_PRESSED || !SwingUtilities.isLeftMouseButton(mouse)) return;
                if (mouse.getComponent() == button || SwingUtilities.isDescendingFrom(mouse.getComponent(), button)) {
                    java.awt.Point point = SwingUtilities.convertPoint(mouse.getComponent(), mouse.getPoint(), button);
                    dismissedOnPress[0] = button.contains(point);
                }
            }
        });
        button.addActionListener(event -> {
            if (dismissedOnPress[0] || popup.isVisible()) {
                dismissedOnPress[0] = false;
                popup.setVisible(false);
            } else open.run();
        });
        button.getModel().addChangeListener(event -> {
            boolean now = button.getModel().isPressed();
            // DefaultButtonModel fires its action before notifying this release change.
            // Also clear after a drag-off release, which deliberately produces no action.
            if (pressed[0] && !now) dismissedOnPress[0] = false;
            pressed[0] = now;
        });
    }
}
