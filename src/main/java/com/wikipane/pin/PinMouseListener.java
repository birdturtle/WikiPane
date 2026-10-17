package com.wikipane.pin;

import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import net.runelite.client.input.MouseAdapter;

/** Only explicit footer clicks are handled; drag modifiers and game clicks pass through. */
public final class PinMouseListener extends MouseAdapter {
    private final BooleanSupplier enabled;
    private final IntConsumer page;
    private volatile Rectangle previous = new Rectangle(), next = new Rectangle();
    private boolean pressed, clicked;
    public PinMouseListener(BooleanSupplier enabled, IntConsumer page) {
        this.enabled = enabled; this.page = page;
    }
    public void bounds(Rectangle previous, Rectangle next) {
        this.previous = new Rectangle(previous); this.next = new Rectangle(next);
    }
    @Override public MouseEvent mousePressed(MouseEvent event) {
        pressed = false; clicked = false;
        if (event.isConsumed() || event.getButton() != MouseEvent.BUTTON1 || event.isAltDown()
            || event.isShiftDown() || event.isControlDown() || event.isMetaDown() || !enabled.getAsBoolean()) return event;
        int delta = previous.contains(event.getPoint()) ? -1 : next.contains(event.getPoint()) ? 1 : 0;
        if (delta != 0) { pressed = true; page.accept(delta); event.consume(); }
        return event;
    }
    @Override public MouseEvent mouseReleased(MouseEvent event) {
        if (pressed && event.getButton() == MouseEvent.BUTTON1) {
            pressed = false; clicked = true; event.consume();
        }
        return event;
    }
    @Override public MouseEvent mouseClicked(MouseEvent event) {
        if (clicked && event.getButton() == MouseEvent.BUTTON1) { clicked = false; event.consume(); }
        return event;
    }
}
