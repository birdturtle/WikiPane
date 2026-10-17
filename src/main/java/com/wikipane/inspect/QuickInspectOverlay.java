package com.wikipane.inspect;

import java.awt.*;
import net.runelite.api.*;
import net.runelite.client.plugins.Plugin;
import com.wikipane.ui.ReaderComponents;
import net.runelite.client.ui.overlay.*;
import net.runelite.client.util.LinkBrowser;

/** A real RuneLite overlay: Shift-right-click actions, Alt-drag positioning, no pretend Swing buttons. */
public final class QuickInspectOverlay extends Overlay {
    private final Client client;
    private final QuickInspectController controller;
    private QuickInspectController.View installed;
    public QuickInspectOverlay(Plugin plugin, Client client, QuickInspectController controller) {
        super(plugin);
        this.client = client; this.controller = controller;
        setPosition(OverlayPosition.TOP_LEFT);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
        setPriority(PRIORITY_LOW);
    }
    private boolean current(QuickInspectController.View original) {
        QuickInspectController.View now = controller.view();
        return now != null && now.lookup.equals(original.lookup)
            && now.inspection == original.inspection && now.page == original.page;
    }
    private void menus(QuickInspectController.View view) {
        if (view == installed) return;
        installed = view;
        getMenuEntries().clear();
        if (view == null) return;
        if (view.inspection != null) {
            addMenuEntry(MenuAction.RUNELITE_OVERLAY, "Open full article", "WikiPane", e -> { if (current(view)) controller.explore(); });
            if (view.page + 1 < view.layout.pages.size())
                addMenuEntry(MenuAction.RUNELITE_OVERLAY, "Next page", "WikiPane", e -> { if (current(view)) controller.page(1); });
            if (view.page > 0)
                addMenuEntry(MenuAction.RUNELITE_OVERLAY, "Previous page", "WikiPane", e -> { if (current(view)) controller.page(-1); });
        } else addMenuEntry(MenuAction.RUNELITE_OVERLAY, "Retry", "WikiPane", e -> { if (current(view)) controller.retry(); });
        addMenuEntry(MenuAction.RUNELITE_OVERLAY, "Open lookup in browser", "WikiPane",
            e -> LinkBrowser.browse(view.lookup.toString()));
        addMenuEntry(MenuAction.RUNELITE_OVERLAY, "Dismiss", "WikiPane", e -> { if (current(view)) controller.dismiss(); });
    }
    /** Add a native submenu rather than filling the top-level menu with article links. */
    public boolean opened() {
        QuickInspectController.View view = controller.view();
        net.runelite.api.Point mouse = client.getMouseCanvasPosition();
        if (view == null || mouse == null || !getBounds().contains(mouse.getX(), mouse.getY())) return false;
        if (client.isKeyPressed(KeyCode.KC_SHIFT) && view.inspection != null
            && !view.inspection.related.isEmpty()) {
            net.runelite.api.Menu sub = client.getMenu().createMenuEntry(1).setOption("Related Wiki articles")
                .setTarget("WikiPane").setType(MenuAction.RUNELITE).createSubMenu();
            for (int i = 0; i < Math.min(12, view.inspection.related.size()); i++) {
                final int index = i;
                String label = view.inspection.related.get(i).target.label.replace('<', ' ').replace('>', ' ');
                sub.createMenuEntry(-1).setOption("Read " + label).setType(MenuAction.RUNELITE)
                    .onClick(e -> { if (current(view)) controller.related(index); });
            }
        }
        return true;
    }
    @Override public Dimension render(Graphics2D graphics) {
        QuickInspectController.View view = controller.view();
        menus(view);
        if (view == null || client.getGameState() != GameState.LOGGED_IN) return null;
        return paint(graphics, view);
    }
    /** Shared with the isolated preview; does not access the game or mutate state. */
    public static Dimension paint(Graphics2D original, QuickInspectController.View view) {
        Graphics2D graphics = (Graphics2D) original.create();
        try {
            final int pad = InspectionLayout.PADDING;
            final int step = InspectionLayout.LINE_HEIGHT;
            int titleHeight = view.layout == null ? 20 : Math.max(20, view.layout.title.size() * 14);
            int count = view.layout == null ? view.messageLines.size() : view.layout.pages.get(view.page).size();
            int height = pad + titleHeight + 3 + count * step + 18;
            net.runelite.client.ui.overlay.components.BackgroundComponent background =
                new net.runelite.client.ui.overlay.components.BackgroundComponent();
            background.setBackgroundColor(net.runelite.client.ui.overlay.components.ComponentConstants.STANDARD_BACKGROUND_COLOR);
            background.setRectangle(new Rectangle(0, 0, InspectionLayout.WIDTH, height));
            background.render(graphics);
            graphics.setColor(ReaderComponents.ACCENT);
            graphics.setFont(InspectionLayout.TITLE);
            if (view.layout == null) graphics.drawString("WikiPane", pad, 15);
            else {
                int y = 15;
                for (String line : view.layout.title) { graphics.drawString(line, pad, y); y += 14; }
                if (view.image != null) {
                    double scale = Math.min(1, Math.min(20.0 / view.image.getWidth(), 20.0 / view.image.getHeight()));
                    graphics.drawImage(view.image, InspectionLayout.WIDTH - pad - 22, pad,
                        (int)(view.image.getWidth() * scale), (int)(view.image.getHeight() * scale), null);
                }
            }
            int y = pad + titleHeight + 12;
            if (view.layout == null) {
                graphics.setFont(InspectionLayout.BODY);
                graphics.setColor(Color.WHITE);
                for (String line : view.messageLines) { graphics.drawString(line, pad, y); y += step; }
            } else {
                for (InspectionLayout.Line line : view.layout.pages.get(view.page)) {
                    graphics.setFont(line.label ? InspectionLayout.LABEL : InspectionLayout.BODY);
                    graphics.setColor(line.label ? ReaderComponents.ACCENT : Color.WHITE);
                    if (!line.value.isEmpty()) {
                        graphics.setFont(InspectionLayout.LABEL);
                        graphics.setColor(ReaderComponents.ACCENT);
                        graphics.drawString(line.text, pad, y);
                        int valueX = pad + graphics.getFontMetrics().stringWidth(line.text) + 7;
                        graphics.setFont(InspectionLayout.BODY);
                        graphics.setColor(Color.WHITE);
                        graphics.drawString(line.value, valueX, y);
                    } else graphics.drawString(line.text, pad, y);
                    y += step;
                }
            }
            graphics.setFont(InspectionLayout.BODY);
            graphics.setColor(new Color(200, 200, 200));
            String footer = "OSRS Wiki | Shift-right-click";
            if (view.layout != null && view.layout.pages.size() > 1)
                footer = "OSRS Wiki " + (view.page + 1) + "/" + view.layout.pages.size() + " | Shift-right-click";
            graphics.drawString(footer, pad, height - 5);
            return new Dimension(InspectionLayout.WIDTH, height);
        } finally { graphics.dispose(); }
    }
}
