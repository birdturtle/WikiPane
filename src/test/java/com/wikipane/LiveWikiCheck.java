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
