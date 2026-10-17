package com.wikipane.pin;

import java.awt.Canvas;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.event.InputEvent;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** Isolated Java UI events only; never attached to a game canvas. */
public class PinMouseListenerTest {
    private MouseEvent event(int kind, int x, int modifiers) {
        return new MouseEvent(new Canvas(), kind, 0, modifiers, x, 10, 1, false, MouseEvent.BUTTON1);
    }
    @Test public void footerClicksPageOnceAndConsumeTheWholeClick() {
        List<Integer> pages = new ArrayList<>();
        PinMouseListener listener = new PinMouseListener(() -> true, pages::add);
        listener.bounds(new Rectangle(0, 0, 20, 20), new Rectangle(30, 0, 20, 20));
        MouseEvent press = event(MouseEvent.MOUSE_PRESSED, 35, 0);
        MouseEvent release = event(MouseEvent.MOUSE_RELEASED, 35, 0);
        MouseEvent click = event(MouseEvent.MOUSE_CLICKED, 35, 0);
        listener.mousePressed(press); listener.mouseReleased(release); listener.mouseClicked(click);
        assertEquals(List.of(1), pages);
        assertTrue(press.isConsumed()); assertTrue(release.isConsumed()); assertTrue(click.isConsumed());
        listener.mousePressed(event(MouseEvent.MOUSE_PRESSED, 10, 0));
        assertEquals(List.of(1, -1), pages);
    }
    @Test public void modifiersOutsideClicksInactiveAndDisabledButtonsPassThrough() {
        List<Integer> pages = new ArrayList<>();
        PinMouseListener listener = new PinMouseListener(() -> true, pages::add);
        listener.bounds(new Rectangle(), new Rectangle(30, 0, 20, 20));
        for (int modifiers : new int[]{InputEvent.ALT_DOWN_MASK, InputEvent.SHIFT_DOWN_MASK, InputEvent.CTRL_DOWN_MASK}) {
            MouseEvent press = event(MouseEvent.MOUSE_PRESSED, 35, modifiers);
            listener.mousePressed(press); assertFalse(press.isConsumed());
        }
        for (int x : new int[]{10, 80}) {
            MouseEvent press = event(MouseEvent.MOUSE_PRESSED, x, 0);
            listener.mousePressed(press); assertFalse(press.isConsumed());
        }
        PinMouseListener inactive = new PinMouseListener(() -> false, pages::add);
        inactive.bounds(new Rectangle(), new Rectangle(30, 0, 20, 20));
        MouseEvent press = event(MouseEvent.MOUSE_PRESSED, 35, 0);
        inactive.mousePressed(press); assertFalse(press.isConsumed()); assertTrue(pages.isEmpty());
    }
}
