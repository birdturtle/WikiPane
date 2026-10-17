package com.wikipane.ui;

import static org.junit.Assert.*;

import com.google.gson.Gson;
import com.wikipane.DocumentTest;
import com.wikipane.api.WikiClient;
import com.wikipane.cache.ImageCache;
import com.wikipane.view.WikiPage;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import javax.swing.*;
import okhttp3.OkHttpClient;
import org.junit.Test;

/** Real sidebar component bounds, not screenshots of the game client. */
public class ReaderFrameworkTest {
    private void layout(Container container) {
        container.doLayout();
        for (Component child : container.getComponents())
            if (child instanceof Container)
                layout((Container) child);
    }
    private List<Component> all(Container container) {
        List<Component> result = new ArrayList<>();
        for (Component child : container.getComponents()) {
            result.add(child);
            if (child instanceof Container)
                result.addAll(all((Container) child));
        }
        return result;
    }
    @Test
    public void explicitLineBreaksSurviveNarrowTextWrapping() throws Exception {
        FutureTask<Void> task = new FutureTask<>(() -> {
            JEditorPane pane = new ReaderComponents(url -> {}).text("<b>First line</b><br>Second line");
            pane.setSize(190, 100);
            String text = pane.getDocument().getText(0, pane.getDocument().getLength());
            assertTrue(pane.modelToView2D(text.indexOf("Second")).getY()
                > pane.modelToView2D(text.indexOf("First")).getY());
            return null;
        });
        SwingUtilities.invokeAndWait(task);
        task.get();
    }
    @Test
    public void factPanelsFitNarrowWidthsWithoutClippingOrLosingAttackDefenceLabels() throws Exception {
        WikiPage page = new WikiPage(DocumentTest.document("Bronze_dagger"));
        ExecutorService pool = Executors.newSingleThreadExecutor();
        WikiClient wiki = new WikiClient(new OkHttpClient(), new Gson());
        try {
            SwingUtilities.invokeAndWait(() -> {
                for (int width : new int[] {185, 225, 300}) {
                    ContentRenderer renderer = new ContentRenderer(url -> {}, pool, new ImageCache(wiki));
                    JPanel body = renderer.renderArea(page, page.view.area("stats"), Map.of(), "Unpoisoned");
                    renderer.revealAll();
                    for (int pass = 0; pass < 4; pass++) {
                        body.setSize(width, body.getPreferredSize().height);
                        layout(body);
                    }
                    String text =
                        all(body)
                            .stream()
                            .filter(c -> c instanceof JEditorPane)
                            .map(c -> org.jsoup.Jsoup.parseBodyFragment(((JEditorPane) c).getText()).text())
                            .reduce("", (a, b) -> a + " " + b);
                    assertTrue(text.contains("Attack bonuses"));
                    assertTrue(text.contains("Defence bonuses"));
                    assertTrue(text.contains("+4"));
                    long cards = all(body).stream().filter(c -> "wiki.facts".equals(c.getName())).count();
                    assertTrue(cards >= 3);
                    for (Component c : all(body))
                        if (c instanceof JEditorPane) {
                            assertTrue("Width " + width + ": " + c.getBounds(),
                                c.getWidth() > 0 && c.getWidth() <= width);
                            assertTrue("Child overflows parent at " + width,
                                c.getX() + c.getWidth() <= c.getParent().getWidth());
                        }
                }
            });
        } finally {
            wiki.close();
            pool.shutdownNow();
        }
    }
    @Test
    public void sourceActionsStayOutOfReadingFlowAndRecordIdentityKeepsItsLink() throws Exception {
        WikiPage page = new WikiPage(DocumentTest.document("Vorkath"));
        ExecutorService pool = Executors.newSingleThreadExecutor();
        WikiClient wiki = new WikiClient(new OkHttpClient(), new Gson());
        try {
            SwingUtilities.invokeAndWait(() -> {
                List<String> opened = new ArrayList<>();
                ContentRenderer renderer = new ContentRenderer(opened::add, pool, new ImageCache(wiki));
                JPanel body = renderer.renderArea(page, page.view.area("drops"), Map.of(), "");
                renderer.revealAll();
                assertFalse(all(body).stream().anyMatch(
                    c -> c instanceof JButton && ((JButton) c).getText().equals("Wiki ↗")));
                assertTrue(all(body).stream().anyMatch(c -> "wiki.record".equals(c.getName())));
                JEditorPane name =
                    (JEditorPane) all(body)
                        .stream()
                        .filter(c
                            -> c instanceof JEditorPane
                                && org.jsoup.Jsoup.parseBodyFragment(((JEditorPane) c).getText())
                                    .text()
                                    .contains("Superior dragon bones"))
                        .findFirst()
                        .orElseThrow();
                String href =
                    org.jsoup.Jsoup.parseBodyFragment(name.getText()).selectFirst("a[href]").attr("href");
                javax.swing.event.HyperlinkEvent click = new javax.swing.event.HyperlinkEvent(
                    name, javax.swing.event.HyperlinkEvent.EventType.ACTIVATED, null, href);
                for (javax.swing.event.HyperlinkListener listener : name.getHyperlinkListeners())
                    listener.hyperlinkUpdate(click);
                assertEquals(List.of(href), opened);
                assertTrue(href.contains("Superior_dragon_bones"));
            });
        } finally {
            wiki.close();
            pool.shutdownNow();
        }
    }
}
