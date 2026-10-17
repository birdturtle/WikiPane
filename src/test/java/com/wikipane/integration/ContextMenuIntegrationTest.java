package com.wikipane.integration;

import com.wikipane.WikiPaneConfig;
import java.util.*;
import java.util.function.Consumer;
import net.runelite.api.*;
import net.runelite.api.events.MenuOpened;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.game.ItemManager;
import okhttp3.HttpUrl;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ContextMenuIntegrationTest {
    private static final class Game {
        final Client client=mock(Client.class);final ItemManager items=mock(ItemManager.class);final Menu menu=mock(Menu.class);
        final List<MenuEntry> added=new ArrayList<>();final List<HttpUrl> opened=new ArrayList<>();
        boolean enabled=true;WikiPaneConfig.ContextAccess access=WikiPaneConfig.ContextAccess.SHIFT_RIGHT_CLICK;
        final WikiPaneConfig config=new WikiPaneConfig(){public boolean contextLookups(){return enabled;}public ContextAccess contextAccess(){return access;}};
        final ContextMenuIntegration integration=new ContextMenuIntegration(client,items,config,opened::add);
        Game(){when(client.getGameState()).thenReturn(GameState.LOGGED_IN);when(client.getMenu()).thenReturn(menu);when(client.isKeyPressed(KeyCode.KC_SHIFT)).thenReturn(true);
            when(items.canonicalize(ItemID.BRONZE_DAGGER)).thenReturn(ItemID.BRONZE_DAGGER);ItemComposition item=mock(ItemComposition.class);when(item.getMembersName()).thenReturn("Bronze dagger");when(items.getItemComposition(ItemID.BRONZE_DAGGER)).thenReturn(item);
            when(menu.createMenuEntry(1)).thenAnswer(call->{MenuEntry entry=mock(MenuEntry.class,RETURNS_SELF);added.add(entry);return entry;});}
        MenuEntry entry(MenuAction action){MenuEntry entry=mock(MenuEntry.class);when(entry.getType()).thenReturn(action);when(entry.getItemId()).thenReturn(ItemID.BRONZE_DAGGER);when(entry.getIdentifier()).thenReturn(ItemID.BRONZE_DAGGER);when(entry.getTarget()).thenReturn("Bronze dagger");return entry;}
        void open(MenuEntry...entries){MenuOpened event=new MenuOpened();event.setMenuEntries(entries);integration.opened(event);assertArrayEquals(entries,event.getMenuEntries());}
    }
    @Test public void supportedItemInterfacesCanonicalizeWithoutChangingOriginalMenuEntries() {
        for(MenuAction action:List.of(MenuAction.CC_OP,MenuAction.CC_OP_LOW_PRIORITY,MenuAction.ITEM_FIRST_OPTION,MenuAction.WIDGET_FIRST_OPTION,MenuAction.EXAMINE_ITEM_GROUND)){
            Game g=new Game();MenuEntry original=g.entry(action),swapper=mock(MenuEntry.class);when(swapper.getType()).thenReturn(MenuAction.RUNELITE);
            g.open(original,swapper);assertEquals(1,g.added.size());verify(g.added.get(0)).setType(MenuAction.RUNELITE);verify(g.menu,never()).setMenuEntries(any());
            verify(original,never()).setType(any());verify(swapper,never()).setType(any());verify(g.items).canonicalize(ItemID.BRONZE_DAGGER);
        }
    }
    @Test public void duplicateActionsForSameEntityAddOneLookupAndMissingItemMetadataAddsNone() {
        Game g=new Game();g.open(g.entry(MenuAction.CC_OP),g.entry(MenuAction.EXAMINE_ITEM));assertEquals(1,g.added.size());
        Game missing=new Game();MenuEntry entry=missing.entry(MenuAction.CC_OP);when(entry.getItemId()).thenReturn(-1);missing.open(entry);assertTrue(missing.added.isEmpty());
    }
    @Test public void optOutModifiersAndTargetSelectionLeaveOrdinaryMenusAlone() {
        assertFalse(new WikiPaneConfig(){}.contextLookups());Game g=new Game();g.enabled=false;g.open(g.entry(MenuAction.CC_OP));assertTrue(g.added.isEmpty());
        g.enabled=true;when(g.client.isKeyPressed(KeyCode.KC_SHIFT)).thenReturn(false);g.open(g.entry(MenuAction.CC_OP));assertTrue(g.added.isEmpty());
        g.access=WikiPaneConfig.ContextAccess.RIGHT_CLICK;g.open(g.entry(MenuAction.CC_OP));assertEquals(1,g.added.size());
        when(g.client.isWidgetSelected()).thenReturn(true);g.open(g.entry(MenuAction.CC_OP));assertEquals(1,g.added.size());
    }
    @Test @SuppressWarnings("unchecked") public void staleMenuCallbacksDoNotLookupAfterLogoutOrOptOut() {
        Game g=new Game();g.open(g.entry(MenuAction.CC_OP));
        org.mockito.ArgumentCaptor<Consumer> callback=org.mockito.ArgumentCaptor.forClass(Consumer.class);verify(g.added.get(0)).onClick(callback.capture());
        when(g.client.getGameState()).thenReturn(GameState.LOGIN_SCREEN);callback.getValue().accept(g.added.get(0));assertTrue(g.opened.isEmpty());
        when(g.client.getGameState()).thenReturn(GameState.LOGGED_IN);g.enabled=false;callback.getValue().accept(g.added.get(0));assertTrue(g.opened.isEmpty());
        g.enabled=true;callback.getValue().accept(g.added.get(0));assertEquals("1205",g.opened.get(0).queryParameter("id"));
    }
    @Test public void transformedNpcAndObjectsRetainExactIdentity() {
        Game g=new Game();MenuEntry entry=mock(MenuEntry.class);when(entry.getType()).thenReturn(MenuAction.EXAMINE_NPC);
        NPC npc=mock(NPC.class);NPCComposition variant=mock(NPCComposition.class);when(entry.getNpc()).thenReturn(npc);when(npc.getTransformedComposition()).thenReturn(variant);when(variant.getId()).thenReturn(8061);when(variant.getName()).thenReturn("Vorkath");
        HttpUrl npcUrl=GameEntityLookup.context(g.client,g.items,entry);assertEquals("8061",npcUrl.queryParameter("id"));assertEquals("npc",npcUrl.queryParameter("type"));
        MenuEntry objectEntry=mock(MenuEntry.class);when(objectEntry.getType()).thenReturn(MenuAction.EXAMINE_OBJECT);when(objectEntry.getIdentifier()).thenReturn(100);
        when(objectEntry.getParam0()).thenReturn(10);when(objectEntry.getParam1()).thenReturn(12);when(objectEntry.getWorldViewId()).thenReturn(-1);
        ObjectComposition object=mock(ObjectComposition.class),transformed=mock(ObjectComposition.class);when(g.client.getObjectDefinition(100)).thenReturn(object);when(object.getImpostorIds()).thenReturn(new int[]{101});when(object.getImpostor()).thenReturn(transformed);when(transformed.getId()).thenReturn(101);when(transformed.getName()).thenReturn("Tree");
        WorldView view=mock(WorldView.class);when(g.client.getWorldView(-1)).thenReturn(view);when(view.getSizeX()).thenReturn(104);when(view.getSizeY()).thenReturn(104);when(view.getBaseX()).thenReturn(3200);when(view.getBaseY()).thenReturn(3200);
        HttpUrl objectUrl=GameEntityLookup.context(g.client,g.items,objectEntry);assertEquals("101",objectUrl.queryParameter("id"));assertEquals("3210",objectUrl.queryParameter("x"));assertEquals("3212",objectUrl.queryParameter("y"));
    }
}
