package com.wikipane.ui;

import com.wikipane.pin.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.*;
import javax.swing.event.*;

/** User-driven selection across native reader fragments. Never sends or synthesizes game input. */
public final class ReaderSelection {
    private final Consumer<PinSelection> pin;
    private final Consumer<String> error;
    private final List<JEditorPane> panes = new ArrayList<>();
    private final Map<JEditorPane, Registration> registered = new IdentityHashMap<>();
    private final Map<JEditorPane, int[]> ranges = new IdentityHashMap<>();
    private Container root;
    private JEditorPane anchor;
    private int anchorOffset;
    private boolean adjusting, dragging;
    private Point press;
    public ReaderSelection(Consumer<PinSelection> pin, Consumer<String> error) {
        this.pin = pin; this.error = error;
    }
    private final class Registration {
        final JEditorPane pane;
        final JPopupMenu original, menu = new JPopupMenu();
        final JMenuItem action = new JMenuItem("Pin selection"), copy = new JMenuItem("Copy");
        PinSelection selection;
        final CaretListener caret;
        final MouseAdapter mouse;
        Registration(JEditorPane pane) {
            this.pane = pane; original = pane.getComponentPopupMenu();
            action.setName("wiki.pinSelection");
            menu.add(action); menu.add(copy);
            if (original != null) {
                menu.addSeparator();
                for (Component component : original.getComponents()) menu.add(component);
            }
            menu.addPopupMenuListener(new PopupMenuListener() {
                public void popupMenuWillBecomeVisible(PopupMenuEvent event) {
                    selection = null;
                    try {
                        selection = capture(pane);
                        String restriction = selection.fragments.stream().map(fragment -> fragment.context.restriction())
                            .filter(reason -> !reason.isEmpty()).findFirst().orElse("");
                        action.setEnabled(restriction.isEmpty());
                        action.setToolTipText(restriction.isEmpty() ? "Pin the highlighted passage" : restriction);
                    } catch (IllegalArgumentException ex) {
                        action.setEnabled(false); action.setToolTipText(ex.getMessage());
                    }
                    copy.setEnabled(!ranges.isEmpty() || pane.getSelectionStart() != pane.getSelectionEnd());
                    pane.getCaret().setSelectionVisible(true);
                }
                public void popupMenuWillBecomeInvisible(PopupMenuEvent event) {}
                public void popupMenuCanceled(PopupMenuEvent event) {}
            });
            action.addActionListener(event -> { if (selection != null) pin.accept(selection); });
            copy.addActionListener(event -> {
                try {
                    PinSelection selected = capture(pane);
                    StringBuilder text = new StringBuilder();
                    for (PinSelection.Fragment fragment : selected.fragments)
                        for (PinSelection.Paragraph paragraph : fragment.paragraphs) {
                            if (text.length() > 0) text.append('\n');
                            if (!paragraph.marker.isEmpty()) text.append(paragraph.marker).append(' ');
                            text.append(paragraph.text());
                        }
                    Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text.toString()), null);
                } catch (IllegalArgumentException | IllegalStateException ex) { error.accept("The selection could not be copied."); }
            });
            pane.setComponentPopupMenu(menu);
            caret = event -> {
                if (adjusting || dragging) return;
                if (event.getDot() != event.getMark()) {
                    clearOthers(pane);
                    ranges.put(pane, new int[]{Math.min(event.getDot(), event.getMark()), Math.max(event.getDot(), event.getMark())});
                } else if (ranges.size() == 1) ranges.remove(pane);
            };
            mouse = new MouseAdapter() {
                @Override public void mousePressed(MouseEvent event) {
                    if (!SwingUtilities.isLeftMouseButton(event)) return;
                    for (JEditorPane p : panes) p.putClientProperty("wiki.selectionDragging", Boolean.FALSE);
                    if (event.getClickCount() > 1) return;
                    clearOthers(pane);
                    anchor = pane;
                    anchorOffset = Math.max(0, pane.viewToModel2D(event.getPoint()));
                    press = event.getPoint(); dragging = false;
                }
                @Override public void mouseDragged(MouseEvent event) {
                    if (anchor != pane || root == null || press == null) return;
                    if (!dragging && press.distance(event.getPoint()) < 3) return;
                    dragging = true;
                    Point point = SwingUtilities.convertPoint(pane, event.getPoint(), root);
                    JEditorPane target = nearest(point);
                    if (target == null) return;
                    Point local = SwingUtilities.convertPoint(root, point, target);
                    int offset = Math.max(0, target.viewToModel2D(local));
                    selectRange(anchor, anchorOffset, target, offset);
                    target.scrollRectToVisible(new Rectangle(local.x, local.y, 1, 1));
                }
                @Override public void mouseReleased(MouseEvent event) { dragging = false; press = null; }
            };
            pane.addCaretListener(caret); pane.addMouseListener(mouse); pane.addMouseMotionListener(mouse);
        }
        void remove() {
            pane.removeCaretListener(caret); pane.removeMouseListener(mouse); pane.removeMouseMotionListener(mouse);
            if (pane.getComponentPopupMenu() == menu) pane.setComponentPopupMenu(original);
            selection = null;
        }
    }
    public void refresh(Container root) {
        this.root = root;
        panes.clear(); scan(root);
    }
    private void scan(Container root) {
        for (Component component : root.getComponents()) {
            if (component instanceof JEditorPane
                && ((JEditorPane)component).getClientProperty("wiki.pin.context") instanceof PinContext) {
                JEditorPane pane = (JEditorPane)component;
                panes.add(pane);
                registered.computeIfAbsent(pane, Registration::new);
            } else if (component instanceof Container) scan((Container)component);
        }
    }
    private void clearOthers(JEditorPane keep) {
        adjusting = true;
        try {
            for (JEditorPane pane : new ArrayList<>(ranges.keySet())) if (pane != keep) pane.select(0, 0);
            ranges.keySet().removeIf(pane -> pane != keep);
        } finally { adjusting = false; }
    }
    private JEditorPane nearest(Point point) {
        JEditorPane closest = null;
        long best = Long.MAX_VALUE;
        for (JEditorPane pane : panes) {
            Rectangle bounds = SwingUtilities.convertRectangle(pane.getParent(), pane.getBounds(), root);
            int dx = Math.max(0, Math.max(bounds.x - point.x, point.x - bounds.x - bounds.width));
            int dy = Math.max(0, Math.max(bounds.y - point.y, point.y - bounds.y - bounds.height));
            long distance = (long)dy * dy * 4 + (long)dx * dx;
            if (distance < best) { best = distance; closest = pane; }
        }
        return closest;
    }
    /** Also exercised directly in isolated Swing tests; updates only sidebar text selections. */
    public void selectRange(JEditorPane from, int start, JEditorPane to, int end) {
        int a = panes.indexOf(from), b = panes.indexOf(to);
        if (a < 0 || b < 0) return;
        if (a > b || a == b && start > end) { int i = a; a = b; b = i; i = start; start = end; end = i; }
        adjusting = true;
        try {
            ranges.clear();
            for (int i = 0; i < panes.size(); i++) {
                JEditorPane pane = panes.get(i);
                int low = i == a ? start : 0, high = i == b ? end : pane.getDocument().getLength();
                if (i < a || i > b) low = high = 0;
                low = Math.max(0, Math.min(low, pane.getDocument().getLength()));
                high = Math.max(low, Math.min(high, pane.getDocument().getLength()));
                pane.select(low, high); pane.getCaret().setSelectionVisible(true);
                pane.putClientProperty("wiki.selectionDragging", Boolean.TRUE);
                if (high > low) ranges.put(pane, new int[]{low, high});
            }
        } finally { adjusting = false; }
    }
    public PinSelection capture(JEditorPane invoker) {
        List<PinSelection.Fragment> fragments = new ArrayList<>();
        if (ranges.size() > 1 && ranges.containsKey(invoker)) {
            int characters = 0;
            for (int[] range : ranges.values()) characters += range[1] - range[0];
            if (characters > PinSelection.MAX_CHARACTERS || ranges.size() > PinSelection.MAX_FRAGMENTS)
                throw new IllegalArgumentException("Select a smaller passage to pin. Nothing has been truncated.");
            for (JEditorPane pane : panes) {
                int[] range = ranges.get(pane);
                if (range != null) fragments.add(SelectionCapture.capture(pane, range[0], range[1]));
            }
        } else if (invoker.getSelectionEnd() > invoker.getSelectionStart())
            fragments.add(SelectionCapture.capture(invoker, invoker.getSelectionStart(), invoker.getSelectionEnd()));
        fragments.removeIf(fragment -> fragment.paragraphs.isEmpty());
        return new PinSelection(fragments);
    }
    public void clear() {
        for (Registration registration : registered.values()) registration.remove();
        registered.clear(); panes.clear(); ranges.clear(); anchor = null; root = null;
    }
}
