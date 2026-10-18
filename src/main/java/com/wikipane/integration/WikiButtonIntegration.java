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

/** Keeps the core Wiki widget intact while temporarily presenting a sidebar-routed button. */
public final class WikiButtonIntegration {
    private final Client client;
    private final ItemManager items;
    private final PluginManager plugins;
    private final WikiPaneConfig config;
    private final Consumer<HttpUrl> lookup;
    private final Runnable search;
    private final Consumer<String> guidance;
    private Widget icon, parent, vanilla, coreIcon;
    private boolean previousHidden, selected, positioned, enabled = true;
    private int previousX, previousY, previousXMode, previousYMode;
    private String lastGuidance = "";
    private void advise(String message) {
        if (!message.equals(lastGuidance)) guidance.accept(message);
        lastGuidance = message;
    }
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
        if (!enabled || !config.replacement()) {
            remove(); lastGuidance = ""; return;
        }
        Widget candidate = client.getWidget(InterfaceID.Orbs.WIKI);
        int vanillaId = InterfaceID.Orbs.WIKI_ICON;
        if (candidate == null || candidate.isHidden()) {
            candidate = client.getWidget(InterfaceID.OrbsNomap.WIKI);
            vanillaId = InterfaceID.OrbsNomap.WIKI_ICON;
        }
        if (candidate == null || candidate.isHidden()) {
            remove();
            return;
        }
        Widget[] currentChildren = candidate.getChildren();
        if (parent == candidate && icon != null && currentChildren != null
            && currentChildren.length > 0 && currentChildren[0] == icon) {
            position();
            if (vanilla != null) vanilla.setHidden(true);
            return;
        }
        remove();
        // Never overwrite another widget installed by a plugin.
        Widget[] children = candidate.getChildren();
        if (children != null && children.length > 0 && children[0] != null) {
            Widget existing = children[0];
            Object[] listener = existing.getOnOpListener();
            if (coreEnabled() && existing.getName() != null
                && "Wiki".equals(net.runelite.client.util.Text.removeTags(existing.getName()))
                && listener != null && listener.length > 0 && listener[0] instanceof JavaScriptCallback) {
                if (client.isWidgetSelected()) return;
                coreIcon = existing;
            }
            else {
                advise("Another plugin owns the Wiki button. WikiPane has left it unchanged.");
                return;
            }
        } else if (coreEnabled()) {
            // Respect the core Wiki plugin's hidden-button preference and wait for widget rebuilds.
            return;
        }
        lastGuidance = "";
        parent = candidate;
        position();
        vanilla = coreIcon == null ? client.getWidget(vanillaId) : null;
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
        Widget installedIcon = icon;
        icon.setOnTargetEnterListener((JavaScriptCallback) event -> {
            if (!enabled || icon != installedIcon || !ownsIcon()) return;
            selected = true;
            icon.setSpriteId(SpriteID.WikiIcon.SELECTED);
            client.setAllWidgetsAreOpTargetable(true);
        });
        icon.setOnTargetLeaveListener((JavaScriptCallback) event -> {
            if (icon == installedIcon) deselect();
        });
        Widget originalCore = coreIcon;
        if (originalCore != null) {
            String[] actions = originalCore.getActions();
            if (actions != null && actions.length > 6 && "DPS".equals(actions[6])) icon.setAction(6, "DPS");
        }
        int searchIndex = config.leftSearch() ? 4 : 5;
        icon.setAction(searchIndex, "Search");
        icon.setOnOpListener((JavaScriptCallback) event -> {
            if (enabled && icon == installedIcon && ownsIcon() && event.getOp() - 1 == searchIndex) {
                deselect();
                search.run();
            } else if (enabled && icon == installedIcon && ownsIcon() && event.getOp() == 7 && originalCore != null) {
                Object[] listener = originalCore.getOnOpListener();
                if (listener != null && listener.length > 0 && listener[0] instanceof JavaScriptCallback)
                    ((JavaScriptCallback) listener[0]).run(event);
            }
        });
        icon.revalidate();
    }
    /** Same positioning as RuneLite's Wiki plugin when the vanilla entity-lookup icon is disabled. */
    private void position() {
        if (parent == null || coreIcon != null || client.getVarbitValue(VarbitID.WIKI_ICON_DISABLED) == 0) return;
        if (!positioned) {
            previousX = parent.getOriginalX(); previousY = parent.getOriginalY();
            previousXMode = parent.getXPositionMode(); previousYMode = parent.getYPositionMode();
            positioned = true;
        }
        parent.setOriginalX(client.isResized() ? 0 : 8);
        parent.setOriginalY(135);
        parent.setXPositionMode(WidgetPositionMode.ABSOLUTE_RIGHT);
        parent.setYPositionMode(WidgetPositionMode.ABSOLUTE_TOP);
        parent.revalidate();
    }
    private boolean ownsIcon() {
        Widget[] children = parent == null ? null : parent.getChildren();
        return icon != null && children != null && children.length > 0 && children[0] == icon;
    }
    private boolean searchClick(MenuOptionClicked event) {
        if (!ownsIcon() || (event.getMenuAction() != MenuAction.CC_OP
            && event.getMenuAction() != MenuAction.CC_OP_LOW_PRIORITY)) return false;
        MenuEntry entry = event.getMenuEntry();
        if (entry == null) return false;
        if (entry.getWidget() != icon && (event.getParam1() != icon.getId()
            || event.getParam0() != icon.getIndex())) return false;
        return event.getId() == (config.leftSearch() ? 5 : 6);
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
        if (searchClick(event)) {
            event.consume();
            deselect();
            search.run();
            return;
        }
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
        Widget[] children = parent == null ? null : parent.getChildren();
        boolean owns = ownsIcon();
        boolean vacant = children == null || children.length == 0 || children[0] == null;
        if (owns) children[0] = coreIcon != null && coreEnabled() ? coreIcon : null;
        if (parent != null && (owns || vacant) && positioned
            && parent.getOriginalY() == 135
            && parent.getXPositionMode() == WidgetPositionMode.ABSOLUTE_RIGHT
            && parent.getYPositionMode() == WidgetPositionMode.ABSOLUTE_TOP) {
            parent.setOriginalX(previousX); parent.setOriginalY(previousY);
            parent.setXPositionMode(previousXMode); parent.setYPositionMode(previousYMode);
            parent.revalidate();
        }
        if (vanilla != null && (owns || vacant) && !coreEnabled()
            && client.getVarbitValue(VarbitID.WIKI_ICON_DISABLED) == 0)
            vanilla.setHidden(previousHidden);
        positioned = false;
        icon = null;
        parent = null;
        vanilla = null;
        coreIcon = null;
    }
    public void stop() {
        enabled = false;
        remove();
    }
}
