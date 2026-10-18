package com.wikipane.view;

import com.wikipane.semantic.SemanticDocument.Fact;
import java.util.*;

/** Presentation importance only. Source facts and provenance remain unchanged. */
public final class FactPolicy {
    public enum Importance { PRIMARY, SUPPORTING, TECHNICAL }
    private FactPolicy() {}
    public static Importance importance(String label, String text) {
        String key = label.strip().toLowerCase(Locale.ROOT);
        if (key.matches("(?:item|npc|object|animation|graphic|model|sound|varbit|varp|recipe) ids?")
            || key.equals("id") || key.equals("moid") || text.strip().equalsIgnoreCase("MOID"))
            return Importance.TECHNICAL;
        if (Set.of("released", "release date", "update", "last update", "developer", "developers",
            "lead developer(s)", "lead developer", "music", "quest series").contains(key))
            return Importance.SUPPORTING;
        return Importance.PRIMARY;
    }
    public static String duplicateKey(Fact fact) {
        // Only singular infobox properties can be consolidated across source units. Never merge
        // arbitrary records, requirements, locations, conditional odds or comparison-table values.
        String label = fact.label.toLowerCase(Locale.ROOT).strip();
        if (!Set.of("daily volume", "weight", "tradeable", "equipable", "stackable", "noteable",
            "members", "quest item", "examine", "item id", "npc id", "object id").contains(label))
            return "unit:" + FieldPresentation.identity(fact.label, fact.value, fact.source);
        return fact.source.url + "\0" + fact.source.revision + "\0" + fact.variant + "\0" + label
            + "\0" + fact.value.html;
    }
}
