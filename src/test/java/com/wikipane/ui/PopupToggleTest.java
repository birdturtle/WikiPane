package com.wikipane.ui;

import java.awt.AWTEvent;
import java.awt.event.MouseEvent;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class PopupToggleTest {
    private static final class Popup extends JPopupMenu {
        private boolean visible;
        @Override public boolean isVisible() { return visible; }
        @Override public void setVisible(boolean value) { visible = value; }
        void cancel() { firePopupMenuCanceled(); visible = false; }
    }

    @Test public void invokerPressCancellationDoesNotReopenOnReleaseAndNextClickOpens() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JButton button = new JButton(); button.setSize(100, 28);
            Popup popup = new Popup(); AtomicInteger opens = new AtomicInteger();
            AWTEvent[] current = {null};
            PopupToggle.bind(button, popup, () -> { opens.incrementAndGet(); popup.setVisible(true); }, () -> current[0]);
            button.doClick(0); assertTrue(popup.isVisible());
            current[0] = new MouseEvent(button, MouseEvent.MOUSE_PRESSED, 1, 0, 10, 10, 1, false, MouseEvent.BUTTON1);
            popup.cancel(); // Swing's global popup listener runs before the button model is pressed.
            button.getModel().setArmed(true); button.getModel().setPressed(true);
            button.getModel().setPressed(false); button.getModel().setArmed(false);
            assertFalse(popup.isVisible()); assertEquals(1, opens.get());
            button.doClick(0); assertTrue(popup.isVisible()); assertEquals(2, opens.get());
        });
    }

    @Test public void outsideDismissalDragOffAndKeyboardToggleDoNotLeaveSuppressionBehind() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JButton button = new JButton(); button.setSize(100, 28);
            Popup popup = new Popup(); AWTEvent[] current = {null};
            PopupToggle.bind(button, popup, () -> popup.setVisible(true), () -> current[0]);
            button.doClick(0); button.doClick(0); assertFalse(popup.isVisible());
            button.doClick(0);
            current[0] = new MouseEvent(new JButton(), MouseEvent.MOUSE_PRESSED, 1, 0, 1, 1, 1, false, MouseEvent.BUTTON1);
            popup.cancel(); button.doClick(0); assertTrue(popup.isVisible());
            current[0] = new MouseEvent(button, MouseEvent.MOUSE_PRESSED, 1, 0, 10, 10, 1, false, MouseEvent.BUTTON1);
            popup.cancel(); button.getModel().setArmed(true); button.getModel().setPressed(true);
            button.getModel().setArmed(false); button.getModel().setPressed(false); // release outside the invoker
            button.doClick(0); assertTrue(popup.isVisible());
        });
    }
}
