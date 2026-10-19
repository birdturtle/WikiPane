package com.wikipane.ui;
import static org.junit.Assert.*;

import com.google.gson.Gson;
import com.wikipane.DocumentTest;
import com.wikipane.api.WikiClient;
import com.wikipane.cache.ImageCache;
import com.wikipane.document.WikiDocument;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import javax.swing.*;
import okhttp3.*;
import org.junit.Test;
public class SemanticUiTest {
    private <T> T edt(Callable<T> task) throws Exception {
        FutureTask<T> future = new FutureTask<>(task);
        SwingUtilities.invokeAndWait(future);
        return future.get();
    }
    private List<Component> all(Container root) {
        List<Component> found = new ArrayList<>();
        for (Component c : root.getComponents()) {
            found.add(c);
            if (c instanceof Container)
                found.addAll(all((Container) c));
        }
        return found;
    }
    private String visibleText(Container root) {
        return all(root)
            .stream()
            .filter(c -> c instanceof JEditorPane)
            .map(c -> com.wikipane.html.WikiHtml.parseBodyFragment(((JEditorPane) c).getText()).text())
            .reduce("", (a, b) -> a + " " + b);
    }
    private void await(Callable<Boolean> condition) throws Exception {
        ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();
        CompletableFuture<Void> result = new CompletableFuture<>();
        timer.scheduleAtFixedRate(() -> {
            try {
                if (edt(condition))
                    result.complete(null);
            } catch (Exception e) {
                result.completeExceptionally(e);
            }
        }, 0, 20, TimeUnit.MILLISECONDS);
        try {
            result.get(8, TimeUnit.SECONDS);
        } finally {
            timer.shutdownNow();
        }
    }
    @Test
    public void monsterHasOneSectionSelectorAndDropsAreADirectDestination() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(3);
        WikiClient wiki =
            new WikiClient(new OkHttpClient.Builder()
                               .addInterceptor(chain
                                   -> new Response.Builder()
                                       .request(chain.request())
                                       .protocol(Protocol.HTTP_1_1)
                                       .code(200)
                                       .message("fixture")
                                       .body(ResponseBody.create(MediaType.get("application/json"),
                                           DocumentTest.fixture("Vorkath").toString()))
                                       .build())
                               .build(),
                new Gson());
        WikiPanePanel panel = edt(() -> new WikiPanePanel(wiki, pool));
        try {
            edt(() -> {
                panel.openTitle("Vorkath", "");
                return null;
            });
            await(()
                      -> all(panel).stream().anyMatch(c
                          -> c instanceof JButton && "wiki.currentSection".equals(c.getName())
                              && "Overview".equals(((JButton)c).getText())) && visibleText(panel).contains("Vorkath"));
            edt(() -> {
                assertEquals(0, all(panel).stream().filter(c -> c instanceof JToggleButton).count());
                com.wikipane.view.WikiPage page =
                    new com.wikipane.view.WikiPage(DocumentTest.document("Vorkath"));
                panel.navigateSection(page.navigation.sections.stream()
                        .filter(d -> d.label.equals("Drops") && d.depth == 0)
                        .findFirst()
                        .orElseThrow());
                return null;
            });
            await(()
                      -> visibleText(panel).contains("Quantity")
                    || visibleText(panel).toLowerCase(Locale.ROOT).contains("average"));
        } finally {
            edt(() -> {
                panel.stop();
                return null;
            });
            wiki.close();
            pool.shutdownNow();
        }
    }
    @Test
    public void dropPaginationMakesTheFinalSourceRowsReachable() throws Exception {
        WikiDocument doc = DocumentTest.document("Vorkath");
        ExecutorService pool = Executors.newSingleThreadExecutor();
        WikiClient wiki = new WikiClient(new OkHttpClient(), new Gson());
        try {
            edt(() -> {
                ContentRenderer renderer = new ContentRenderer(url -> {}, pool, new ImageCache(wiki));
                JPanel body = renderer.renderArea(
                    doc, com.wikipane.view.ArticleViewModel.create(doc.semantic).area("drops"), Map.of(), "");
                renderer.revealAll();
                int clicks = 0;
                while (true) {
                    JButton more =
                        (JButton) all(body)
                            .stream()
                            .filter(c
                                -> c instanceof JButton && ((JButton) c).getText().startsWith("Show more ("))
                            .findFirst()
                            .orElse(null);
                    if (more == null)
                        break;
                    more.doClick();
                    if (++clicks > 100)
                        fail("Pagination did not terminate");
                }
                String text = visibleText(body);
                assertTrue(text.contains("Skeletal visage"));
                assertTrue(text.contains("Draconic visage"));
                assertTrue(text.contains("1/5,000") || text.contains("1/5000"));
                return null;
            });
        } finally {
            wiki.close();
            pool.shutdownNow();
        }
    }
    @Test
    public void fullWalkthroughProgressivelyRevealsEveryBufferedComponent() throws Exception {
        WikiDocument doc = DocumentTest.document("Recipe_for_Disaster__Full_guide");
        ExecutorService pool = Executors.newSingleThreadExecutor();
        WikiClient wiki = new WikiClient(new OkHttpClient(), new Gson());
        try {
            edt(() -> {
                ContentRenderer renderer = new ContentRenderer(url -> {}, pool, new ImageCache(wiki));
                JPanel body = renderer.renderArea(doc,
                    com.wikipane.view.ArticleViewModel.create(doc.semantic).area("walkthrough"), Map.of(),
                    "");
                assertEquals(Integer.valueOf(6), renderer.tableState().get("@shown:walkthrough"));
                renderer.revealAll();
                assertEquals(Integer.valueOf(com.wikipane.view.ArticleViewModel.create(doc.semantic)
                                     .area("walkthrough")
                                     .components.size()),
                    renderer.tableState().get("@shown:walkthrough"));
                String text = visibleText(body).toLowerCase(Locale.ROOT);
                assertTrue(text.contains("mountain dwarf"));
                assertTrue(text.contains("goblin"));
                assertTrue(text.contains("culinaromancer"));
                return null;
            });
        } finally {
            wiki.close();
            pool.shutdownNow();
        }
    }
}
