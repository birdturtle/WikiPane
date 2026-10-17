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
    /** Snapshot a context-menu entity on the client thread, before its widget or NPC disappears. */
    public static HttpUrl context(Client client, ItemManager items, MenuEntry entry) {
        switch (entry.getType()) {
            case NPC_FIRST_OPTION: case NPC_SECOND_OPTION: case NPC_THIRD_OPTION:
            case NPC_FOURTH_OPTION: case NPC_FIFTH_OPTION: case EXAMINE_NPC:
                NPC npc = entry.getNpc();
                NPCComposition definition = npc == null ? null : npc.getTransformedComposition();
                return definition == null ? null : checked("npc", definition.getId(),
                    definition.getName(), npc.getWorldLocation());
            case GAME_OBJECT_FIRST_OPTION: case GAME_OBJECT_SECOND_OPTION:
            case GAME_OBJECT_THIRD_OPTION: case GAME_OBJECT_FOURTH_OPTION:
            case GAME_OBJECT_FIFTH_OPTION: case EXAMINE_OBJECT:
                ObjectComposition object = client.getObjectDefinition(entry.getIdentifier());
                if (object != null && object.getImpostorIds() != null) object = object.getImpostor();
                WorldView view = client.getWorldView(entry.getWorldViewId());
                if (object == null || view == null || entry.getParam0() < 0 || entry.getParam1() < 0
                    || entry.getParam0() >= view.getSizeX() || entry.getParam1() >= view.getSizeY()) return null;
                return checked("object", object.getId(), object.getName(),
                    WorldPoint.fromScene(view, entry.getParam0(), entry.getParam1(), view.getPlane()));
            case GROUND_ITEM_FIRST_OPTION: case GROUND_ITEM_SECOND_OPTION:
            case GROUND_ITEM_THIRD_OPTION: case GROUND_ITEM_FOURTH_OPTION:
            case GROUND_ITEM_FIFTH_OPTION: case EXAMINE_ITEM_GROUND:
                return item(items, entry.getIdentifier());
            case CC_OP: case CC_OP_LOW_PRIORITY: case ITEM_USE: case EXAMINE_ITEM:
            case ITEM_FIRST_OPTION: case ITEM_SECOND_OPTION: case ITEM_THIRD_OPTION:
            case ITEM_FOURTH_OPTION: case ITEM_FIFTH_OPTION:
            case WIDGET_FIRST_OPTION: case WIDGET_SECOND_OPTION: case WIDGET_THIRD_OPTION:
            case WIDGET_FOURTH_OPTION: case WIDGET_FIFTH_OPTION:
                int id = entry.getItemId();
                if (id < 0 && entry.getType() == MenuAction.EXAMINE_ITEM) id = entry.getIdentifier();
                return id < 0 ? null : item(items, id);
            default: return null;
        }
    }
    private static HttpUrl item(ItemManager items, int id) {
        if (id < 0 || id == net.runelite.api.gameval.ItemID.BLANKOBJECT) return null;
        int canonical = items.canonicalize(id);
        return checked("item", canonical, items.getItemComposition(canonical).getMembersName(), null);
    }
    private static HttpUrl checked(String type, int id, String name, WorldPoint location) {
        return id < 0 || name == null || name.isBlank() || name.equals("null")
            ? null : url(type, id, Text.removeTags(name), location);
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
