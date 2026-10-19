package com.wikipane.integration;

import com.wikipane.DocumentTest;
import com.wikipane.semantic.SemanticDocument.*;
import java.util.*;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.worldmap.WorldMap;
import net.runelite.api.worldmap.WorldMapData;
import net.runelite.client.ui.overlay.worldmap.*;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class WikiMapIntegrationTest {
    @SuppressWarnings("unchecked")
    private WikiMapIntegration integration(Client client,WorldMapPointManager points,List<String> messages) {
        net.runelite.client.game.SpriteManager sprites=mock(net.runelite.client.game.SpriteManager.class);
        doAnswer(call->{((java.util.function.Consumer<java.awt.image.BufferedImage>)call.getArgument(2))
            .accept(new java.awt.image.BufferedImage(18,18,java.awt.image.BufferedImage.TYPE_INT_ARGB));return null;})
            .when(sprites).getSpriteAsync(eq(net.runelite.api.gameval.SpriteID.MAPMARKER),eq(0),
                org.mockito.ArgumentMatchers.<java.util.function.Consumer<java.awt.image.BufferedImage>>any());
        return new WikiMapIntegration(client,points,sprites,Runnable::run,messages::add);
    }
    private final Source source=new Source("Example","https://oldschool.runescape.wiki/w/Example",1,"Start","Start","b1.c0");
    private Location location(String x,String y,String plane) {return new Location(x,y,plane,source,true);}
    @Test public void onlyExplicitIntegralWorldCoordinatesWithKnownPlaneQualify() {
        assertEquals(new WorldPoint(3208,3216,0),WikiMapIntegration.destination(location("3208","3216","0")));
        for(Location invalid:List.of(location("3208","3216",""),location("3208.5","3216","0"),
            location("-1","3000","0"),location("99999999999","3000","0"),location("3000","3000","4"),
            new Location("3208","3216","0",source),
            new Location("3208","3216","0",source,true,"10001"),
            new Location("3208","3216","0",source,true,""))) assertNull(WikiMapIntegration.destination(invalid));
    }
    @Test public void actualQuestMapLinkAndTableCellKeepVerifiedSourceButViewportCentersDoNot() throws Exception {
        List<Location> quest=new ArrayList<>();DocumentTest.document("Cooks_Assistant").semantic.nodes.forEach(n->quest.addAll(n.locations));
        assertTrue(quest.stream().anyMatch(l->l.verifiedMap&&l.x.equals("3208")&&l.y.equals("3216")&&l.plane.equals("0")));
        List<Location> rake=new ArrayList<>();DocumentTest.document("Rake").semantic.nodes.forEach(n->rake.addAll(n.locations));
        assertTrue(rake.stream().anyMatch(l->l.verifiedMap&&l.x.equals("2852")&&l.y.equals("10193")&&l.source.unit.contains(".r")));
        assertTrue(DocumentTest.document("Hans").semantic.nodes.stream().flatMap(n->n.locations.stream()).noneMatch(l->l.verifiedMap));
    }
    @Test public void markerIsBoundedAndOwnedAndWaitsForOpenMapBeforeCentering() {
        Client client=mock(Client.class);when(client.isClientThread()).thenReturn(true);when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        WorldMapPointManager points=mock(WorldMapPointManager.class);List<String> messages=new ArrayList<>();
        WikiMapIntegration integration=integration(client,points,messages);
        integration.show(location("3208","3216","0"));assertTrue(messages.get(0).contains("could not be opened"));
        verify(points,never()).add(any());
        WorldMap map=mock(WorldMap.class);WorldMapData data=mock(WorldMapData.class);
        when(client.getWidget(InterfaceID.Worldmap.MAP_DISPLAY)).thenReturn(mock(Widget.class));when(client.getWorldMap()).thenReturn(map);
        when(map.getWorldMapData()).thenReturn(data);when(data.surfaceContainsPosition(3208,3216)).thenReturn(true);
        assertTrue(integration.center());verify(map).setWorldMapPositionTarget(new WorldPoint(3208,3216,0));
        ArgumentCaptor<WorldMapPoint> marker=ArgumentCaptor.forClass(WorldMapPoint.class);verify(points).add(marker.capture());
        assertEquals(new WorldPoint(3208,3216,0),marker.getValue().getWorldPoint());
        assertEquals("Example · Start<br>Example · Start",marker.getValue().getTooltip());

        integration.center();verify(map,times(1)).setWorldMapPositionTarget(any());
        when(data.surfaceContainsPosition(3209,3217)).thenReturn(true);
        integration.show(location("3209","3217","0"));verify(points).remove(marker.getValue());
        integration.stop();integration.show(location("3208","3216","0"));verify(points,times(2)).add(any());
        verify(points,never()).removeIf(any());
    }
    @Test public void wrongAreaAndLogoutDoNotMoveOrOpenGameInterfaces() {
        Client client=mock(Client.class);when(client.isClientThread()).thenReturn(true);when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(client.getWidget(InterfaceID.Worldmap.MAP_DISPLAY)).thenReturn(mock(Widget.class));
        WorldMap map=mock(WorldMap.class);when(client.getWorldMap()).thenReturn(map);when(map.getWorldMapData()).thenReturn(mock(WorldMapData.class));
        WorldMapPointManager points=mock(WorldMapPointManager.class);List<String> messages=new ArrayList<>();
        WikiMapIntegration integration=integration(client,points,messages);
        integration.show(location("2852","10193","0"));assertTrue(messages.get(0).contains("Wiki map preview"));
        verify(map,never()).setWorldMapPositionTarget(any());
        when(client.getGameState()).thenReturn(GameState.LOGIN_SCREEN);integration.clear();
        integration.show(location("3208","3216","0"));verify(points,never()).add(any());
        when(client.isClientThread()).thenReturn(false);
        try {integration.clear();fail();}catch(IllegalStateException expected){}
        verify(client,never()).runScript(any(Object[].class));
    }
    @Test @SuppressWarnings("unchecked") public void opensNativeFloatingMapAndClosesOnlyItsOwnedInterface() {
        Client client=mock(Client.class);when(client.isClientThread()).thenReturn(true);when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        int parent=InterfaceID.Toplevel.FLOATER;
        when(client.getTopLevelInterfaceId()).thenReturn(net.runelite.api.widgets.WidgetUtil.componentToInterface(parent));
        when(client.getWidget(parent)).thenReturn(mock(Widget.class));
        HashTable<WidgetNode> table=mock(HashTable.class);when(client.getComponentTable()).thenReturn(table);
        WidgetNode node=mock(WidgetNode.class);java.util.concurrent.atomic.AtomicReference<WidgetNode> current=new java.util.concurrent.atomic.AtomicReference<>();
        when(table.get(parent)).thenAnswer(call->current.get());
        when(client.openInterface(parent,InterfaceID.WORLDMAP,net.runelite.api.widgets.WidgetModalMode.NON_MODAL))
            .thenAnswer(call->{current.set(node);return node;});
        WikiMapIntegration integration=integration(client,mock(WorldMapPointManager.class),new ArrayList<>());
        integration.show(location("3208","3216","0"));
        verify(client).openInterface(parent,InterfaceID.WORLDMAP,net.runelite.api.widgets.WidgetModalMode.NON_MODAL);
        MenuEntry close=mock(MenuEntry.class);when(close.getType()).thenReturn(MenuAction.CC_OP);when(close.getParam1()).thenReturn(InterfaceID.Worldmap.CLOSE);
        net.runelite.api.events.MenuOptionClicked event=new net.runelite.api.events.MenuOptionClicked(close);
        assertTrue(integration.clicked(event));assertTrue(event.isConsumed());verify(client).closeInterface(node,false);
        integration.stop();verify(client,times(1)).closeInterface(any(),anyBoolean());
    }
    @Test public void displayProjectionHidesCoordinatesWithoutRemovingSourceData() throws Exception {
        com.wikipane.view.WikiPage page=new com.wikipane.view.WikiPage(DocumentTest.document("Cooks_Assistant"));
        boolean checked=false;
        for(com.wikipane.semantic.SemanticDocument.Node node:page.document.semantic.nodes) if(node.table!=null)
            for(com.wikipane.view.ReaderLayout.Record record:page.layout.records(node.table)) {
                List<com.wikipane.view.ReaderLayout.Field> fields=new ArrayList<>(record.fields);
                if(record.identity!=null)fields.add(record.identity);
                for(com.wikipane.view.ReaderLayout.Field field:fields) if(field.value.html.contains("data-wikipane-map")
                    &&field.value.text.contains("3208")) {
                    assertFalse(field.displayHtml.contains("3208"));assertTrue(field.value.text.contains("3216"));checked=true;
                }
            }
        assertTrue(checked);
    }
    @Test @SuppressWarnings("unchecked") public void lateNativeSpritesCannotRestoreReplacedOrShutdownMarkers() {
        Client client=mock(Client.class);when(client.isClientThread()).thenReturn(true);when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(client.getWidget(InterfaceID.Worldmap.MAP_DISPLAY)).thenReturn(mock(Widget.class));
        WorldMap map=mock(WorldMap.class);WorldMapData data=mock(WorldMapData.class);
        when(client.getWorldMap()).thenReturn(map);when(map.getWorldMapData()).thenReturn(data);
        when(data.surfaceContainsPosition(anyInt(),anyInt())).thenReturn(true);
        net.runelite.client.game.SpriteManager sprites=mock(net.runelite.client.game.SpriteManager.class);
        List<java.util.function.Consumer<java.awt.image.BufferedImage>> callbacks=new ArrayList<>();
        doAnswer(call->{callbacks.add(call.getArgument(2));return null;}).when(sprites).getSpriteAsync(anyInt(),anyInt(),
            org.mockito.ArgumentMatchers.<java.util.function.Consumer<java.awt.image.BufferedImage>>any());
        WorldMapPointManager points=mock(WorldMapPointManager.class);
        WikiMapIntegration integration=new WikiMapIntegration(client,points,sprites,Runnable::run,message->{});
        integration.show(location("3208","3216","0"));integration.show(location("3209","3217","0"));
        java.awt.image.BufferedImage image=new java.awt.image.BufferedImage(18,18,java.awt.image.BufferedImage.TYPE_INT_ARGB);
        callbacks.get(0).accept(image);verify(points,never()).add(any());
        callbacks.get(1).accept(image);verify(points,times(1)).add(any());
        integration.stop();callbacks.get(1).accept(image);verify(points,times(1)).add(any());
    }
    @Test public void groupedSelectionReplacesAllOwnedMarkersAndKeepsTheirSourceMenu() {
        Client client=mock(Client.class);when(client.isClientThread()).thenReturn(true);
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(client.getWidget(InterfaceID.Worldmap.MAP_DISPLAY)).thenReturn(mock(Widget.class));
        WorldMap map=mock(WorldMap.class);WorldMapData data=mock(WorldMapData.class);
        when(client.getWorldMap()).thenReturn(map);when(map.getWorldMapData()).thenReturn(data);
        when(data.surfaceContainsPosition(anyInt(),anyInt())).thenReturn(true);
        WorldMapPointManager points=mock(WorldMapPointManager.class);
        WikiMapIntegration integration=integration(client,points,new ArrayList<>());
        List<String> sources=new ArrayList<>();integration.sourceRequests(sources::add);
        integration.show(com.wikipane.view.MapDestinationTest.group());
        ArgumentCaptor<WorldMapPoint> capture=ArgumentCaptor.forClass(WorldMapPoint.class);
        verify(points,times(2)).add(capture.capture());
        for(WorldMapPoint marker:capture.getAllValues()) {
            assertEquals("WikiPane: West garden",marker.getName());
            assertFalse(marker.isSnapToEdge());
            assertEquals("West garden<br>Example creature · Spawns",marker.getTooltip());
        }
        MenuEntry focus=mock(MenuEntry.class);when(focus.getType()).thenReturn(MenuAction.RUNELITE);
        when(focus.getOption()).thenReturn("Focus on");when(focus.getTarget()).thenReturn("<col=ff9040>WikiPane: West garden</col>");
        MenuEntry read=mock(MenuEntry.class,RETURNS_SELF);when(client.createMenuEntry(-1)).thenReturn(read);
        net.runelite.api.events.MenuOpened opened=new net.runelite.api.events.MenuOpened();
        opened.setMenuEntries(new MenuEntry[]{focus});integration.opened(opened);
        ArgumentCaptor<java.util.function.Consumer<MenuEntry>> action=ArgumentCaptor.forClass(java.util.function.Consumer.class);
        verify(read).onClick(action.capture());verify(read).setOption("Read in WikiPane");
        action.getValue().accept(read);
        assertEquals(List.of("https://oldschool.runescape.wiki/w/Example_creature#Spawns"),sources);
        integration.show(location("3208","3216","0"));
        for(WorldMapPoint marker:capture.getAllValues())verify(points).remove(marker);
        action.getValue().accept(read);assertEquals("Stale menus must not navigate",1,sources.size());
        integration.stop();verify(points,times(3)).remove(any());verify(points,never()).removeIf(any());
    }
    @Test public void anotherPluginsFocusEntryDoesNotReceiveWikiPaneActions() {
        Client client=mock(Client.class);when(client.isClientThread()).thenReturn(true);
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(client.getWidget(InterfaceID.Worldmap.MAP_DISPLAY)).thenReturn(mock(Widget.class));
        WorldMap map=mock(WorldMap.class);WorldMapData data=mock(WorldMapData.class);
        when(client.getWorldMap()).thenReturn(map);when(map.getWorldMapData()).thenReturn(data);
        when(data.surfaceContainsPosition(anyInt(),anyInt())).thenReturn(true);
        WikiMapIntegration integration=integration(client,mock(WorldMapPointManager.class),new ArrayList<>());
        integration.show(location("3208","3216","0"));
        MenuEntry focus=mock(MenuEntry.class);when(focus.getType()).thenReturn(MenuAction.RUNELITE);
        when(focus.getOption()).thenReturn("Focus on");when(focus.getTarget()).thenReturn("Other plugin marker");
        net.runelite.api.events.MenuOpened opened=new net.runelite.api.events.MenuOpened();
        opened.setMenuEntries(new MenuEntry[]{focus});integration.opened(opened);
        verify(client,never()).createMenuEntry(anyInt());
    }

    @Test public void unsupportedMapAreaKeepsThePreviousSelectionAndNeverClaimsSuccess() {
        Client client=mock(Client.class);when(client.isClientThread()).thenReturn(true);when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(client.getWidget(InterfaceID.Worldmap.MAP_DISPLAY)).thenReturn(mock(Widget.class));
        WorldMap map=mock(WorldMap.class);WorldMapData data=mock(WorldMapData.class);
        when(client.getWorldMap()).thenReturn(map);when(map.getWorldMapData()).thenReturn(data);
        when(data.surfaceContainsPosition(3208,3216)).thenReturn(true);
        WorldMapPointManager points=mock(WorldMapPointManager.class);List<String> messages=new ArrayList<>();
        WikiMapIntegration integration=integration(client,points,messages);
        integration.show(location("3208","3216","0"));verify(points).add(any());
        integration.show(location("2852","10193","0"));verify(points,times(1)).add(any());verify(points,never()).remove(any());
        assertTrue(messages.stream().anyMatch(message->message.contains("Wiki map preview")));
        verify(map,times(1)).setWorldMapPositionTarget(any());
        integration.show(location("3208","3216","0"));assertEquals("",messages.get(messages.size()-1));
        integration.stop();verify(points,times(2)).remove(any());
    }

}
