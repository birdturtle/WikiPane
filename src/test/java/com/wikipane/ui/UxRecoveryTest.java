package com.wikipane.ui;

import com.google.gson.Gson;
import com.wikipane.api.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import javax.swing.*;
import okhttp3.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Deterministic request ordering; no network, sleeps, game process or browser launches. */
public class UxRecoveryTest {
    private static final class Workers extends AbstractExecutorService {
        final Queue<Runnable> tasks = new ArrayDeque<>(); boolean stopped;
        public void shutdown() { stopped=true; }
        public List<Runnable> shutdownNow() { stopped=true; tasks.clear(); return List.of(); }
        public boolean isShutdown() { return stopped; }
        public boolean isTerminated() { return stopped; }
        public boolean awaitTermination(long timeout, TimeUnit unit) { return stopped; }
        public void execute(Runnable task) { if (stopped) throw new RejectedExecutionException(); tasks.add(task); }
        void next() throws Exception { tasks.remove().run(); SwingUtilities.invokeAndWait(() -> {}); }
    }
    static List<Component> all(Container parent) {
        List<Component> result = new ArrayList<>();
        for (Component child : parent.getComponents()) { result.add(child); if (child instanceof Container) result.addAll(all((Container)child)); }
        return result;
    }
    private static JButton button(Container parent, String tooltip) {
        return (JButton) all(parent).stream().filter(c -> c instanceof JButton && tooltip.equals(((JButton)c).getToolTipText())).findFirst().orElseThrow();
    }
    @Test public void failedNavigationRetainsVisibleArticleBrowserTargetAndHasIndependentRecovery() throws Exception {
        Workers workers = new Workers();
        WikiClient wiki = new WikiClient(new OkHttpClient.Builder().addInterceptor(chain -> {
            String title = chain.request().url().queryParameter("page");
            if (title.equals("Missing")) throw new java.net.UnknownHostException("private-internal-host-details");
            return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("fixture")
                .body(ResponseBody.create(MediaType.get("application/json"), "{\"parse\":{\"title\":\"Present\",\"text\":\"<p>Present content.</p>\"}}")).build();
        }).build(),new Gson());
        WikiPanePanel[] panel = new WikiPanePanel[1];
        try {
            SwingUtilities.invokeAndWait(() -> { panel[0]=new WikiPanePanel(wiki,workers); panel[0].openTitle("Present",""); }); workers.next();
            SwingUtilities.invokeAndWait(() -> panel[0].openTitle("Missing","Requirements")); workers.next();
            SwingUtilities.invokeAndWait(() -> {
                assertEquals(WikiUrls.article("Present"),button(panel[0],"Open in browser").getActionCommand());
                JButton fallback=button(panel[0],"Open the failed request in browser");
                assertTrue(fallback.isVisible()); assertEquals(WikiUrls.article("Missing")+"#Requirements",fallback.getActionCommand());
                assertTrue(all(panel[0]).stream().anyMatch(c -> c instanceof JEditorPane && com.wikipane.html.WikiHtml.parse(((JEditorPane)c).getText()).text().contains("Check your connection")));
                assertFalse(all(panel[0]).stream().anyMatch(c -> c instanceof JEditorPane && com.wikipane.html.WikiHtml.parse(((JEditorPane)c).getText()).text().contains("private-internal")));
                button(panel[0],"Retry this request").doClick();
            }); assertEquals(1,workers.tasks.size()); workers.next();
        } finally { SwingUtilities.invokeAndWait(() -> { if(panel[0]!=null)panel[0].stop(); }); wiki.close(); workers.shutdownNow(); }
    }
    @Test public void firstFailureOffersBrowserFallbackEvenWithoutAnExistingArticle() throws Exception {
        Workers workers = new Workers();
        WikiClient wiki=new WikiClient(new OkHttpClient.Builder().addInterceptor(chain -> {throw new java.net.SocketTimeoutException();}).build(),new Gson());
        WikiPanePanel[] panel=new WikiPanePanel[1];
        try {
            SwingUtilities.invokeAndWait(() -> {panel[0]=new WikiPanePanel(wiki,workers);panel[0].openTitle("Missing","");});workers.next();
            SwingUtilities.invokeAndWait(() -> {
                assertFalse(button(panel[0],"Open in browser").isEnabled());
                assertTrue(button(panel[0],"Open the failed request in browser").isVisible());
                assertTrue(button(panel[0],"Retry this request").isVisible());
            });
        } finally {SwingUtilities.invokeAndWait(() -> panel[0].stop());wiki.close();workers.shutdownNow();}
    }
}
