package com.wikipane.integration;

import com.wikipane.WikiPaneConfig;
import com.wikipane.api.WikiUrls;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import net.runelite.api.*;
import net.runelite.api.events.MenuOpened;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.ItemManager;
import okhttp3.HttpUrl;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class SkillInterfaceIntegrationTest {
    private static final class Game {
        final Client client = mock(Client.class);
        final ItemManager items = mock(ItemManager.class);
        final Menu menu = mock(Menu.class);
        final List<HttpUrl> articles = new ArrayList<>(), entities = new ArrayList<>();
        final List<MenuEntry> added = new ArrayList<>();
        boolean active = true, replacement = true;
        final WikiPaneConfig config = new WikiPaneConfig() {
            public boolean replacement() { return replacement; }
        };
        final SkillInterfaceIntegration integration = new SkillInterfaceIntegration(client, items, config,
            () -> active, articles::add, entities::add);
        Game() {
            when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
            when(client.getMenu()).thenReturn(menu);
            when(menu.createMenuEntry(1)).thenAnswer(call -> {
                MenuEntry entry = callbackEntry(); added.add(entry); return entry;
            });
        }
        @SuppressWarnings("unchecked") MenuEntry callbackEntry() {
            MenuEntry entry = mock(MenuEntry.class, RETURNS_SELF);
            AtomicReference<Consumer<MenuEntry>> callback = new AtomicReference<>();
            when(entry.onClick()).thenAnswer(call -> callback.get());
            doAnswer(call -> { callback.set(call.getArgument(0)); return entry; })
                .when(entry).onClick(nullable(Consumer.class));
            return entry;
        }
        MenuEntry skill(int component, String skill) {
            MenuEntry entry = callbackEntry();
            when(entry.getType()).thenReturn(MenuAction.CC_OP);
            when(entry.getParam1()).thenReturn(component); when(entry.getParam0()).thenReturn(-1);
            when(entry.getTarget()).thenReturn(skill);
            Widget widget = mock(Widget.class); when(client.getWidget(component)).thenReturn(widget);
            when(widget.getParentId()).thenReturn(InterfaceID.Stats.UNIVERSE);
            when(widget.getActions()).thenReturn(new String[]{"View " + skill + " guide", "View experience"});
            return entry;
        }
        void open(MenuEntry... entries) {
            MenuOpened event = new MenuOpened(); event.setMenuEntries(entries);
            integration.opened(event); assertArrayEquals(entries, event.getMenuEntries());
        }
        void click(MenuEntry entry) {
            // Match injected-client ordering: post event, invoke attached callback even if consumed.
            integration.clicked(new MenuOptionClicked(entry));
            if (entry.onClick() != null) entry.onClick().accept(entry);
        }
    }
    @Test public void reusesCoreWikiCallbackWithoutDuplicateEntryOrBrowserLaunch() {
        for (String name : List.of("Farming", "Herblore")) {
            Game g = new Game(); MenuEntry original = g.skill(name.equals("Farming") ? InterfaceID.Stats.FARMING : InterfaceID.Stats.HERBLORE, name);
            MenuEntry wiki = g.callbackEntry(); when(wiki.getType()).thenReturn(MenuAction.RUNELITE);
            when(wiki.getOption()).thenReturn("Wiki"); when(wiki.getTarget()).thenReturn("<col=ff9040>" + name);
            int[] browser = {0}; wiki.onClick(e -> browser[0]++);
            g.open(original, wiki); g.click(wiki);
            assertTrue(g.added.isEmpty()); assertEquals(0, browser[0]);
            assertEquals(name, WikiUrls.title(g.articles.get(0)));
            verify(original, never()).setType(any()); verify(g.menu, never()).setMenuEntries(any());
            // If the opt-in changes while this menu is open, retain its original callback.
            g.replacement = false; g.click(wiki); assertEquals(1, browser[0]);
            assertEquals(1, g.articles.size());
        }
    }
    @Test public void suppliesSingleEquivalentWikiActionWhenCorePluginIsDisabled() {
        Game g = new Game(); MenuEntry original = g.skill(InterfaceID.Stats.FARMING, "Farming");
        g.open(original, original); assertEquals(1, g.added.size());
        MenuEntry wiki = g.added.get(0); verify(wiki).setOption("Wiki"); verify(wiki).setType(MenuAction.RUNELITE);
        g.click(wiki); assertEquals("Farming", WikiUrls.title(g.articles.get(0)));
        g.active = false; g.click(wiki); assertEquals(1, g.articles.size());
    }
    @Test public void unrelatedCallbacksAndOrdinaryGuideActionsAreUnchanged() {
        Game g = new Game(); MenuEntry original = g.skill(InterfaceID.Stats.FARMING, "Farming");
        MenuEntry unrelated = g.callbackEntry(); when(unrelated.getType()).thenReturn(MenuAction.RUNELITE);
        when(unrelated.getOption()).thenReturn("Wiki"); when(unrelated.getTarget()).thenReturn("Unrelated page");
        Consumer<MenuEntry> callback = e -> {}; unrelated.onClick(callback);
        g.open(original, unrelated); assertSame(callback, unrelated.onClick());
        for (String option : List.of("View Farming guide", "View experience", "Check materials", "Check runes", "Close")) {
            when(original.getOption()).thenReturn(option); MenuOptionClicked event = new MenuOptionClicked(original);
            assertFalse(g.integration.clicked(event)); assertFalse(event.isConsumed());
        }
        assertTrue(g.articles.isEmpty()); assertTrue(g.entities.isEmpty());
    }
    @Test public void sameNamedTargetsOutsideVerifiedSkillWidgetsDoNotIntercept() {
        Game g = new Game(); MenuEntry original = g.skill(InterfaceID.Stats.FARMING, "Farming");
        Widget widget = g.client.getWidget(InterfaceID.Stats.FARMING);
        when(widget.getParentId()).thenReturn(InterfaceID.Questlist.LIST);
        g.open(original); assertTrue(g.added.isEmpty());
        when(widget.getParentId()).thenReturn(InterfaceID.Stats.UNIVERSE);
        when(widget.getActions()).thenReturn(new String[]{"View unrelated guide"});
        g.open(original); assertTrue(g.added.isEmpty());
    }
    @Test public void settingsMatrixAndLogoutGuardsMatchQuestRouting() {
        assertFalse(SkillInterfaceIntegration.enabled(new WikiPaneConfig() {}));
        for (boolean replacement : new boolean[]{false, true}) {
            Game g = new Game(); g.replacement = replacement;
            g.open(g.skill(InterfaceID.Stats.HERBLORE, "Herblore"));
            assertEquals(replacement ? 1 : 0, g.added.size());
        }
        Game g = new Game(); MenuEntry original = g.skill(InterfaceID.Stats.FARMING, "Farming");
        when(g.client.getGameState()).thenReturn(GameState.LOGIN_SCREEN); g.open(original);
        assertTrue(g.added.isEmpty()); when(g.client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(g.client.isWidgetSelected()).thenReturn(true); g.open(original); assertTrue(g.added.isEmpty());
    }
    @Test public void nativeGuideWikiUsesCanonicalItemIdAndSuppressesAttachedBrowserCallback() {
        for (int component : new int[]{InterfaceID.SkillGuide.ICONS, InterfaceID.SkillGuideV2.LIST}) {
            Game g = new Game(); MenuEntry entry = g.callbackEntry();
            when(entry.getType()).thenReturn(MenuAction.CC_OP); when(entry.getOption()).thenReturn("Wiki:");
            when(entry.getParam1()).thenReturn(component); when(entry.getParam0()).thenReturn(4);
            when(entry.getItemId()).thenReturn(-1);
            Widget container = mock(Widget.class), icon = mock(Widget.class);
            when(g.client.getWidget(component)).thenReturn(container); when(container.getChild(4)).thenReturn(icon);
            when(icon.getItemId()).thenReturn(ItemID.BRONZE_DAGGER);
            when(g.items.canonicalize(ItemID.BRONZE_DAGGER)).thenReturn(ItemID.BRONZE_DAGGER);
            ItemComposition item = mock(ItemComposition.class); when(item.getMembersName()).thenReturn("Bronze dagger");
            when(g.items.getItemComposition(ItemID.BRONZE_DAGGER)).thenReturn(item);
            int[] browser = {0}; entry.onClick(e -> browser[0]++);
            MenuOptionClicked event = new MenuOptionClicked(entry);
            assertTrue(g.integration.clicked(event)); assertTrue(event.isConsumed());
            if (entry.onClick() != null) entry.onClick().accept(entry);
            assertEquals(0, browser[0]); assertEquals("item", g.entities.get(0).queryParameter("type"));
            assertEquals(Integer.toString(ItemID.BRONZE_DAGGER), g.entities.get(0).queryParameter("id"));
            assertFalse(g.integration.clicked(event)); assertEquals(1, g.entities.size());
        }
    }
    @Test public void missingGuideIdentityAndUnrelatedInterfacesRetainNormalBehavior() {
        Game g = new Game(); MenuEntry entry = g.callbackEntry();
        when(entry.getType()).thenReturn(MenuAction.CC_OP); when(entry.getOption()).thenReturn("Wiki");
        when(entry.getParam1()).thenReturn(InterfaceID.SkillGuideV2.LIST); when(entry.getParam0()).thenReturn(-1);
        Widget widget = mock(Widget.class); when(g.client.getWidget(InterfaceID.SkillGuideV2.LIST)).thenReturn(widget);
        when(entry.getItemId()).thenReturn(-1); when(widget.getItemId()).thenReturn(-1);
        MenuOptionClicked event = new MenuOptionClicked(entry);
        assertFalse(g.integration.clicked(event)); assertFalse(event.isConsumed());
        when(entry.getParam1()).thenReturn(InterfaceID.Questlist.LIST);
        assertFalse(g.integration.clicked(event)); assertTrue(g.entities.isEmpty());
    }
    @Test public void guideUsesExplicitTrainingDestinationInsteadOfGuessingFromItsIcon() {
        Game g = new Game(); MenuEntry entry = g.callbackEntry();
        when(entry.getType()).thenReturn(MenuAction.CC_OP); when(entry.getOption()).thenReturn("Open wiki");
        when(entry.getParam1()).thenReturn(InterfaceID.SkillGuideV2.OVERVIEW_CONTENT);
        when(entry.getParam0()).thenReturn(-1);
        Widget widget = mock(Widget.class); when(g.client.getWidget(entry.getParam1())).thenReturn(widget);
        when(widget.getOnOpListener()).thenReturn(new Object[]{42, WikiUrls.article("Farming training")});
        MenuOptionClicked event = new MenuOptionClicked(entry);
        assertTrue(g.integration.clicked(event)); assertTrue(event.isConsumed());
        assertEquals("Farming training", WikiUrls.title(g.articles.get(0)));
        assertTrue(g.entities.isEmpty()); verifyNoInteractions(g.items);
    }
    @Test public void ambiguousOrUntrustedGuideDestinationsRemainUntouched() {
        for (Object[] listener : new Object[][]{
            {42, WikiUrls.article("Farming"), WikiUrls.article("Farming training")},
            {42, "https://example.com/Farming"},
            {42, "file:///tmp/article"},
            {42, "https://oldschool.runescape.wiki.evil.example/w/Farming"}}) {
            Game g = new Game(); MenuEntry entry = g.callbackEntry();
            when(entry.getType()).thenReturn(MenuAction.CC_OP); when(entry.getOption()).thenReturn("Wiki");
            when(entry.getParam1()).thenReturn(InterfaceID.SkillGuideV2.OVERVIEW_CONTENT);
            when(entry.getParam0()).thenReturn(-1); when(entry.getItemId()).thenReturn(-1);
            Widget widget = mock(Widget.class); when(widget.getItemId()).thenReturn(-1);
            when(g.client.getWidget(entry.getParam1())).thenReturn(widget);
            when(widget.getOnOpListener()).thenReturn(listener);
            MenuOptionClicked event = new MenuOptionClicked(entry);
            assertFalse(g.integration.clicked(event)); assertFalse(event.isConsumed());
            assertTrue(g.articles.isEmpty()); assertTrue(g.entities.isEmpty());
        }
    }
}
