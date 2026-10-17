package com.wikipane.integration;

import com.wikipane.WikiPaneConfig;
import java.util.*;
import java.util.function.Consumer;
import net.runelite.api.*;
import net.runelite.api.events.MenuOpened;
import net.runelite.client.game.ItemManager;
import okhttp3.HttpUrl;

/** Adds local callbacks only after right-click opens a menu. Never swaps or consumes game actions. */
public final class ContextMenuIntegration {
    private final Client client;
    private final ItemManager items;
    private final WikiPaneConfig config;
    private final Consumer<HttpUrl> lookup;
    public ContextMenuIntegration(Client client, ItemManager items, WikiPaneConfig config,
                                  Consumer<HttpUrl> lookup) {
        this.client = client; this.items = items; this.config = config; this.lookup = lookup;
    }
    public void opened(MenuOpened event) {
        if (!config.contextLookups() || client.getGameState() != GameState.LOGGED_IN
            || client.isWidgetSelected()
            || (config.contextAccess() == WikiPaneConfig.ContextAccess.SHIFT_RIGHT_CLICK
                && !client.isKeyPressed(KeyCode.KC_SHIFT))) return;
        Set<String> seen = new HashSet<>();
        int added = 0;
        // Use the event snapshot; Menu Entry Swapper's independent additions remain untouched.
        MenuEntry[] entries = event.getMenuEntries();
        for (int i = entries.length - 1; i >= 0 && added < 8; i--) {
            MenuEntry original = entries[i];
            HttpUrl entity = GameEntityLookup.context(client, items, original);
            if (entity == null || !seen.add(entity.toString())) continue;
            added++;
            client.getMenu().createMenuEntry(1).setOption("WikiPane")
                .setTarget(original.getTarget()).setType(MenuAction.RUNELITE)
                .onClick(ignored -> { if (config.contextLookups()) lookup.accept(entity); });
        }
    }
}
