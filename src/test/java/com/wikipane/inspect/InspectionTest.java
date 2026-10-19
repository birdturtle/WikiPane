package com.wikipane.inspect;

import com.google.gson.Gson;
import com.wikipane.DocumentTest;
import com.wikipane.api.*;
import com.wikipane.cache.ImageCache;
import com.wikipane.integration.GameEntityLookup;
import com.wikipane.knowledge.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import okhttp3.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class InspectionTest {
    private static final class Workers extends AbstractExecutorService {
        final Queue<Runnable> queue = new ArrayDeque<>();
        boolean stopped;
        public void shutdown() { stopped = true; }
        public List<Runnable> shutdownNow() { stopped = true; List<Runnable> old = List.copyOf(queue); queue.clear(); return old; }
        public boolean isShutdown() { return stopped; }
        public boolean isTerminated() { return stopped && queue.isEmpty(); }
        public boolean awaitTermination(long timeout, TimeUnit unit) { return isTerminated(); }
        public void execute(Runnable r) { if (stopped) throw new RejectedExecutionException(); queue.add(r); }
        void next() { queue.remove().run(); }
    }
    private static final class Harness implements AutoCloseable {
        final Workers workers = new Workers();
        final List<Runnable> dispatch = new ArrayList<>();
        final List<String> opened = new ArrayList<>();
        final AtomicInteger calls = new AtomicInteger();
        boolean fail, multipleRecipes;
        final WikiClient wiki = new WikiClient(new OkHttpClient.Builder().addInterceptor(chain -> {
            calls.incrementAndGet();
            if (fail) throw new IOException("Offline");
            HttpUrl url = chain.request().url();
            String body;
            Response.Builder response = new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(200).message("fixture");
            if (url.encodedPath().equals("/w/Special:Lookup")) {
                String title = multipleRecipes ? "Prayer_potion" : "5295".equals(url.queryParameter("id")) ? "Ranarr_seed" : "Bronze_dagger";
                response.code(302).header("Location", "/w/" + title);
                body = "";
            } else if (url.encodedPath().startsWith("/w/")) body = "resolved";
            else if (url.encodedPath().startsWith("/images/")) throw new IOException("No image fixture");
            else {
                com.google.gson.JsonObject fixture=DocumentTest.fixture(url.queryParameter("page").replace(' ', '_'));
                if(multipleRecipes) {
                    com.wikipane.html.Element root=com.wikipane.html.WikiHtml.parseBodyFragment(fixture.getAsJsonObject("parse").get("text").getAsString()).body();
                    com.wikipane.html.Element original=root.selectFirst(".recipe-table"),copy=original.clone();
                    copy.select("table").get(1).select("tr").get(1).select("td").get(2).text("2");
                    original.after(copy);fixture.getAsJsonObject("parse").addProperty("text",root.html());
                }
                body=fixture.toString();
            }
            return response.body(ResponseBody.create(MediaType.get("application/json"), body)).build();
        }).build(), new Gson());
        final QuickInspectController controller = new QuickInspectController(new KnowledgeService(wiki),
            new ImageCache(wiki), workers, dispatch::add, opened::add);
        void inspect(int id) { controller.inspect(GameEntityLookup.url("item", id, "test item", null)); }
        void flush() { List<Runnable> old = List.copyOf(dispatch); dispatch.clear(); old.forEach(Runnable::run); }
        public void close() { controller.stop(); wiki.close(); workers.shutdownNow(); }
    }
    @Test public void recipeSwitchingUsesCachedSemanticDataAndCannotSurviveDismissal() {
        try(Harness h=new Harness()) {
            h.multipleRecipes=true;h.inspect(5295);h.workers.next();h.flush();
            assertEquals(2,h.controller.view().inspection.article.recipes.size());
            int calls=h.calls.get();
            h.controller.recipe(1);h.workers.next();h.flush();
            assertEquals(1,h.controller.view().inspection.recipeIndex);
            assertTrue(h.controller.view().inspection.rows.stream().anyMatch(r->r.label.equals("Ingredient")&&r.value.text.startsWith("2 × Ranarr")));
            assertEquals(calls,h.calls.get());assertTrue(h.opened.isEmpty());
            h.controller.recipe(0);h.workers.next();h.controller.dismiss();h.flush();assertNull(h.controller.view());
        }
    }
    @Test public void staleCompletionCannotReplaceANewerEntity() {
        try (Harness h = new Harness()) {
            h.inspect(5295); h.workers.next();
            h.inspect(1205); h.workers.next();
            Collections.reverse(h.dispatch);
            h.flush();
            assertEquals("Bronze dagger", h.controller.view().inspection.article.document.title);
            assertTrue(h.opened.isEmpty());
        }
    }
    @Test public void dismissAndShutdownIgnorePendingCompletions() {
        try (Harness h = new Harness()) {
            h.inspect(5295); h.workers.next(); h.controller.dismiss(); h.flush();
            assertNull(h.controller.view());
            h.inspect(1205); h.workers.next(); h.controller.stop(); h.flush();
            assertNull(h.controller.view());
            h.inspect(5295); assertNull(h.controller.view());
            assertTrue(h.opened.isEmpty());
        }
    }
    @Test public void inspectDoesNotNavigateAndExploreIsExplicit() {
        try (Harness h = new Harness()) {
            h.inspect(5295); h.workers.next(); h.flush();
            assertTrue(h.opened.isEmpty());
            assertNotNull(h.controller.view().inspection);
            h.controller.explore();
            assertEquals(1, h.opened.size());
            assertTrue(h.opened.get(0).contains("/w/Ranarr_seed"));
            assertNull(h.controller.view());
        }
    }
    @Test public void failuresRemainDismissibleAndRetryable() {
        try (Harness h = new Harness()) {
            h.fail = true; h.inspect(5295); h.workers.next(); h.flush();
            assertNull(h.controller.view().inspection);
            assertTrue(h.controller.view().message.contains("could not"));
            assertTrue(h.opened.isEmpty());
            h.fail = false; h.controller.retry(); h.workers.next(); h.flush();
            assertNotNull(h.controller.view().inspection);
            h.controller.page(1000);
            assertEquals(h.controller.view().layout.pages.size() - 1, h.controller.view().page);
            h.controller.page(-1000); assertEquals(0, h.controller.view().page);
        }
    }
    @Test public void cancelledQueuedLookupsNeverFetchAndBusyQueueIsRecoverable() {
        try (Harness h = new Harness()) {
            h.inspect(5295); h.inspect(1205); h.workers.next();
            assertEquals(0, h.calls.get());
            h.workers.next(); h.flush();
            assertEquals("Bronze dagger", h.controller.view().inspection.article.document.title);
            h.workers.shutdown(); h.inspect(5295);
            assertTrue(h.controller.view().message.contains("busy"));
        }
    }
    @Test public void overlayContentIsWrappedPaginatedAndBounded() throws Exception {
        for (String fixture : new String[]{"Ranarr_seed", "Vorkath", "Recipe_for_Disaster", "Bronze_dagger", "Prayer_potion", "Bronze_bar"}) {
            Inspection inspection = Inspection.project(new KnowledgeArticle(DocumentTest.document(fixture), "", ""));
            InspectionLayout layout = new InspectionLayout(inspection);
            assertFalse(layout.pages.isEmpty());
            for (int page = 0; page < layout.pages.size(); page++) {
                assertTrue(layout.pages.get(page).size() <= InspectionLayout.LINES);
                BufferedImage canvas = new BufferedImage(400, 500, BufferedImage.TYPE_INT_ARGB);
                Graphics2D graphics = canvas.createGraphics();
                for (InspectionLayout.Line line : layout.pages.get(page)) {
                    graphics.setFont(line.label ? InspectionLayout.LABEL : InspectionLayout.BODY);
                    assertTrue(fixture + " clipped: " + line.text,
                        graphics.getFontMetrics().stringWidth(line.text) <= InspectionLayout.INNER + 2);
                    if (!line.value.isEmpty()) {
                        int width = graphics.getFontMetrics(InspectionLayout.LABEL).stringWidth(line.text) + 7
                            + graphics.getFontMetrics(InspectionLayout.BODY).stringWidth(line.value);
                        assertTrue("Inline field overflow: " + line.text, width <= InspectionLayout.INNER + 2);
                    }
                }
                Dimension size = QuickInspectOverlay.paint(graphics,
                    new QuickInspectController.View(GameEntityLookup.url("item", 1, "test", null),
                        inspection, layout, null, "", page));
                graphics.dispose();
                assertEquals(210, size.width);
                assertTrue("Oversized inspection: " + size.height, size.height <= 240);
                int translucent = 0;
                for (int y = 0; y < size.height; y++) for (int x = 0; x < size.width; x++) {
                    int alpha = canvas.getRGB(x, y) >>> 24;
                    if (alpha > 0 && alpha < 255) translucent++;
                }
                assertTrue("Inspection background must be translucent", translucent > size.width * size.height / 2);
            }
        }
    }
}
