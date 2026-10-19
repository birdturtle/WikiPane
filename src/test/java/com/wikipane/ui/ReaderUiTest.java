package com.wikipane.ui;

import static org.junit.Assert.*;

import com.google.gson.Gson;
import com.wikipane.api.WikiClient;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;
import javax.swing.*;
import okhttp3.*;
import org.junit.Test;

public class ReaderUiTest {
    private <T> T edt(Callable<T> task) throws Exception {
        FutureTask<T> f = new FutureTask<>(task);
        SwingUtilities.invokeAndWait(f);
        return f.get();
    }
    private void await(Callable<Boolean> check) throws Exception {
        ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();
        CompletableFuture<Void> result = new CompletableFuture<>();
        timer.scheduleAtFixedRate(() -> {
            try {
                if (edt(check))
                    result.complete(null);
            } catch (Exception ex) {
                result.completeExceptionally(ex);
            }
        }, 0, 20, TimeUnit.MILLISECONDS);
        try {
            result.get(5, TimeUnit.SECONDS);
        } finally {
            timer.shutdownNow();
        }
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
    private boolean text(WikiPanePanel panel, String text) {
        return all(panel).stream().anyMatch(
            c -> c instanceof JEditorPane && ((JEditorPane) c).getText().contains(text));
    }
    private JButton button(WikiPanePanel panel, String tip) {
        return (JButton) all(panel)
            .stream()
            .filter(c -> c instanceof JButton && tip.equals(((JButton) c).getToolTipText()))
            .findFirst()
            .get();
    }
    private Response reply(Interceptor.Chain chain, String title, String content) {
        return new Response.Builder()
            .request(chain.request())
            .code(200)
            .message("Test")
            .protocol(Protocol.HTTP_1_1)
            .body(ResponseBody.create(MediaType.get("application/json"),
                "{\"parse\":{\"title\":\"" + title + "\",\"text\":\"<p>" + content + "</p>\"}}"))
            .build();
    }
    @Test
    public void laterArticleWinsAndFailurePreservesExistingContent() throws Exception {
        CompletableFuture<Void> firstStarted = new CompletableFuture<>(),
                                releaseFirst = new CompletableFuture<>();
        ExecutorService workers = Executors.newFixedThreadPool(3);
        WikiClient wiki = new WikiClient(new OkHttpClient.Builder()
                                             .addInterceptor(chain -> {
                                                 String title = chain.request().url().queryParameter("page");
                                                 if (title.equals("Slow")) {
                                                     firstStarted.complete(null);
                                                     try {
                                                         releaseFirst.get(5, TimeUnit.SECONDS);
                                                     } catch (Exception ex) {
                                                         throw new java.io.IOException(ex);
                                                     }
                                                 }
                                                 if (title.equals("Missing"))
                                                     throw new java.io.IOException("Offline");
                                                 return reply(chain, title, title + " marker");
                                             })
                                             .build(),
            new Gson());
        WikiPanePanel panel = edt(() -> new WikiPanePanel(wiki, workers));
        try {
            edt(() -> {
                panel.openTitle("Slow", "");
                return null;
            });
            firstStarted.get(5, TimeUnit.SECONDS);
            edt(() -> {
                panel.openTitle("New", "");
                return null;
            });
            await(() -> text(panel, "New marker"));
            releaseFirst.complete(null);
            edt(() -> {
                panel.openTitle("Missing", "");
                return null;
            });
            await(()
                      -> all(panel).stream().anyMatch(
                          c -> c instanceof JEditorPane && com.wikipane.html.WikiHtml.parse(((JEditorPane)c).getText()).text().contains("Please try again")));
            assertTrue(edt(() -> text(panel, "New marker")));
            edt(() -> {
                panel.stop();
                return null;
            });
        } finally {
            releaseFirst.complete(null);
            wiki.close();
            workers.shutdownNow();
        }
    }
    @Test
    public void sidebarHasFixedControlsAndBackForwardRestoreScroll() throws Exception {
        ExecutorService workers = Executors.newFixedThreadPool(3);
        WikiClient wiki =
            new WikiClient(new OkHttpClient.Builder()
                               .addInterceptor(chain -> {
                                   String title = chain.request().url().queryParameter("page");
                                   return reply(chain, title, (title + " content ").repeat(250));
                               })
                               .build(),
                new Gson());
        WikiPanePanel panel = edt(() -> new WikiPanePanel(wiki, workers));
        try {
            edt(() -> {
                panel.setSize(225, 700);
                panel.doLayout();
                panel.openTitle("Recipe", "");
                return null;
            });
            await(() -> text(panel, "Recipe content"));
            edt(() -> {
                layout(panel);
                return null;
            });
            JScrollPane scroll = edt(()
                                         -> (JScrollPane) all(panel)
                                             .stream()
                                             .filter(c -> c instanceof JScrollPane)
                                             .findFirst()
                                             .get());
            edt(() -> {
                layout(panel);
                layout(panel);
                scroll.getVerticalScrollBar().setValue(120);
                assertEquals("Fixture must be scrollable", 120, scroll.getVerticalScrollBar().getValue());
                panel.openTitle("Cook", "");
                return null;
            });
            await(() -> text(panel, "Cook content"));
            edt(() -> {
                button(panel, "Back").doClick();
                return null;
            });
            await(() -> {
                layout(panel);
                return text(panel, "Recipe content") && scroll.getVerticalScrollBar().getValue() == 120;
            });
            edt(() -> {
                button(panel, "Forward").doClick();
                return null;
            });
            await(() -> text(panel, "Cook content"));
            assertEquals(
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER, scroll.getHorizontalScrollBarPolicy());
            assertTrue(edt(() -> button(panel, "Open in browser").getY() < scroll.getY()));
        } finally {
            edt(() -> {
                panel.stop();
                return null;
            });
            wiki.close();
            workers.shutdownNow();
        }
    }
    private void layout(Container c) {
        c.doLayout();
        for (Component child : c.getComponents())
            if (child instanceof Container)
                layout((Container) child);
    }
}
