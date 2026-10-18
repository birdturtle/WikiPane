package com.wikipane.integration;

import com.wikipane.WikiPaneConfig;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import net.runelite.api.*;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.*;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.PluginManager;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Mock API fixtures only. No live client, game input, reflection in plugin code, or screenshots. */
public class WikiButtonIntegrationTest {
    private static final class Game {
        final Client client = mock(Client.class);
        final ItemManager items = mock(ItemManager.class);
        final PluginManager plugins = mock(PluginManager.class);
        final Widget parent = mock(Widget.class), vanilla = mock(Widget.class);
        final AtomicReference<Widget[]> children = new AtomicReference<>();
        final List<Widget> created = new ArrayList<>();
        final List<String> messages = new ArrayList<>();
        final List<String> searches = new ArrayList<>();
        final List<okhttp3.HttpUrl> lookups = new ArrayList<>();
        boolean leftSearch;
        final WikiPaneConfig config = new WikiPaneConfig() {
            public boolean replacement() { return true; }
            public boolean leftSearch() { return Game.this.leftSearch; }
        };
        final WikiButtonIntegration integration;
        Game() {
            when(plugins.getPlugins()).thenReturn(Collections.emptyList());
            when(client.getWidget(InterfaceID.Orbs.WIKI)).thenReturn(parent);
            when(client.getWidget(InterfaceID.Orbs.WIKI_ICON)).thenReturn(vanilla);
            when(parent.getChildren()).thenAnswer(call -> children.get());
            when(parent.getOriginalX()).thenReturn(37);
            when(parent.getOriginalY()).thenReturn(46);
            when(parent.createChild(0, WidgetType.GRAPHIC)).thenAnswer(call -> {
                Widget icon = mock(Widget.class);
                when(icon.getId()).thenReturn(InterfaceID.Orbs.WIKI);
                when(icon.getIndex()).thenReturn(0);
                children.set(new Widget[]{icon}); created.add(icon); return icon;
            });
            integration = new WikiButtonIntegration(client, items, plugins, config,
                lookups::add, () -> searches.add("search"), messages::add);
        }
    }
    @Test public void replacementInstallsWhenVanillaEntityLookupIsDisabled() {
        Game game = new Game();
        when(game.client.getVarbitValue(VarbitID.WIKI_ICON_DISABLED)).thenReturn(1);
        game.integration.reconcile();
        assertEquals(1, game.created.size());
        verify(game.parent).setOriginalY(135);
        verify(game.vanilla).setHidden(true);
    }
    @Test public void detachedWidgetIsRecreatedAfterInterfaceRebuild() {
        Game game = new Game();
        game.integration.reconcile();
        game.children.set(new Widget[]{null});
        game.integration.reconcile();
        assertEquals(2, game.created.size());
        assertSame(game.created.get(1), game.children.get()[0]);
    }
    @Test public void searchClickIsConsumedBeforeTheVanillaScriptCanRun() {
        Game game = new Game();
        game.integration.reconcile();
        MenuOptionClicked event = mock(MenuOptionClicked.class);
        MenuEntry entry = mock(MenuEntry.class);
        when(event.getMenuAction()).thenReturn(MenuAction.CC_OP);
        when(event.getMenuEntry()).thenReturn(entry);
        when(event.getId()).thenReturn(6); // Wiki's zero-based Search action index 5.
        when(entry.getWidget()).thenReturn(game.created.get(0));
        game.integration.clicked(event);
        assertEquals(List.of("search"), game.searches);
        verify(event).consume();
    }
    @Test public void searchRecognizesOwnedDynamicChildWhenTheApiReturnsItsParent() {
        Game game = new Game();
        game.integration.reconcile();
        MenuOptionClicked event = mock(MenuOptionClicked.class);
        MenuEntry entry = mock(MenuEntry.class);
        when(event.getMenuAction()).thenReturn(MenuAction.CC_OP_LOW_PRIORITY);
        when(event.getMenuEntry()).thenReturn(entry);
        when(event.getId()).thenReturn(6);
        when(event.getParam1()).thenReturn(InterfaceID.Orbs.WIKI);
        when(event.getParam0()).thenReturn(0);
        when(entry.getWidget()).thenReturn(game.parent);
        game.integration.clicked(event);
        assertEquals(List.of("search"), game.searches);
        verify(event).consume();
    }
    @Test public void leftClickSearchUsesItsCorrectActionIndex() {
        Game game = new Game();
        game.leftSearch = true;
        game.integration.reconcile();
        MenuOptionClicked event = mock(MenuOptionClicked.class);
        MenuEntry entry = mock(MenuEntry.class);
        when(event.getMenuAction()).thenReturn(MenuAction.CC_OP);
        when(event.getMenuEntry()).thenReturn(entry);
        when(event.getId()).thenReturn(5);
        when(entry.getWidget()).thenReturn(game.created.get(0));
        game.integration.clicked(event);
        assertEquals(List.of("search"), game.searches);
        verify(event).consume();
        verify(game.created.get(0)).setAction(4, "Search");
    }
    @Test public void targetSelectionRoutesExactEntityAndExitsSelection() {
        Game game = new Game();
        game.integration.reconcile();
        org.mockito.ArgumentCaptor<JavaScriptCallback> enter = org.mockito.ArgumentCaptor.forClass(JavaScriptCallback.class);
        verify(game.created.get(0)).setOnTargetEnterListener(enter.capture());
        enter.getValue().run(mock(ScriptEvent.class));
        ItemComposition item = mock(ItemComposition.class);
        when(game.items.canonicalize(1205)).thenReturn(1205);
        when(game.items.getItemComposition(1205)).thenReturn(item);
        when(item.getMembersName()).thenReturn("Bronze dagger");
        MenuOptionClicked event = mock(MenuOptionClicked.class);
        when(event.getMenuAction()).thenReturn(MenuAction.WIDGET_TARGET_ON_GROUND_ITEM);
        when(event.getId()).thenReturn(1205);
        game.integration.clicked(event);
        verify(event).consume();
        verify(game.client).setWidgetSelected(false);
        assertEquals(1, game.lookups.size());
        assertEquals("1205", game.lookups.get(0).queryParameter("id"));
        assertEquals("item", game.lookups.get(0).queryParameter("type"));
    }
    @Test public void disablingRestoresOwnedParentLayoutAndDoesNotUnhideADisabledGameIcon() {
        Game game = new Game();
        when(game.client.getVarbitValue(VarbitID.WIKI_ICON_DISABLED)).thenReturn(1);
        game.integration.reconcile();
        when(game.parent.getOriginalY()).thenReturn(135);
        when(game.parent.getXPositionMode()).thenReturn(WidgetPositionMode.ABSOLUTE_RIGHT);
        when(game.parent.getYPositionMode()).thenReturn(WidgetPositionMode.ABSOLUTE_TOP);
        game.integration.stop();
        assertNull(game.children.get()[0]);
        verify(game.parent).setOriginalX(37);
        verify(game.parent).setOriginalY(46);
        verify(game.vanilla, never()).setHidden(false);
    }
    @Test public void coreHiddenButtonIsRespectedAndNeverAutomaticallyDisabled() {
        Game game = new Game();
        net.runelite.client.plugins.wiki.WikiPlugin core = mock(net.runelite.client.plugins.wiki.WikiPlugin.class);
        when(game.plugins.getPlugins()).thenReturn(Collections.singletonList(core));
        when(game.plugins.isPluginEnabled(core)).thenReturn(true);
        game.integration.reconcile(); game.integration.reconcile();
        assertTrue(game.messages.isEmpty());
        assertTrue(game.created.isEmpty());
        verify(game.plugins, never()).setPluginEnabled(any(), anyBoolean());
    }
    @Test public void coreWidgetIsRestoredAndItsDpsActionIsPreservedWithoutCoreLookupSelection() {
        Game game = new Game();
        net.runelite.client.plugins.wiki.WikiPlugin core = mock(net.runelite.client.plugins.wiki.WikiPlugin.class);
        when(game.plugins.getPlugins()).thenReturn(Collections.singletonList(core));
        when(game.plugins.isPluginEnabled(core)).thenReturn(true);
        Widget original = mock(Widget.class); when(original.getName()).thenReturn("Wiki");
        JavaScriptCallback dps = mock(JavaScriptCallback.class);
        when(original.getOnOpListener()).thenReturn(new Object[]{dps});
        when(original.getActions()).thenReturn(new String[]{null,null,null,null,null,"Search","DPS"});
        game.children.set(new Widget[]{original}); game.integration.reconcile(); game.integration.reconcile();
        assertEquals(1, game.created.size()); verify(game.created.get(0)).setAction(6,"DPS");
        org.mockito.ArgumentCaptor<JavaScriptCallback> op = org.mockito.ArgumentCaptor.forClass(JavaScriptCallback.class);
        verify(game.created.get(0)).setOnOpListener(op.capture());
        ScriptEvent event = mock(ScriptEvent.class); when(event.getOp()).thenReturn(7); op.getValue().run(event);
        verify(dps).run(event); assertTrue(game.searches.isEmpty());
        verify(original,never()).setOnTargetEnterListener(any());
        game.integration.stop(); assertSame(original,game.children.get()[0]);
        verify(game.plugins,never()).setPluginEnabled(any(),anyBoolean());
    }
    @Test public void removedWidgetCallbacksCannotReactivateSelection() {
        Game game = new Game();
        game.integration.reconcile();
        org.mockito.ArgumentCaptor<JavaScriptCallback> enter = org.mockito.ArgumentCaptor.forClass(JavaScriptCallback.class);
        verify(game.created.get(0)).setOnTargetEnterListener(enter.capture());
        game.integration.stop();
        clearInvocations(game.client);
        enter.getValue().run(mock(ScriptEvent.class));
        verify(game.client, never()).setAllWidgetsAreOpTargetable(true);
    }
    @Test public void foreignWidgetAndUnrelatedSearchAreNotIntercepted() {
        Game game = new Game();
        game.integration.reconcile();
        Widget foreign = mock(Widget.class);
        game.children.set(new Widget[]{foreign});
        game.integration.reconcile();
        assertEquals(1, game.created.size());
        assertSame(foreign, game.children.get()[0]);
        MenuOptionClicked event = mock(MenuOptionClicked.class);
        MenuEntry entry = mock(MenuEntry.class);
        when(event.getMenuAction()).thenReturn(MenuAction.CC_OP);
        when(event.getMenuEntry()).thenReturn(entry);
        when(event.getId()).thenReturn(6);
        when(entry.getWidget()).thenReturn(foreign);
        game.integration.clicked(event);
        assertTrue(game.searches.isEmpty());
        verify(event, never()).consume();
    }
}
