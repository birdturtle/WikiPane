package com.wikipane.integration;

import com.wikipane.api.WikiUrls;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.Text;
import okhttp3.HttpUrl;

/** Read only on the RuneLite client thread. No inventory snapshots or player identifiers. */
public final class GameEntityLookup {
    private GameEntityLookup() {}
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
