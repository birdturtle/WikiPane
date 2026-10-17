/*
 * Copyright (c) 2018 Abex
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
// Selection behavior adapted for WikiPane.
package com.wikipane.integration;

import com.wikipane.WikiPaneConfig;
import java.util.function.*;
import net.runelite.api.*;
import net.runelite.api.events.*;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.SpriteID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.*;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.wiki.WikiPlugin;
import okhttp3.HttpUrl;

/** Owns only its own widget. Never intercepts LinkBrowser or mutates other plugin settings. */
public final class WikiButtonIntegration {
    private final Client client;
    private final ItemManager items;
    private final PluginManager plugins;
    private final WikiPaneConfig config;
    private final Consumer<HttpUrl> lookup;
    private final Runnable search;
    private final Consumer<String> guidance;
    private Widget icon, parent, vanilla;
    private boolean previousHidden, selected, enabled = true;
    public WikiButtonIntegration(Client client, ItemManager items, PluginManager plugins,
        WikiPaneConfig config, Consumer<HttpUrl> lookup, Runnable search, Consumer<String> guidance) {
        this.client = client;
        this.items = items;
        this.plugins = plugins;
        this.config = config;
        this.lookup = lookup;
        this.search = search;
        this.guidance = guidance;
    }
    public boolean coreEnabled() {
        return plugins.getPlugins().stream().anyMatch(
            p -> p instanceof WikiPlugin && plugins.isPluginEnabled(p));
    }
    public void reconcile() {
        boolean conflict = coreEnabled();
        if (!enabled || !config.replacement() || conflict) {
            remove();
            if (enabled && config.replacement() && conflict)
                guidance.accept("Replacement paused: disable RuneLite's Wiki plugin.");
            return;
        }
        Widget candidate = client.getWidget(InterfaceID.Orbs.WIKI);
        int vanillaId = InterfaceID.Orbs.WIKI_ICON;
        if (candidate == null || candidate.isHidden()) {
            candidate = client.getWidget(InterfaceID.OrbsNomap.WIKI);
            vanillaId = InterfaceID.OrbsNomap.WIKI_ICON;
        }
        if (candidate == null || candidate.isHidden()
            || client.getVarbitValue(VarbitID.WIKI_ICON_DISABLED) != 0) {
            remove();
            return;
        }
        if (parent == candidate && icon != null) {
            if (vanilla != null)
                vanilla.setHidden(true);
            return;
        }
        remove();
        // Never overwrite another widget installed by a plugin.
        Widget[] children = candidate.getChildren();
        if (children != null && children.length > 0 && children[0] != null) {
            guidance.accept("Another plugin owns the Wiki button.");
            return;
        }
        parent = candidate;
        vanilla = client.getWidget(vanillaId);
        if (vanilla != null) {
            previousHidden = vanilla.isSelfHidden();
            vanilla.setHidden(true);
        }
        icon = parent.createChild(0, WidgetType.GRAPHIC);
        icon.setSpriteId(SpriteID.WikiIcon.DESELECTED);
        icon.setOriginalX(0);
        icon.setOriginalY(0);
        icon.setXPositionMode(WidgetPositionMode.ABSOLUTE_CENTER);
        icon.setYPositionMode(WidgetPositionMode.ABSOLUTE_CENTER);
        icon.setOriginalWidth(40);
        icon.setOriginalHeight(14);
        icon.setTargetVerb("Lookup");
        icon.setName("WikiPane");
        icon.setClickMask(WidgetConfig.USE_GROUND_ITEM | WidgetConfig.USE_NPC | WidgetConfig.USE_OBJECT
            | WidgetConfig.USE_WIDGET);
        icon.setNoClickThrough(true);
        icon.setOnTargetEnterListener((JavaScriptCallback) event -> {
            selected = true;
            icon.setSpriteId(SpriteID.WikiIcon.SELECTED);
            client.setAllWidgetsAreOpTargetable(true);
        });
        icon.setOnTargetLeaveListener((JavaScriptCallback) event -> deselect());
        int searchIndex = config.leftSearch() ? 4 : 5;
        icon.setAction(searchIndex, "Search");
        icon.setOnOpListener((JavaScriptCallback) event -> {
            if (event.getOp() - 1 == searchIndex) {
                deselect();
                search.run();
            }
        });
        icon.revalidate();
    }
    private void deselect() {
        if (!selected)
            return;
        selected = false;
        client.setAllWidgetsAreOpTargetable(false);
        client.setWidgetSelected(false);
        if (icon != null)
            icon.setSpriteId(SpriteID.WikiIcon.DESELECTED);
    }
    public void clicked(MenuOptionClicked event) {
        if (!selected)
            return;
        deselect();
        event.consume();
        HttpUrl url = GameEntityLookup.resolve(client, items, event);
        if (url != null)
            lookup.accept(url);
    }
    public void menu(MenuEntryAdded event) {
        if (!selected || event.getType() != MenuAction.WIDGET_TARGET_ON_WIDGET.getId())
            return;
        Widget widget = client.getWidget(event.getActionParam1());
        if (widget != null && event.getActionParam0() != -1)
            widget = widget.getChild(event.getActionParam0());
        for (MenuEntry entry : client.getMenu().getMenuEntries())
            if (entry.getType() == MenuAction.WIDGET_TARGET_ON_WIDGET) {
                if (widget == null || widget.getType() != WidgetType.GRAPHIC || widget.getItemId() < 0
                    || widget.getItemId() == ItemID.BLANKOBJECT)
                    client.getMenu().removeMenuEntry(entry);
                else
                    entry.setTarget(net.runelite.client.ui.JagexColors.MENU_TARGET_TAG
                        + items.getItemComposition(items.canonicalize(widget.getItemId())).getMembersName());
            }
    }
    public void refresh() {
        remove();
        reconcile();
    }
    public void remove() {
        deselect();
        if (parent != null) {
            Widget[] children = parent.getChildren();
            if (children != null && children.length > 0 && children[0] == icon)
                children[0] = null;
        }
        if (vanilla != null && !coreEnabled() && client.getVarbitValue(VarbitID.WIKI_ICON_DISABLED) == 0)
            vanilla.setHidden(previousHidden);
        icon = null;
        parent = null;
        vanilla = null;
    }
    public void stop() {
        enabled = false;
        remove();
    }
}
