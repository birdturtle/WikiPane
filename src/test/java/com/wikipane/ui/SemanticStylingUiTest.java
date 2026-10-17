package com.wikipane.ui;

import com.google.gson.Gson;
import com.wikipane.DocumentTest;
import com.wikipane.api.WikiClient;
import com.wikipane.cache.ImageCache;
import com.wikipane.pin.*;
import com.wikipane.view.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import javax.swing.*;
import okhttp3.OkHttpClient;
import org.junit.Test;
import static org.junit.Assert.*;

/** Offline reader tests: no RuneScape canvas or game input. */
public class SemanticStylingUiTest {
    private static List<Component> all(Container root) {
        List<Component> found = new ArrayList<>();
        for (Component child : root.getComponents()) { found.add(child); if (child instanceof Container) found.addAll(all((Container)child)); }
        return found;
    }
    private static void layout(Container root) {
        root.doLayout(); for (Component child : root.getComponents()) if (child instanceof Container) layout((Container)child);
    }
    private static String plain(JEditorPane pane) { return org.jsoup.Jsoup.parseBodyFragment(pane.getText()).text(); }
    @Test public void rakeDisplayRetainsOneVolumeLabelAndAWorkingRealTimePriceLink() throws Exception {
        WikiPage page = new WikiPage(DocumentTest.document("Rake"));
        ExecutorService worker = Executors.newSingleThreadExecutor(); WikiClient wiki = new WikiClient(new OkHttpClient(), new Gson());
        try {
            SwingUtilities.invokeAndWait(() -> {
                List<String> opened = new ArrayList<>();
                ContentRenderer renderer = new ContentRenderer(opened::add, worker, new ImageCache(wiki));
                JPanel body = renderer.renderArea(page, page.view.area("info"), Map.of(), ""); renderer.revealAll();
                List<Component> children = all(body);
                assertEquals(1, children.stream().filter(c -> c instanceof JEditorPane && plain((JEditorPane)c).equals("Daily volume")).count());
                JEditorPane prices = (JEditorPane)children.stream().filter(c -> c instanceof JEditorPane && plain((JEditorPane)c).equals("View real-time prices")).findFirst().orElseThrow();
                String url = org.jsoup.Jsoup.parseBodyFragment(prices.getText()).selectFirst("a").attr("href");
                for (javax.swing.event.HyperlinkListener listener : prices.getHyperlinkListeners())
                    listener.hyperlinkUpdate(new javax.swing.event.HyperlinkEvent(prices, javax.swing.event.HyperlinkEvent.EventType.ACTIVATED, null, url));
                assertEquals(List.of("https://prices.runescape.wiki/osrs/item/5341"), opened);
                assertFalse(children.stream().filter(c -> c instanceof JEditorPane).anyMatch(c -> plain((JEditorPane)c).contains("Loading...")));
            });
        } finally { wiki.close(); worker.shutdownNow(); }
    }
    @Test public void coloredDropOddsRemainExactSelectableSourceWithoutDerivedRarityLabels() throws Exception {
        WikiPage page = new WikiPage(DocumentTest.document("Vorkath"));
        ExecutorService worker = Executors.newSingleThreadExecutor(); WikiClient wiki = new WikiClient(new OkHttpClient(), new Gson());
        try {
            SwingUtilities.invokeAndWait(() -> {
                ContentRenderer renderer = new ContentRenderer(url -> {}, worker, new ImageCache(wiki), pin -> {}, error -> fail(error));
                JPanel body = renderer.renderArea(page, page.view.area("drops"), Map.of(), ""); renderer.revealAll();
                List<Component> children = all(body);
                JEditorPane rare = (JEditorPane)children.stream().filter(c -> c instanceof JEditorPane)
                    .filter(c -> ((JEditorPane)c).getClientProperty("wiki.field.presentation") instanceof FieldPresentation)
                    .filter(c -> { FieldPresentation p = (FieldPresentation)((JEditorPane)c).getClientProperty("wiki.field.presentation");
                        return p.probability != null && p.probability.rarity == Probability.Rarity.COMMON; })
                    .findFirst().orElseThrow();
                FieldPresentation display = (FieldPresentation)rare.getClientProperty("wiki.field.presentation");
                assertEquals(display.probability.original, plain(rare));
                assertTrue(children.stream().anyMatch(c -> c instanceof JLabel && "wiki.rarity".equals(c.getName()) && ((JLabel)c).getText().equals("Common")));
                PinSelection.Fragment captured = SelectionCapture.capture(rare, 0, rare.getDocument().getLength());
                assertEquals(display.probability.original, captured.text().strip());
                assertFalse(captured.text().contains("Common"));
                assertTrue(children.stream().anyMatch(c -> c instanceof JEditorPane && plain((JEditorPane)c).equals("2 × 5/150")));
            });
        } finally { wiki.close(); worker.shutdownNow(); }
    }
    @Test public void styledFieldsAndRequirementsFitNormalAndNarrowSidebarWidths() throws Exception {
        ExecutorService worker = Executors.newSingleThreadExecutor(); WikiClient wiki = new WikiClient(new OkHttpClient(), new Gson());
        try {
            for (String[] target : new String[][]{{"Rake", "info"}, {"Ranarr_seed", "methods"}, {"Vorkath", "drops"},
                    {"Recipe_for_Disaster", "requirements"}, {"Bronze_dagger", "stats"}}) {
                WikiPage page = new WikiPage(DocumentTest.document(target[0]));
                ArticleViewModel.Area area = page.view.areas.stream().filter(a -> a.id.equals(target[1])).findFirst().orElse(page.view.areas.get(0));
                SwingUtilities.invokeAndWait(() -> {
                    for (int width : new int[]{185,225}) {
                        ContentRenderer renderer = new ContentRenderer(url -> {}, worker, new ImageCache(wiki));
                        JPanel body = renderer.renderArea(page, area, Map.of(), "");
                        for (int pass = 0; pass < 5; pass++) { body.setSize(width, body.getPreferredSize().height); layout(body); }
                        SidebarLayoutValidator.inspect(body, page.view.audit);
                        for (Component child : all(body)) if (child instanceof JEditorPane || "wiki.rarity".equals(child.getName())) {
                            assertTrue(target[0] + ": width " + width + " " + child.getBounds(), child.getWidth() > 0 && child.getX() >= 0 && child.getX()+child.getWidth() <= child.getParent().getWidth()+1);
                        }
                        renderer.clear();
                    }
                });
                assertTrue(page.view.audit.summary(), page.view.audit.valid());
            }
        } finally { wiki.close(); worker.shutdownNow(); }
    }
}
