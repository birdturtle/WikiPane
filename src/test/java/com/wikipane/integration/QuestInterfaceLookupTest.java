package com.wikipane.integration;

import com.wikipane.WikiPaneConfig;
import com.wikipane.api.WikiUrls;
import java.util.ArrayList;
import java.util.List;
import net.runelite.api.*;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import okhttp3.HttpUrl;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class QuestInterfaceLookupTest {
    private final Client client = mock(Client.class);
    private final Widget container = mock(Widget.class), row = mock(Widget.class);
    private MenuEntry entry(String title) {
        MenuEntry entry = mock(MenuEntry.class);
        when(entry.getType()).thenReturn(MenuAction.CC_OP);
        when(entry.getParam1()).thenReturn(InterfaceID.Questlist.LIST);
        when(entry.getParam0()).thenReturn(17);
        when(entry.getTarget()).thenReturn(title);
        when(client.getWidget(InterfaceID.Questlist.LIST)).thenReturn(container);
        when(container.getChild(17)).thenReturn(row);
        when(row.getText()).thenReturn(title);
        return entry;
    }
    @Test public void coloredQuestNamesResolveWithoutUsingChildIndexAsIdentity() {
        for (String title : List.of("Cook's Assistant", "Recipe for Disaster", "Dragon Slayer II")) {
            HttpUrl url = QuestInterfaceLookup.resolve(client, entry("<col=00ff00>" + title + "</col>"));
            assertNotNull(url); assertEquals(title, WikiUrls.title(url));
            assertNull(url.queryParameter("id"));
        }
        MenuEntry entry = entry("Cook’s Assistant");
        assertEquals("Cook's Assistant", WikiUrls.title(QuestInterfaceLookup.resolve(client, entry)));
    }
    @Test public void unrelatedWidgetsHeadersAndAmbiguousTargetsAreRejected() {
        MenuEntry entry = entry("Cook's Assistant");
        when(entry.getParam1()).thenReturn(InterfaceID.Stats.COOKING);
        assertNull(QuestInterfaceLookup.resolve(client, entry));
        entry = entry("Quest points"); assertNull(QuestInterfaceLookup.resolve(client, entry));
        entry = entry("Cook's Assistant"); when(entry.getTarget()).thenReturn("Recipe for Disaster");
        assertNull(QuestInterfaceLookup.resolve(client, entry));
        when(entry.getTarget()).thenReturn(""); when(row.isHidden()).thenReturn(true);
        assertNull(QuestInterfaceLookup.resolve(client, entry));
        when(row.isHidden()).thenReturn(false); when(container.getChild(17)).thenReturn(null);
        assertNull(QuestInterfaceLookup.resolve(client, entry));
    }
    @Test public void existingGuideActionsOpenMatchingPagesWithoutAddingMenuEntries() {
        assertFalse(new WikiPaneConfig() {}.replacement());
        WikiPaneConfig config = new WikiPaneConfig() { public boolean replacement() { return true; } };
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        for (String option : List.of("Open wiki guide", "Open wiki quick guide",
                "Open wiki guide:", "Open wiki quick guide:",
                "<col=ffffff>Open wiki guide:</col>")) {
            MenuEntry original = entry("Cook's Assistant");
            when(original.getOption()).thenReturn(option);
            net.runelite.api.events.MenuOptionClicked event = new net.runelite.api.events.MenuOptionClicked(original);
            List<HttpUrl> full = new ArrayList<>();
            assertTrue(QuestInterfaceLookup.clicked(client, config, event, full::add));
            assertTrue(event.isConsumed());
            assertEquals("Cook's Assistant" + (option.contains("quick") ? "/Quick guide" : ""), WikiUrls.title(full.get(0)));
            assertTrue(full.get(0).isHttps());
            verify(original, never()).setType(any());
            verify(client, never()).getMenu();
            assertFalse(QuestInterfaceLookup.clicked(client, config, event, full::add));
            assertEquals(1, full.size());
        }
    }
    @Test public void journalMapAndUnrelatedBrowserActionsRemainUntouched() {
        WikiPaneConfig config = new WikiPaneConfig() { public boolean replacement() { return true; } };
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        for (String option : List.of("Read journal", "Show on map", "Read journal:", "Show on map:",
                "Open wiki", "Wiki", "Cancel", "Open wiki guide: unexpected")) {
            MenuEntry original = entry("Recipe for Disaster"); when(original.getOption()).thenReturn(option);
            net.runelite.api.events.MenuOptionClicked event = new net.runelite.api.events.MenuOptionClicked(original);
            assertFalse(QuestInterfaceLookup.clicked(client, config, event, u -> fail()));
            assertFalse(event.isConsumed());
        }
        MenuEntry original = entry("Cook's Assistant"); when(original.getOption()).thenReturn("Open wiki guide");
        when(original.getParam1()).thenReturn(InterfaceID.Stats.COOKING);
        net.runelite.api.events.MenuOptionClicked event = new net.runelite.api.events.MenuOptionClicked(original);
        assertFalse(QuestInterfaceLookup.clicked(client, config, event, u -> fail()));
        assertFalse(event.isConsumed());
    }
    @Test public void disabledLoggedOutAndTargetSelectionKeepBrowserBehavior() {
        boolean[] enabled = {false};
        WikiPaneConfig config = new WikiPaneConfig() { public boolean replacement() { return enabled[0]; } };
        MenuEntry original = entry("Recipe for Disaster"); when(original.getOption()).thenReturn("Open wiki guide");
        net.runelite.api.events.MenuOptionClicked event = new net.runelite.api.events.MenuOptionClicked(original);
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        assertFalse(QuestInterfaceLookup.clicked(client, config, event, u -> fail()));
        enabled[0] = true; when(client.getGameState()).thenReturn(GameState.LOGIN_SCREEN);
        assertFalse(QuestInterfaceLookup.clicked(client, config, event, u -> fail()));
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN); when(client.isWidgetSelected()).thenReturn(true);
        assertFalse(QuestInterfaceLookup.clicked(client, config, event, u -> fail()));
        assertFalse(event.isConsumed());
    }
    @Test public void unifiedIntegrationRoutesBothQuestGuideActionsAndOptOutLeavesThemAlone() {
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        for (boolean enabled : new boolean[]{false, true}) {
            WikiPaneConfig config = new WikiPaneConfig() { public boolean replacement() { return enabled; } };
            for (String option : List.of("Open wiki guide:", "Open wiki quick guide:")) {
                MenuEntry original = entry("Cook's Assistant");
                when(original.getParam0()).thenReturn(11); when(container.getChild(11)).thenReturn(row);
                when(original.getOption()).thenReturn(option);
                net.runelite.api.events.MenuOptionClicked event = new net.runelite.api.events.MenuOptionClicked(original);
                List<HttpUrl> opened = new ArrayList<>();
                assertEquals(enabled, QuestInterfaceLookup.clicked(client, config, event, opened::add));
                assertEquals(enabled, event.isConsumed()); assertEquals(enabled ? 1 : 0, opened.size());
                if (enabled) assertEquals("Cook's Assistant" + (option.contains("quick") ? "/Quick guide" : ""), WikiUrls.title(opened.get(0)));
            }
        }
    }
}
