package com.wikipane.inspect;

import java.awt.*;
import net.runelite.api.*;
import net.runelite.client.plugins.Plugin;
import com.wikipane.ui.SemanticPalette;
import net.runelite.client.ui.overlay.*;
import net.runelite.client.util.LinkBrowser;

/** A real RuneLite overlay: Shift-right-click actions, Alt-drag positioning, no pretend Swing buttons. */
public final class QuickInspectOverlay extends Overlay {
    private final Client client;
    private final QuickInspectController controller;
    private QuickInspectController.View installed;
    private InspectMouseListener mouse;
    public void mouse(InspectMouseListener value) { mouse = value; }
    public void control(InspectMouseListener.Action action) {
        switch (action) {
            case READ: controller.explore(); break;
            case PREVIOUS: controller.page(-1); break;
            case NEXT: controller.page(1); break;
            case DISMISS: controller.dismiss(); break;
        }
    }
    static Rectangle button(int height, InspectMouseListener.Action action) {
        switch (action) {
            case READ: return new Rectangle(6, height-19, 68, 17);
            case PREVIOUS: return new Rectangle(80, height-19, 20, 17);
            case NEXT: return new Rectangle(146, height-19, 20, 17);
            default: return new Rectangle(184, height-19, 20, 17);
        }
    }
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
        if(client.isKeyPressed(KeyCode.KC_SHIFT)&&view.inspection!=null
            &&view.inspection.article.semantic.type==com.wikipane.semantic.SemanticDocument.PageType.ITEM
            &&view.inspection.article.recipes.size()>1) {
            net.runelite.api.Menu recipes=client.getMenu().createMenuEntry(1).setOption("Recipe methods")
                .setTarget("WikiPane").setType(MenuAction.RUNELITE).createSubMenu();
            for(int i=0;i<Math.min(12,view.inspection.article.recipes.size());i++) {
                final int index=i;
                com.wikipane.semantic.Recipe recipe=view.inspection.article.recipes.get(i);
                String label=recipe.label().isEmpty()?"Method "+(i+1):recipe.label();
                label=label.replace('<',' ').replace('>',' ');
                recipes.createMenuEntry(-1).setOption(label).setType(MenuAction.RUNELITE)
                    .onClick(e->{if(current(view))controller.recipe(index);});
            }
            if(view.inspection.article.recipes.size()>12)
                recipes.createMenuEntry(-1).setOption("All recipes in article").setType(MenuAction.RUNELITE)
                    .onClick(e->{if(current(view))controller.explore();});
        }
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
        if (view == null || client.getGameState() != GameState.LOGGED_IN) {
            if (mouse != null) mouse.bounds(java.util.Map.of()); return null;
        }
        Dimension size = paint(graphics, view);
        if (mouse != null) {
            java.util.Map<InspectMouseListener.Action, Rectangle> targets = new java.util.EnumMap<>(InspectMouseListener.Action.class);
            for (InspectMouseListener.Action action : InspectMouseListener.Action.values()) {
                if (action == InspectMouseListener.Action.READ && view.inspection == null
                    || action == InspectMouseListener.Action.PREVIOUS && (view.layout == null || view.page == 0)
                    || action == InspectMouseListener.Action.NEXT && (view.layout == null || view.page+1 >= view.layout.pages.size())) continue;
                Rectangle target = button(size.height, action); target.translate(getBounds().x, getBounds().y); targets.put(action, target);
            }
            mouse.bounds(targets);
        }
        return size;
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
            graphics.setColor(SemanticPalette.LINK);
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
                graphics.setColor(SemanticPalette.TEXT);
                for (String line : view.messageLines) { graphics.drawString(line, pad, y); y += step; }
            } else {
                for (InspectionLayout.Line line : view.layout.pages.get(view.page)) {
                    graphics.setFont(line.label ? InspectionLayout.LABEL : InspectionLayout.BODY);
                    graphics.setColor(line.label ? SemanticPalette.LINK : line.color);
                    if (!line.value.isEmpty()) {
                        graphics.setFont(InspectionLayout.LABEL);
                        graphics.setColor(SemanticPalette.LINK);
                        graphics.drawString(line.text, pad + line.indent, y);
                        int valueX = pad + line.indent + graphics.getFontMetrics().stringWidth(line.text) + 7;
                        graphics.setFont(InspectionLayout.BODY);
                        graphics.setColor(line.color);
                        graphics.drawString(line.value, valueX, y);
                    } else graphics.drawString(line.text, pad + line.indent, y);
                    y += step;
                }
            }
            graphics.setFont(InspectionLayout.BODY);
            graphics.setColor(new Color(200, 200, 200));
            for (InspectMouseListener.Action action : InspectMouseListener.Action.values()) {
                boolean enabled = action == InspectMouseListener.Action.DISMISS
                    || view.inspection != null && (action == InspectMouseListener.Action.READ
                        || action == InspectMouseListener.Action.PREVIOUS && view.page > 0
                        || action == InspectMouseListener.Action.NEXT && view.page+1 < view.layout.pages.size());
                if ((action == InspectMouseListener.Action.NEXT || action == InspectMouseListener.Action.PREVIOUS)
                    && (view.layout == null || view.layout.pages.size() <= 1)) continue;
                Rectangle target = button(height, action);
                graphics.setColor(new Color(255,255,255,enabled ? 28 : 8)); graphics.fillRect(target.x,target.y,target.width,target.height);
                graphics.setColor(enabled ? SemanticPalette.LINK : Color.GRAY);
                String label = action == InspectMouseListener.Action.READ ? "Read article"
                    : action == InspectMouseListener.Action.PREVIOUS ? "<" : action == InspectMouseListener.Action.NEXT ? ">" : "×";
                graphics.drawString(label, target.x+4, height-6);
            }
            graphics.setColor(Color.LIGHT_GRAY);
            if (view.layout != null && view.layout.pages.size() > 1)
                graphics.drawString((view.page+1)+"/"+view.layout.pages.size(), 106, height-6);
            else graphics.drawString("OSRS Wiki", 88, height-6);
            return new Dimension(InspectionLayout.WIDTH, height);
        } finally { graphics.dispose(); }
    }
}
