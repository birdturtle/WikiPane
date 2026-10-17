package com.wikipane.integration;

import com.wikipane.api.WikiUrls;
import com.wikipane.WikiPaneConfig;
import java.util.function.Consumer;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.GameState;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.Quest;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.util.Text;
import okhttp3.HttpUrl;
import lombok.extern.slf4j.Slf4j;

/** Read-only quest-list lookup. Never interprets child indexes as quest IDs. */
@Slf4j
public final class QuestInterfaceLookup {
    private static final Map<String, String> TITLES = new HashMap<>();
    static {
        for (Quest quest : Quest.values()) TITLES.put(key(quest.getName()), quest.getName());
    }
    private QuestInterfaceLookup() {}

    public static boolean enabled(WikiPaneConfig config) {
        return config.questLookups() || config.replacement()
            && config.destination() == WikiPaneConfig.Destination.SIDEBAR;
    }

    /** Only the game's two informational guide actions are consumed, before their widget script runs. */
    public static boolean clicked(Client client, WikiPaneConfig config, MenuOptionClicked event,
                                  Consumer<HttpUrl> article) {
        String option = key(event.getMenuOption());
        // The game's quest list uses a trailing colon before the quest-name target.
        if (option.endsWith(":")) option = option.substring(0, option.length() - 1).trim();
        boolean quick = option.equals("open wiki quick guide");
        if (!quick && !option.equals("open wiki guide")) return false;
        log.debug("Quest guide click: enabled={}, action={}, component={}, child={}, state={}, targetSelected={}, consumed={}",
            enabled(config), event.getMenuAction(), event.getParam1(), event.getParam0(),
            client.getGameState(), client.isWidgetSelected(), event.isConsumed());
        if (!enabled(config) || event.isConsumed() || client.getGameState() != GameState.LOGGED_IN
            || client.isWidgetSelected()) return false;
        HttpUrl resolved = resolve(client, event.getMenuEntry());
        if (resolved == null) {
            log.debug("Quest guide click left unchanged: quest row could not be verified");
            return false;
        }
        HttpUrl destination = quick
            ? HttpUrl.get(WikiUrls.article(WikiUrls.title(resolved) + "/Quick guide")) : resolved;
        event.consume();
        log.debug("Quest guide click routed to WikiPane (quick={})", quick);
        article.accept(destination);
        return true;
    }

    public static HttpUrl resolve(Client client, MenuEntry entry) {
        if (entry.getType() != MenuAction.CC_OP && entry.getType() != MenuAction.CC_OP_LOW_PRIORITY)
            return null;
        int component = entry.getParam1();
        // Quest list text and its actionable list are distinct containers in the game interface.
        if (component != InterfaceID.Questlist.LIST && component != InterfaceID.Questlist.TEXT_CONTAINER)
            return null;
        Widget widget = client.getWidget(component);
        if (widget == null || entry.getParam0() < 0) return null;
        widget = widget.getChild(entry.getParam0());
        if (widget == null || widget.isHidden()) return null;
        String title = TITLES.get(key(widget.getText()));
        if (title == null) title = TITLES.get(key(widget.getName()));
        if (title == null) return null;
        // A catalog match in the widget is required; an arbitrary menu target is insufficient.
        String target = key(entry.getTarget());
        if (!target.isEmpty() && !target.equals(key(title))) return null;
        return HttpUrl.get(WikiUrls.article(title));
    }

    private static String key(String value) {
        return value == null ? "" : Text.removeTags(value).replace('\u00a0', ' ')
            .replace('\u2019', '\'').trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}
