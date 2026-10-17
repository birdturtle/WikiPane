package com.wikipane.integration;

import com.wikipane.WikiPaneConfig;
import com.wikipane.api.WikiUrls;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.runelite.api.*;
import net.runelite.api.events.MenuOpened;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetUtil;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.Text;
import okhttp3.HttpUrl;
import lombok.extern.slf4j.Slf4j;

/** Informational Wiki actions only; game skill-guide operations are never replaced. */
@Slf4j
public final class SkillInterfaceIntegration {
    private static final Map<Integer, Skill> SKILLS = Map.ofEntries(
        Map.entry(InterfaceID.Stats.ATTACK, Skill.ATTACK), Map.entry(InterfaceID.Stats.STRENGTH, Skill.STRENGTH),
        Map.entry(InterfaceID.Stats.DEFENCE, Skill.DEFENCE), Map.entry(InterfaceID.Stats.RANGED, Skill.RANGED),
        Map.entry(InterfaceID.Stats.PRAYER, Skill.PRAYER), Map.entry(InterfaceID.Stats.MAGIC, Skill.MAGIC),
        Map.entry(InterfaceID.Stats.RUNECRAFT, Skill.RUNECRAFT), Map.entry(InterfaceID.Stats.CONSTRUCTION, Skill.CONSTRUCTION),
        Map.entry(InterfaceID.Stats.HITPOINTS, Skill.HITPOINTS), Map.entry(InterfaceID.Stats.AGILITY, Skill.AGILITY),
        Map.entry(InterfaceID.Stats.HERBLORE, Skill.HERBLORE), Map.entry(InterfaceID.Stats.THIEVING, Skill.THIEVING),
        Map.entry(InterfaceID.Stats.CRAFTING, Skill.CRAFTING), Map.entry(InterfaceID.Stats.FLETCHING, Skill.FLETCHING),
        Map.entry(InterfaceID.Stats.SLAYER, Skill.SLAYER), Map.entry(InterfaceID.Stats.HUNTER, Skill.HUNTER),
        Map.entry(InterfaceID.Stats.MINING, Skill.MINING), Map.entry(InterfaceID.Stats.SMITHING, Skill.SMITHING),
        Map.entry(InterfaceID.Stats.FISHING, Skill.FISHING), Map.entry(InterfaceID.Stats.COOKING, Skill.COOKING),
        Map.entry(InterfaceID.Stats.FIREMAKING, Skill.FIREMAKING), Map.entry(InterfaceID.Stats.WOODCUTTING, Skill.WOODCUTTING),
        Map.entry(InterfaceID.Stats.FARMING, Skill.FARMING), Map.entry(InterfaceID.Stats.SAILING, Skill.SAILING));
    private final Client client;
    private final ItemManager items;
    private final WikiPaneConfig config;
    private final BooleanSupplier active;
    private final Consumer<HttpUrl> article;
    private final Consumer<HttpUrl> entity;

