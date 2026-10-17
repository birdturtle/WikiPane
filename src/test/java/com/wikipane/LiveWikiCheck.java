package com.wikipane;
import com.google.gson.Gson;
import com.wikipane.api.*;
import com.wikipane.cache.ImageCache;
import com.wikipane.document.WikiDocument;
import com.wikipane.integration.GameEntityLookup;
import okhttp3.OkHttpClient;
/** Explicit opt-in network check; not part of the deterministic test suite. */
public final class LiveWikiCheck {
    public static void main(String[] args) throws Exception {
        OkHttpClient http = new OkHttpClient();
        try (WikiClient wiki = new WikiClient(http, new Gson())) {
            for (String title : new String[] {"Bronze dagger", "Recipe for Disaster", "Vorkath"}) {
                long start = System.nanoTime();
                WikiDocument d = wiki.article(title);
                System.out.println(d.title + ": revision " + d.revision + ", " + d.blocks.size() + " blocks, "
                    + d.sections.size() + " sections, " + (System.nanoTime() - start) / 1_000_000 + " ms");
                if (!d.title.equals(title))
                    throw new AssertionError("Incorrect title");
            }
            if (!wiki.search("Bronze dagger", true).contains("Bronze dagger"))
                throw new AssertionError("Suggestions");
            if (!wiki.search("Recipe for Disaster", false).contains("Recipe for Disaster"))
                throw new AssertionError("Search");
            String resolved = wiki.entity(GameEntityLookup.url("item", 1205, "Bronze dagger", null));
            System.out.println("Exact item lookup: " + resolved);
            if (!resolved.contains("Bronze_dagger"))
                throw new AssertionError("Lookup");
            com.wikipane.knowledge.KnowledgeService knowledge = new com.wikipane.knowledge.KnowledgeService(wiki);
            com.wikipane.knowledge.Inspection seed = com.wikipane.knowledge.Inspection.project(
                knowledge.entity(GameEntityLookup.url("item", 5295, "Ranarr seed", null)));
            if (!seed.article.document.title.equals("Ranarr seed")
                || seed.rows.stream().noneMatch(row -> row.label.equals("Growth time") && row.value.text.contains("80 min"))
                || seed.rows.stream().noneMatch(row -> row.label.equals("Level required") && row.value.text.contains("32")))
                throw new AssertionError("Live seed knowledge projection");
            System.out.println("Exact seed inspection: " + seed.article.document.title + ", " + seed.rows.size() + " sourced rows");
            com.wikipane.knowledge.KnowledgeArticle monster = knowledge.entity(
                GameEntityLookup.url("npc", 8061, "Vorkath", null));
            if (!monster.variantKnown || monster.facts.stream().noneMatch(fact -> fact.label.equals("Hitpoints") && fact.value.text.equals("750")))
                throw new AssertionError("Live NPC variant knowledge query");
            System.out.println("Exact NPC inspection: " + monster.document.title + " · " + monster.variant);
            WikiDocument d = wiki.article("Vorkath");
            WikiDocument.Image thumbnail = null;
            for (WikiDocument.Block b : d.blocks) {
                if (b instanceof WikiDocument.Image)
                    thumbnail = (WikiDocument.Image) b;
                if (b instanceof WikiDocument.Table && !((WikiDocument.Table) b).images.isEmpty())
                    thumbnail = ((WikiDocument.Table) b).images.get(0);
                if (thumbnail != null)
                    break;
            }
            if (thumbnail == null)
                throw new AssertionError("No source thumbnail found");
            java.awt.image.BufferedImage image = new ImageCache(wiki).load(thumbnail.url);
            System.out.println("Wiki image decoded: " + image.getWidth() + " x " + image.getHeight());
            if (image.getWidth() > 200 || image.getHeight() > 220)
                throw new AssertionError("Image size");
        } finally {
            http.connectionPool().evictAll();
            http.dispatcher().executorService().shutdownNow();
        }
    }
}
