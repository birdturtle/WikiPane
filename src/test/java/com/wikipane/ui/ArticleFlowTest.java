package com.wikipane.ui;
import static org.junit.Assert.*;

import com.google.gson.Gson;
import com.wikipane.DocumentTest;
import com.wikipane.api.WikiClient;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import javax.swing.*;
import okhttp3.*;
import org.junit.Test;
/** Exercises only isolated Swing panels, never the game client or game inputs. */
public class ArticleFlowTest {
    private <T> T edt(Callable<T> task) throws Exception {
        FutureTask<T> f = new FutureTask<>(task);
        SwingUtilities.invokeAndWait(f);
        return f.get();
    }
    private List<Component> all(Container root) {
        List<Component> result = new ArrayList<>();
        for (Component c : root.getComponents()) {
            result.add(c);
            if (c instanceof Container)
                result.addAll(all((Container) c));
        }
        return result;
    }
    private void waitFor(WikiPanePanel panel, String title) throws Exception {
        ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();
        CompletableFuture<Void> done = new CompletableFuture<>();
        timer.scheduleAtFixedRate(() -> {
            try {
                if (edt(()
                            -> all(panel).stream().anyMatch(
                                c -> c instanceof JEditorPane && "wiki.articleTitle".equals(c.getName())
                                    && title.equals(com.wikipane.vendor.jsoup.Jsoup.parse(((JEditorPane)c).getText()).text()))))
                    done.complete(null);
            } catch (Exception e) {
                done.completeExceptionally(e);
            }
        }, 0, 20, TimeUnit.MILLISECONDS);
        try {
            done.get(6, TimeUnit.SECONDS);
        } finally {
            timer.shutdownNow();
        }
    }
    private JButton button(WikiPanePanel panel, String tooltip) {
        return (JButton) all(panel)
            .stream()
            .filter(c -> c instanceof JButton && tooltip.equals(((JButton) c).getToolTipText()))
            .findFirst()
            .get();
    }
    @Test
    public void realRecipeToCooksAssistantAndBackForward() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(3);
        WikiClient wiki =
            new WikiClient(new OkHttpClient.Builder()
                               .addInterceptor(chain -> {
                                   String title = chain.request().url().queryParameter("page");
                                   String fixture = title.equals("Cook's Assistant") ? "Cooks_Assistant"
                                                                                     : "Recipe_for_Disaster";
                                   return new Response.Builder()
                                       .request(chain.request())
                                       .code(200)
                                       .message("fixture")
                                       .protocol(Protocol.HTTP_1_1)
                                       .body(ResponseBody.create(MediaType.get("application/json"),
                                           DocumentTest.fixture(fixture).toString()))
                                       .build();
                               })
                               .build(),
                new Gson());
        WikiPanePanel panel = edt(() -> new WikiPanePanel(wiki, pool));
        try {
            edt(() -> {
                panel.openTitle("Recipe for Disaster", "");
                return null;
            });
            waitFor(panel, "Recipe for Disaster");
            String requirementsLink = com.wikipane.DocumentTest.document("Recipe_for_Disaster")
                                          .blocks.stream()
                                          .filter(b -> b instanceof com.wikipane.document.WikiDocument.Table)
                                          .map(b -> (com.wikipane.document.WikiDocument.Table) b)
                                          .flatMap(t -> t.rows.stream())
                                          .flatMap(r -> r.cells.stream())
                                          .filter(c -> c.html.contains("Cook's Assistant"))
                                          .map(c
                                              -> com.wikipane.vendor.jsoup.Jsoup.parseBodyFragment(c.html)
                                                  .select("a[href]")
                                                  .stream()
                                                  .filter(a -> a.text().equals("Cook's Assistant"))
                                                  .findFirst()
                                                  .map(a -> a.attr("href"))
                                                  .orElse(""))
                                          .filter(s -> !s.isEmpty())
                                          .findFirst()
                                          .get();
            edt(() -> {
                panel.openLink(requirementsLink);
                return null;
            });
            waitFor(panel, "Cook's Assistant");
            edt(() -> {
                button(panel, "Back").doClick();
                return null;
            });
            waitFor(panel, "Recipe for Disaster");
            edt(() -> {
                button(panel, "Forward").doClick();
                return null;
            });
            waitFor(panel, "Cook's Assistant");
        } finally {
            edt(() -> {
                panel.stop();
                return null;
            });
            wiki.close();
            pool.shutdownNow();
        }
    }
}
