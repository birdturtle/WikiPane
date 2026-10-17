package com.wikipane.pin;

import java.awt.*;
import net.runelite.api.*;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.ui.overlay.*;
import net.runelite.client.ui.overlay.components.*;

/** Independent native reference overlay. The sidebar may close or navigate without affecting it. */
public final class PinnedOverlay extends Overlay {
    private final Client client;
    private final PinnedReferences owner;
    private final PinnedReferences.Pin pin;
    private int menuPage = -1;
    private PinMouseListener mouse;
    public void mouse(PinMouseListener mouse) { this.mouse = mouse; }
    public PinnedOverlay(Plugin plugin, Client client, PinnedReferences owner, PinnedReferences.Pin pin) {
        super(plugin); this.client = client; this.owner = owner; this.pin = pin;
        setPosition(OverlayPosition.TOP_LEFT); setLayer(OverlayLayer.ABOVE_WIDGETS); setPriority(PRIORITY_LOW);
        menus();
    }
    @Override public String getName() { return "WikiPane reference " + pin.slot; }
    private void menus() {
        if (menuPage == pin.page) return;
        menuPage = pin.page; getMenuEntries().clear();
        addMenuEntry(MenuAction.RUNELITE_OVERLAY, "Open source", "WikiPane", event -> owner.openSource(pin));
        if (pin.page + 1 < pin.layout.pages.size())
            addMenuEntry(MenuAction.RUNELITE_OVERLAY, "Next page", "WikiPane", event -> owner.page(pin, 1));
        if (pin.page > 0)
            addMenuEntry(MenuAction.RUNELITE_OVERLAY, "Previous page", "WikiPane", event -> owner.page(pin, -1));
        addMenuEntry(MenuAction.RUNELITE_OVERLAY, "Unpin", "WikiPane", event -> owner.unpin(pin));
    }
    public boolean hovered() {
        net.runelite.api.Point point = client.getMouseCanvasPosition();
        return owner.active(pin) && point != null && getBounds().contains(point.getX(), point.getY());
    }
    @Override public Dimension render(Graphics2D graphics) {
        if (!owner.active(pin) || client.getGameState() != GameState.LOGGED_IN) return null;
        menus();
        Dimension size = paint(graphics, pin.layout, pin.page);
        if (mouse != null) {
            Rectangle previous = button(size.height, false), next = button(size.height, true);
            previous.translate(getBounds().x, getBounds().y); next.translate(getBounds().x, getBounds().y);
            mouse.bounds(pin.layout.pages.size() > 1 ? previous : new Rectangle(),
                pin.layout.pages.size() > 1 ? next : new Rectangle());
        }
        return size;
    }
    static Rectangle button(int height, boolean next) {
        return new Rectangle(PinLayout.WIDTH - PinLayout.PAD - (next ? 24 : 52), height - 19, 24, 17);
    }
    public static Dimension paint(Graphics2D original, PinLayout layout, int page) {
        Graphics2D graphics = (Graphics2D)original.create();
        try {
            int heading = layout.title.size() * PinLayout.STEP;
            int height = PinLayout.PAD * 2 + heading + layout.pages.get(page).size() * PinLayout.STEP + 17;
            BackgroundComponent background = new BackgroundComponent();
            background.setRectangle(new Rectangle(0, 0, PinLayout.WIDTH, height));
            background.setBackgroundColor(ComponentConstants.STANDARD_BACKGROUND_COLOR); background.render(graphics);
            int y = PinLayout.PAD + 11;
            for (PinLayout.Line line : layout.title) { line.text.draw(graphics, PinLayout.PAD + line.x, y); y += PinLayout.STEP; }
            y += 3;
            for (PinLayout.Line line : layout.pages.get(page)) { line.text.draw(graphics, PinLayout.PAD + line.x, y); y += PinLayout.STEP; }
            graphics.setFont(PinLayout.BODY); graphics.setColor(Color.LIGHT_GRAY);
            String footer = "OSRS Wiki | Shift-right-click";
            if (layout.pages.size() > 1) {
                footer = "OSRS Wiki  " + (page + 1) + "/" + layout.pages.size();
                for (boolean next : new boolean[]{false, true}) {
                    Rectangle button = button(height, next);
                    boolean enabled = next ? page + 1 < layout.pages.size() : page > 0;
                    graphics.setColor(new Color(255, 255, 255, enabled ? 28 : 8));
                    graphics.fillRect(button.x, button.y, button.width, button.height);
                    graphics.setColor(enabled ? new Color(255, 190, 65) : Color.GRAY);
                    graphics.drawString(next ? ">" : "<", button.x + 9, height - 6);
                }
                graphics.setColor(Color.LIGHT_GRAY);
            }
            graphics.drawString(footer, PinLayout.PAD, height - 5);
            return new Dimension(PinLayout.WIDTH, height);
        } finally { graphics.dispose(); }
    }
}