    public SkillInterfaceIntegration(Client client, ItemManager items, WikiPaneConfig config,
        BooleanSupplier active, Consumer<HttpUrl> article, Consumer<HttpUrl> entity) {
        this.client = client; this.items = items; this.config = config; this.active = active;
        this.article = article; this.entity = entity;
    }
    public static boolean enabled(WikiPaneConfig config) {
        return config.skillLookups() || config.replacement() && config.destination() == WikiPaneConfig.Destination.SIDEBAR;
    }
    private boolean available() {
        return active.getAsBoolean() && enabled(config) && client.getGameState() == GameState.LOGGED_IN
            && !client.isWidgetSelected();
    }
    private static boolean wikiAction(String value) {
        String option = clean(value).toLowerCase(Locale.ROOT);
        if (option.endsWith(":")) option = option.substring(0, option.length() - 1).trim();
        return option.equals("wiki") || option.equals("open wiki") || option.equals("wiki lookup");
    }
    private static String clean(String value) { return value == null ? "" : Text.removeTags(value).trim(); }
    private Widget widget(MenuEntry entry) {
        Widget widget = client.getWidget(entry.getParam1());
        return widget != null && entry.getParam0() >= 0 ? widget.getChild(entry.getParam0()) : widget;
    }
    private Skill skill(MenuEntry entry) {
        Skill skill = SKILLS.get(entry.getParam1());
        if (skill == null || (entry.getType() != MenuAction.CC_OP && entry.getType() != MenuAction.CC_OP_LOW_PRIORITY)) return null;
        Widget widget = widget(entry);
        if (widget == null || widget.isHidden() || widget.getParentId() != InterfaceID.Stats.UNIVERSE) return null;
        String[] actions = widget.getActions();
        if (actions == null) return null;
        for (String action : actions)
            if (clean(action).equalsIgnoreCase("View " + skill.getName() + " guide")) return skill;
        return null;
    }
    public void opened(MenuOpened event) {
        if (!available()) return;
        MenuEntry[] entries = event.getMenuEntries();
        Set<Skill> seen = new HashSet<>();
        for (MenuEntry original : entries) {
            Skill skill = skill(original);
            if (skill == null || !seen.add(skill)) continue;
            HttpUrl url = HttpUrl.get(WikiUrls.article(skill.getName()));
            MenuEntry existing = null;
            for (MenuEntry entry : entries) {
                // Core Wiki's entry has no widget params; tie it to this menu's verified skill operation.
                if (entry.getType() == MenuAction.RUNELITE && wikiAction(entry.getOption())
                    && clean(entry.getTarget()).equalsIgnoreCase(skill.getName())) { existing = entry; break; }
            }
            log.debug("Skill Wiki menu prepared: component={}, reusingEntry={}", original.getParam1(), existing != null);
            if (existing != null) {
                Consumer<MenuEntry> browser = existing.onClick();
                existing.onClick(clicked -> {
                    if (available()) article.accept(url);
                    else if (browser != null) browser.accept(clicked);
                });
            } else {
                client.getMenu().createMenuEntry(1).setOption("Wiki")
                    .setTarget(net.runelite.client.ui.JagexColors.MENU_TARGET_TAG + skill.getName())
                    .setType(MenuAction.RUNELITE).onClick(clicked -> { if (available()) article.accept(url); });
            }
        }
    }
    /** Native level-guide Wiki item actions retain canonical item identity. */
    public boolean clicked(MenuOptionClicked event) {
        if (event.isConsumed() || !wikiAction(event.getMenuOption())) return false;
        MenuEntry entry = event.getMenuEntry();
        if (entry.getType() != MenuAction.CC_OP && entry.getType() != MenuAction.CC_OP_LOW_PRIORITY) return false;
        int group = WidgetUtil.componentToInterface(entry.getParam1());
        if (group != InterfaceID.SKILL_GUIDE && group != InterfaceID.SKILL_GUIDE_V2) return false;
        log.debug("Skill-guide Wiki click: enabled={}, action={}, component={}, child={}",
            enabled(config), entry.getType(), entry.getParam1(), entry.getParam0());
        if (!available()) return false;
        Widget widget = widget(entry);
        if (widget == null || widget.isHidden()) return false;
        Object[] listener = widget.getOnOpListener();
        HttpUrl explicit = wikiDestination(listener);
        if (explicit != null) {
            entry.onClick(null);
            event.consume();
            article.accept(explicit);
            log.debug("Skill-guide Wiki click routed using explicit article destination");
            return true;
        }
        if (listener != null) for (Object argument : listener)
            if (argument instanceof String && HttpUrl.parse((String) argument) != null) return false;
        int item = entry.getItemId() >= 0 ? entry.getItemId() : widget.getItemId();
        if (item < 0 || item == net.runelite.api.gameval.ItemID.BLANKOBJECT) {
            log.debug("Skill-guide Wiki click left unchanged: no verified item or explicit article destination");
            return false;
        }
        int canonical = items.canonicalize(item);
        ItemComposition definition = items.getItemComposition(canonical);
        String name = definition == null ? "" : clean(definition.getMembersName());
        if (canonical < 0 || name.isEmpty() || name.equals("null")) return false;
        HttpUrl url = GameEntityLookup.url("item", canonical, name, null);
        // RuneLite invokes attached callbacks even for consumed events. Suppress only this handled Wiki action.
        entry.onClick(null);
        event.consume();
        entity.accept(url);
        log.debug("Skill-guide Wiki click routed using canonical item identity");
        return true;
    }
    /** Use a script's explicit article URL only when its destination is unambiguous and Wiki-owned. */
    private static HttpUrl wikiDestination(Object[] listener) {
        if (listener == null) return null;
        HttpUrl result = null;
        for (Object argument : listener) {
            if (!(argument instanceof String)) continue;
            HttpUrl url = HttpUrl.parse((String) argument);
            if (url == null) continue;
            if (!WikiUrls.wiki(url) || WikiUrls.title(url) == null || WikiUrls.title(url).isBlank()
                || WikiUrls.title(url).startsWith("Special:")) return null;
            if (result != null && !result.equals(url)) return null;
            result = url;
        }
        return result;
    }
}
