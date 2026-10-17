package com.wikipane;

import com.wikipane.document.WikiDocument;
import com.wikipane.view.*;

/** Deterministic, fixture-only composition audit. No network or game client. */
public final class SemanticReport {
    public static void main(String[] args) throws Exception {
        for (String fixture : new String[] {"Bronze_dagger", "Recipe_for_Disaster", "Vorkath", "Cooking",
                 "Vorkath__Strategies", "Recipe_for_Disaster__Full_guide", "Ranarr_seed", "Rake", "Prayer_potion", "Bronze_bar", "Sailing__Level_up_table", "Farming__Level_up_table", "Herblore__Level_up_table"}) {
            WikiPage page = new WikiPage(DocumentTest.document(fixture));
            WikiDocument doc = page.document;
            System.out.println("\n" + doc.title + " [" + doc.semantic.type + "]");
            for (ArticleViewModel.Area area : page.view.areas)
                System.out.println("  " + area.label + ": " + area.components.size() + " components, "
                    + area.components.stream().mapToInt(c -> c.text().length()).sum() + " characters");
            System.out.println(page.view.audit.summary());
            if (!page.view.audit.valid())
                throw new AssertionError("Composition audit failed: " + doc.title);
        }
    }
}
