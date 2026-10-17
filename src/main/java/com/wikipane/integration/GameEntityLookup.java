package com.wikipane.integration;

import com.wikipane.api.WikiUrls;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.widgets.*;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.Text;
import okhttp3.HttpUrl;

/** Read only on the RuneLite client thread. No inventory snapshots or player identifiers. */
public final class GameEntityLookup {
    private GameEntityLookup() {}
    public static HttpUrl resolve(Client client, ItemManager items, MenuOptionClicked event) {
        String type, name;
        int id;
        WorldPoint location = null;
        switch (event.getMenuAction()) {
            case WIDGET_TARGET_ON_GROUND_ITEM:
                type = "item";
                id = items.canonicalize(event.getId());
                name = items.getItemComposition(id).getMembersName();
                break;
            case WIDGET_TARGET_ON_NPC:
                NPC npc = event.getMenuEntry().getNpc();
                if (npc == null)
                    return null;
                NPCComposition definition = npc.getTransformedComposition();
                if (definition == null)
                    return null;
                type = "npc";
                id = definition.getId();
                name = definition.getName();
                location = npc.getWorldLocation();
                break;
            case WIDGET_TARGET_ON_GAME_OBJECT:
                ObjectComposition object = client.getObjectDefinition(event.getId());
                if (object == null)
                    return null;
                if (object.getImpostorIds() != null)
                    object = object.getImpostor();
                if (object == null)
                    return null;
                type = "object";
                id = object.getId();
                name = object.getName();
                location =
                    WorldPoint.fromScene(client, event.getParam0(), event.getParam1(), client.getPlane());
                break;
            case WIDGET_TARGET_ON_WIDGET:
                Widget widget = client.getWidget(event.getParam1());
                if (widget != null && event.getParam0() != -1)
                    widget = widget.getChild(event.getParam0());
                if (widget == null || widget.getType() != WidgetType.GRAPHIC || widget.getItemId() < 0)
                    return null;
                type = "item";
                id = items.canonicalize(widget.getItemId());
                name = items.getItemComposition(id).getMembersName();
                break;
            default:
                return null;
        }
        if (id < 0 || name == null || name.equals("null"))
            return null;
        return url(type, id, Text.removeTags(name), location);
    }
    public static HttpUrl url(String type, int id, String name, WorldPoint location) {
        if (!java.util.Arrays.asList("item", "npc", "object").contains(type) || id < 0)
            throw new IllegalArgumentException("Unsupported entity");
        HttpUrl.Builder url = WikiUrls.BASE.newBuilder()
                                  .addPathSegments("w/Special:Lookup")
                                  .addQueryParameter("type", type)
                                  .addQueryParameter("id", Integer.toString(id))
                                  .addQueryParameter("name", name)
                                  .addQueryParameter("utm_source", "runelite");
        if (location != null)
            url.addQueryParameter("x", Integer.toString(location.getX()))
                .addQueryParameter("y", Integer.toString(location.getY()))
                .addQueryParameter("plane", Integer.toString(location.getPlane()));
        return url.build();
    }
}
