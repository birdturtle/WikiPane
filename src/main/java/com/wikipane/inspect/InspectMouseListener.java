package com.wikipane.inspect;

import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.runelite.client.input.MouseAdapter;

/** Explicit overlay controls only; modifier gestures and all other game clicks pass through. */
public final class InspectMouseListener extends MouseAdapter {
    public enum Action { READ, PREVIOUS, NEXT, DISMISS }
    private final BooleanSupplier enabled;
    private final Consumer<Action> action;
    private volatile Map<Action, Rectangle> bounds = Map.of();
    private boolean pressed, clicked;
    public InspectMouseListener(BooleanSupplier enabled, Consumer<Action> action) { this.enabled = enabled; this.action = action; }
    public void bounds(Map<Action, Rectangle> value) {
        Map<Action, Rectangle> copy = new EnumMap<>(Action.class);
        value.forEach((key, rectangle) -> copy.put(key, new Rectangle(rectangle))); bounds = copy;
    }
    @Override public MouseEvent mousePressed(MouseEvent event) {
        pressed = clicked = false;
        if (event.isConsumed() || event.getButton() != MouseEvent.BUTTON1 || event.isAltDown() || event.isShiftDown()
            || event.isControlDown() || event.isMetaDown() || !enabled.getAsBoolean()) return event;
        for (Map.Entry<Action, Rectangle> entry : bounds.entrySet()) if (entry.getValue().contains(event.getPoint())) {
            pressed = true; action.accept(entry.getKey()); event.consume(); break;
        }
        return event;
    }
    @Override public MouseEvent mouseReleased(MouseEvent event) {
        if (pressed && event.getButton() == MouseEvent.BUTTON1) { pressed = false; clicked = true; event.consume(); }
        return event;
    }
    @Override public MouseEvent mouseClicked(MouseEvent event) {
        if (clicked && event.getButton() == MouseEvent.BUTTON1) { clicked = false; event.consume(); }
        return event;
    }
}
