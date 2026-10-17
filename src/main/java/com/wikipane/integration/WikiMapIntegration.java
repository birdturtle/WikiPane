package com.wikipane.integration;

import com.wikipane.semantic.SemanticDocument.Location;
import java.math.BigDecimal;
import java.util.function.Consumer;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.WidgetNode;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.MenuAction;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetModalMode;
import net.runelite.api.widgets.WidgetUtil;
import net.runelite.api.gameval.SpriteID;
import net.runelite.client.game.SpriteManager;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.worldmap.WorldMap;
import net.runelite.client.ui.overlay.worldmap.WorldMapPoint;
import net.runelite.client.ui.overlay.worldmap.WorldMapPointManager;

/** One user-selected local marker; no movement, input injection, or other plugins' marker changes. */
public final class WikiMapIntegration {
    private final Client client;
    private final WorldMapPointManager points;
    private final Consumer<String> feedback;
    private final SpriteManager sprites;
    private final Consumer<Runnable> later;
    private long request;
    private WidgetNode ownedMap;
    private int ownedParent;
    private WorldMapPoint marker;
    private WorldPoint pending;
    private boolean stopped;
    public WikiMapIntegration(Client client, WorldMapPointManager points, SpriteManager sprites,
        Consumer<Runnable> later, Consumer<String> feedback) {
        this.client=client; this.points=points; this.feedback=feedback;
        this.sprites=sprites;this.later=later;
    }
    public static WorldPoint destination(Location location) {
        if (!location.verifiedMap || !java.util.Set.of("0","-1").contains(location.mapId)) return null;
        try {
            int x=new BigDecimal(location.x).intValueExact(),y=new BigDecimal(location.y).intValueExact();
            int plane=Integer.parseInt(location.plane);
            return x>0&&x<16384&&y>0&&y<16384&&plane>=0&&plane<=3 ? new WorldPoint(x,y,plane) : null;
        } catch (ArithmeticException|NumberFormatException ex) { return null; }
    }
    private void checkThread() { if (!client.isClientThread()) throw new IllegalStateException("Map actions require client thread"); }
    public void show(Location location) {
        checkThread(); if(stopped) return;
        WorldPoint point=destination(location);
        if(point==null) {feedback.accept("This Wiki location has no verified map tile and plane.");return;}
        if(client.getGameState()!=GameState.LOGGED_IN) {feedback.accept("Log in to show this location on the world map.");return;}
        clear();long version=request;pending=point;
        sprites.getSpriteAsync(SpriteID.MAPMARKER,0,image -> later.accept(() -> {
            if(stopped||version!=request||client.getGameState()!=GameState.LOGGED_IN||image==null)return;
            marker=new WorldMapPoint(point,destinationIcon(image));marker.setName("WikiPane: "+location.source.article);
            marker.setTooltip("WikiPane: "+location.source.article);marker.setJumpOnClick(true);marker.setSnapToEdge(true);points.add(marker);
        }));
        if(!mapOpen()) openMap();
        center();
    }
    /** Integer scaling keeps the native flag's pixel art crisp and legible among map symbols. */
    private static java.awt.image.BufferedImage destinationIcon(java.awt.image.BufferedImage image) {
        java.awt.image.BufferedImage scaled=new java.awt.image.BufferedImage(
            image.getWidth()*2,image.getHeight()*2,java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D graphics=scaled.createGraphics();
        try {
            graphics.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                java.awt.RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            graphics.drawImage(image,0,0,scaled.getWidth(),scaled.getHeight(),null);
        } finally {graphics.dispose();}
        return scaled;
    }
    private boolean mapOpen() {
        Widget widget=client.getWidget(InterfaceID.Worldmap.MAP_DISPLAY);
        return widget!=null&&!widget.isHidden();
    }
    private void openMap() {
        if(ownedMap!=null&&client.getComponentTable()!=null&&client.getComponentTable().get(ownedParent)==ownedMap)return;
        // Open only the native local floating interface. Never invoke a game/menu action or send input.
        for(int parent:new int[]{InterfaceID.Toplevel.FLOATER,InterfaceID.ToplevelOsrsStretch.FLOATER}) {
            if(WidgetUtil.componentToInterface(parent)!=client.getTopLevelInterfaceId())continue;
            Widget layer=client.getWidget(parent);
            if(layer==null||client.getComponentTable()==null||client.getComponentTable().get(parent)!=null)break;
            try {
                ownedMap=client.openInterface(parent,InterfaceID.WORLDMAP,WidgetModalMode.NON_MODAL);
                ownedParent=parent;return;
            } catch(IllegalStateException ex) {break;}
        }
        feedback.accept("The world map could not be opened in this interface.");
    }
    private void closeOwnedMap() {
        if(ownedMap!=null&&client.getComponentTable()!=null&&client.getComponentTable().get(ownedParent)==ownedMap)
            client.closeInterface(ownedMap,false);
        ownedMap=null;
        pending=null;
    }
    public boolean clicked(MenuOptionClicked event) {
        checkThread();
        if(stopped||ownedMap==null||event.isConsumed()||event.getParam1()!=InterfaceID.Worldmap.CLOSE
            ||(event.getMenuAction()!=MenuAction.CC_OP&&event.getMenuAction()!=MenuAction.CC_OP_LOW_PRIORITY))return false;
        if(client.getComponentTable()==null||client.getComponentTable().get(ownedParent)!=ownedMap) {ownedMap=null;return false;}
        event.consume();closeOwnedMap();return true;
    }
    public boolean center() {
        checkThread(); if(stopped||pending==null) return true;
        if(!mapOpen()) return false;
        WorldMap map=client.getWorldMap();
        if(map==null||map.getWorldMapData()==null) return false;
        if(!map.getWorldMapData().surfaceContainsPosition(pending.getX(),pending.getY())) {
            pending=null;feedback.accept("Marker added. Select the matching map area to view this location.");return true;
        }
        map.setWorldMapPositionTarget(pending); pending=null;return true;
    }
    public void clear() {checkThread();request++;if(marker!=null)points.remove(marker);marker=null;pending=null;
        if(client.getGameState()!=GameState.LOGGED_IN)closeOwnedMap();}
    public void stop() {checkThread();clear();closeOwnedMap();stopped=true;}
}
