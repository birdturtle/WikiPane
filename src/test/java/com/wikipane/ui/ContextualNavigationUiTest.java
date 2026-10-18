package com.wikipane.ui;

import static org.junit.Assert.*;

import com.google.gson.Gson;
import com.wikipane.DocumentTest;
import com.wikipane.api.WikiClient;
import com.wikipane.view.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.*;
import okhttp3.*;
import org.junit.Test;

public class ContextualNavigationUiTest {
    private static <T> T edt(Callable<T> task) throws Exception {
        FutureTask<T> result = new FutureTask<>(task);
        SwingUtilities.invokeAndWait(result);
        return result.get();
    }
    private static List<Component> all(Container root) {
        List<Component> found = new ArrayList<>();
        for (Component c : root.getComponents()) {
            found.add(c);
            if (c instanceof Container)
                found.addAll(all((Container) c));
        }
        return found;
    }
    private static void await(Callable<Boolean> condition) throws Exception {
        ScheduledExecutorService poll = Executors.newSingleThreadScheduledExecutor();
        CompletableFuture<Void> result = new CompletableFuture<>();
        poll.scheduleAtFixedRate(() -> {
            try {
                if (edt(condition))
                    result.complete(null);
            } catch (Exception error) {
                result.completeExceptionally(error);
            }
        }, 0, 20, TimeUnit.MILLISECONDS);
        try {
            result.get(8, TimeUnit.SECONDS);
        } finally {
            poll.shutdownNow();
        }
    }
    private static void layout(Container root) {
        root.doLayout();
        for (Component c : root.getComponents())
            if (c instanceof Container)
                layout((Container) c);
    }
    private static boolean visible(Component c, Container root) {
        for (Component current = c; current != null && current != root; current = current.getParent())
            if (!current.isVisible())
                return false;
        return true;
    }
    private static final class Reader implements AutoCloseable {
        final ExecutorService workers = Executors.newFixedThreadPool(3);
        final AtomicInteger requests = new AtomicInteger();
        final WikiClient wiki;
        final WikiPanePanel panel;
        Reader() throws Exception { this(null); }
        Reader(java.util.function.Consumer<com.wikipane.pin.PinSelection> pin) throws Exception {
            wiki = new WikiClient(
                new OkHttpClient.Builder()
                    .addInterceptor(chain -> {
                        requests.incrementAndGet();
                        String page = chain.request().url().queryParameter("page");
                        String body;
                        if (page != null)
                            body = DocumentTest.fixture(page.replace("/", "__").replace(' ', '_')).toString();
                        else if ("opensearch".equals(chain.request().url().queryParameter("action")))
                            body = "[\"bronze\",[\"Bronze dagger\",\"Vorkath\"],[],[]]";
                        else
                            body = "{\"query\":{\"search\":[{\"title\":\"Bronze "
                                + "dagger\"},{\"title\":\"Vorkath\"}]}}";
                        return new Response.Builder()
                            .request(chain.request())
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("fixture")
                            .body(ResponseBody.create(MediaType.get("application/json"), body))
                            .build();
                    })
                    .build(),
                new Gson());
            panel = edt(() -> new WikiPanePanel(wiki, workers, pin));
        }
        void size() {
            panel.setSize(225, 780);
            for (int pass = 0; pass < 4; pass++) layout(panel);
        }
        JButton action(String tooltip) {
            return (JButton) all(panel)
                .stream()
                .filter(c -> c instanceof JButton && tooltip.equals(((JButton) c).getToolTipText()))
                .findFirst()
                .orElseThrow();
        }
        WikiPage opened;
        String sectionLabel() {
            return ((JButton) all(panel).stream().filter(c -> "wiki.currentSection".equals(c.getName())).findFirst().orElseThrow()).getText();
        }
        void chooseMode(String label) {
            panel.navigateSection(opened.navigation.sections.stream().filter(d -> d.depth == 0 && d.label.equals(label)).findFirst().orElseThrow());
        }
        JTextField input() {
            return (JTextField) all(panel)
                .stream()
                .filter(c -> "wiki.search".equals(c.getName()))
                .findFirst()
                .orElseThrow();
        }
        JScrollPane scroller() {
            return (JScrollPane) all(panel)
                .stream()
                .filter(c -> c instanceof JScrollPane)
                .findFirst()
                .orElseThrow();
        }
        boolean article(String title) {
            return all(panel).stream().anyMatch(c
                -> c instanceof JEditorPane && "wiki.articleTitle".equals(c.getName()) && visible(c, panel)
                    && org.jsoup.Jsoup.parseBodyFragment(((JEditorPane) c).getText()).text().equals(title));
        }
        void open(String title) throws Exception {
            opened = new WikiPage(DocumentTest.document(title.replace("/", "__").replace(' ', '_')));
            edt(() -> {
                panel.openTitle(title, "");
                return null;
            });
            await(() -> article(title));
            edt(() -> {
                size();
                return null;
            });
        }
        public void close() throws Exception {
            edt(() -> {
                panel.stop();
                return null;
            });
            wiki.close();
            workers.shutdownNow();
        }
    }
    @Test
    public void readingHasOneSectionDropdownAndSearchExpandsOnlyWhenRequested() throws Exception {
        try (Reader reader = new Reader()) {
            reader.open("Recipe for Disaster");
            edt(() -> {
                assertFalse(visible(reader.input(), reader.panel));
                JButton selector = (JButton) all(reader.panel).stream().filter(c -> "wiki.currentSection".equals(c.getName())).findFirst().orElseThrow();
                assertEquals("Overview",selector.getText());
                assertNotNull(selector.getIcon());
                selector.doClick();
                assertTrue(all(selector.getComponentPopupMenu()).stream().anyMatch(c -> "wiki.sections".equals(c.getName())));
                assertEquals(1,all(reader.panel).stream().filter(c -> "wiki.currentSection".equals(c.getName())).count());
                assertEquals(0,
                    all(reader.panel)
                        .stream()
                        .filter(c -> c instanceof JToggleButton && visible(c, reader.panel))
                        .count());
                assertFalse(all(reader.panel)
                        .stream()
                        .anyMatch(c -> c instanceof JButton && ((JButton) c).getText().equals("Results")));
                JComponent header = (JComponent) all(reader.panel)
                                        .stream()
                                        .filter(c -> "wiki.articleTitle".equals(c.getName()))
                                        .findFirst()
                                        .orElseThrow();
                Point before = SwingUtilities.convertPoint(header, 0, 0, reader.panel);
                reader.scroller().getVerticalScrollBar().setValue(180);
                assertEquals(before, SwingUtilities.convertPoint(header, 0, 0, reader.panel));
                assertTrue("Controls consume too much height",
                    SwingUtilities.convertPoint(reader.scroller(), 0, 0, reader.panel).y < 175);
                reader.action("Search").doClick();
                reader.size();
                assertTrue(visible(reader.input(), reader.panel));
                assertTrue(reader.input().getHeight() < 40);
                return null;
            });
            edt(() -> {
                reader.action("Back").doClick();
                return null;
            });
            await(() -> reader.article("Recipe for Disaster"));
        }
    }
    @Test
    public void backReturnsToCachedSearchResultsAndForwardRestoresTheArticleWithoutRequests()
        throws Exception {
        try (Reader reader = new Reader()) {
            edt(() -> {
                reader.input().setText("bronze");
                reader.input().postActionEvent();
                return null;
            });
            await(
                ()
                    -> all(reader.panel)
                        .stream()
                        .anyMatch(
                            c -> c instanceof JButton && ((JButton) c).getText().contains("Bronze dagger")));
            edt(() -> {
                ((JButton) all(reader.panel)
                        .stream()
                        .filter(
                            c -> c instanceof JButton && ((JButton) c).getText().contains("Bronze dagger"))
                        .findFirst()
                        .orElseThrow())
                    .doClick();
                return null;
            });
            await(() -> reader.article("Bronze dagger"));
            int requests = reader.requests.get();
            edt(() -> {
                reader.action("Back").doClick();
                return null;
            });
            await(() -> visible(reader.input(), reader.panel));
            edt(() -> {
                assertEquals("bronze", reader.input().getText());
                assertTrue(all(reader.panel)
                        .stream()
                        .anyMatch(
                            c -> c instanceof JButton && ((JButton) c).getText().contains("Bronze dagger")));
                reader.action("Forward").doClick();
                return null;
            });
            await(() -> reader.article("Bronze dagger"));
            assertEquals(requests, reader.requests.get());
        }
    }
    @Test
    public void sectionsShareOneReaderAndArticleHistoryRestoresTheLatestReadingPosition() throws Exception {
        try(Reader reader=new Reader()) {
            reader.open("Recipe for Disaster/Full guide");
            int saved=edt(() -> {reader.scroller().getVerticalScrollBar().setValue(211);return reader.scroller().getVerticalScrollBar().getValue();});
            reader.open("Rake");
            edt(() -> {reader.action("Back").doClick();reader.size();return null;});
            await(() -> reader.article("Recipe for Disaster/Full guide") && reader.scroller().getVerticalScrollBar().getValue()==saved);
        }
    }
    @Test
    public void cookingWineJumpAlignsTheHeadingAtTheTopOfTheViewport() throws Exception {
        try (Reader reader = new Reader()) {
            reader.open("Cooking");
            ArticleNavigation.Destination wine = reader.opened.navigation.sections.stream()
                .filter(d -> d.anchor.equals("Wine")).findFirst().orElseThrow();
            edt(() -> {
                reader.panel.navigateSection(wine); reader.size();
                // A non-displayable test panel needs the layout pass normally scheduled by RepaintManager.
                SwingUtilities.invokeLater(reader::size);
                return null;
            });
            try { await(() -> {
                reader.size();
                Component heading = all(reader.panel).stream()
                    .filter(c -> c instanceof JButton && "wiki.sectionHeading".equals(c.getName())
                        && ((com.wikipane.view.ArticleNavigation.Destination)((JComponent)c).getClientProperty("wiki.destination")).label.equals("Wine"))
                    .findFirst().orElse(null);
                if (heading == null) return false;
                JViewport viewport = reader.scroller().getViewport();
                int y = SwingUtilities.convertPoint(heading,0,0,viewport).y;
                return y >= 0 && y <= 2;
            });
            } catch (TimeoutException ex) {
                throw new AssertionError(edt(() -> all(reader.panel).stream()
                    .filter(c -> c instanceof JButton && "wiki.sectionHeading".equals(c.getName()))
                    .map(c -> ((JButton)c).getText()+"@"+SwingUtilities.convertPoint(c,0,0,reader.scroller().getViewport()).y)
                    .collect(java.util.stream.Collectors.joining(",")) + " scroll=" + reader.scroller().getViewport().getViewPosition()),ex);
            }
            assertEquals("Wine",edt(reader::sectionLabel));
        }
    }
    @Test
    public void sectionsUpdateTheArticleWithoutAddingBackForwardStops() throws Exception {
        try (Reader reader = new Reader()) {
            reader.open("Rake");
            reader.open("Vorkath");
            WikiPage page = new WikiPage(DocumentTest.document("Vorkath"));
            ArticleNavigation.Destination drops = page.navigation.sections.stream()
                                                      .filter(d -> d.label.equals("Drops") && d.depth == 0)
                                                      .findFirst()
                                                      .orElseThrow();
            edt(() -> {
                reader.panel.navigateSection(drops);
                return null;
            });
            await(() -> { reader.size(); return all(reader.panel)
                          .stream()
                          .anyMatch(c
                              -> c instanceof JButton && "wiki.currentSection".equals(c.getName())
                                  && ((JButton) c).getText().equals("Drops")); });
            edt(() -> {
                reader.action("Back").doClick();
                return null;
            });
            await(() -> reader.article("Rake"));
            edt(() -> {
                reader.action("Forward").doClick();
                return null;
            });
            await(() -> { reader.size(); return all(reader.panel)
                          .stream()
                          .anyMatch(c
                              -> c instanceof JButton && "wiki.currentSection".equals(c.getName())
                                  && ((JButton) c).getText().equals("Drops")); });
        }
    }
    @Test
    public void temporaryInspectionPreservesArticleAndExplicitExploreParticipatesInHistory() throws Exception {
        try (Reader reader = new Reader()) {
            reader.open("Recipe for Disaster");
            WikiClient temporaryWiki = new WikiClient(new OkHttpClient.Builder().addInterceptor(chain -> {
                okhttp3.HttpUrl url = chain.request().url();
                Response.Builder response = new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                    .code(200).message("fixture");
                String body = "resolved";
                if (url.encodedPath().equals("/w/Special:Lookup")) {
                    response.code(302).header("Location", "/w/Bronze_dagger");
                } else if (url.encodedPath().equals("/api.php")) {
                    body = DocumentTest.fixture("Bronze_dagger").toString();
                }
                return response.body(ResponseBody.create(MediaType.get("application/json"), body)).build();
            }).build(), new Gson());
            com.wikipane.inspect.QuickInspectController inspector = new com.wikipane.inspect.QuickInspectController(
                new com.wikipane.knowledge.KnowledgeService(temporaryWiki),
                new com.wikipane.cache.ImageCache(temporaryWiki), reader.workers,
                SwingUtilities::invokeLater, reader.panel::openLink);
            try {
                edt(() -> {
                    reader.scroller().getVerticalScrollBar().setValue(211);
                    inspector.inspect(com.wikipane.integration.GameEntityLookup.url("item", 1205, "Bronze dagger", null));
                    return null;
                });
                await(() -> inspector.view() != null && inspector.view().inspection != null);
                edt(() -> {
                    assertTrue(reader.article("Recipe for Disaster"));
                    assertEquals(211, reader.scroller().getVerticalScrollBar().getValue());
                    inspector.dismiss();
                    assertTrue(reader.article("Recipe for Disaster"));
                    assertEquals(211, reader.scroller().getVerticalScrollBar().getValue());
                    inspector.inspect(com.wikipane.integration.GameEntityLookup.url("item", 1205, "Bronze dagger", null));
                    return null;
                });
                await(() -> inspector.view() != null && inspector.view().inspection != null);
                edt(() -> { inspector.explore(); return null; });
                await(() -> reader.article("Bronze dagger"));
                edt(() -> { reader.action("Back").doClick(); return null; });
                await(() -> reader.article("Recipe for Disaster") && reader.scroller().getVerticalScrollBar().getValue() == 211);
            } finally {
                edt(() -> { inspector.stop(); return null; });
                temporaryWiki.close();
            }
        }
    }
    @Test
    public void highlightRightClickPinUsesSemanticSourceAndLeavesTheReaderInPlace() throws Exception {
        List<com.wikipane.pin.PinSelection> pinned = new ArrayList<>();
        try (Reader reader = new Reader(pinned::add)) {
            reader.open("Recipe for Disaster");
            edt(() -> {
                ArticleNavigation.Destination requirements=reader.opened.navigation.sections.stream().filter(d -> d.label.equals("Requirements")).findFirst().orElseThrow();
                reader.panel.navigateSection(requirements);reader.size();return null;
            });
            await(() -> all(reader.panel).stream().anyMatch(c -> c instanceof JEditorPane && ((JEditorPane)c).getClientProperty("wiki.pin.context") instanceof com.wikipane.pin.PinContext
                && ((com.wikipane.pin.PinContext)((JEditorPane)c).getClientProperty("wiki.pin.context")).condition!=null));
            edt(() -> {
                reader.scroller().getVerticalScrollBar().setValue(211);
                JEditorPane passage = (JEditorPane)all(reader.panel).stream().filter(component -> {
                    if (!(component instanceof JEditorPane)) return false;
                    JEditorPane pane = (JEditorPane)component;
                    Object context = pane.getClientProperty("wiki.pin.context");
                    return context instanceof com.wikipane.pin.PinContext
                        && ((com.wikipane.pin.PinContext)context).condition != null
                        && org.jsoup.Jsoup.parseBodyFragment(pane.getText()).text().contains("Cooking");
                }).findFirst().orElseThrow();
                passage.select(0, passage.getDocument().getLength());
                JPopupMenu menu = passage.getComponentPopupMenu();
                for (javax.swing.event.PopupMenuListener listener : menu.getPopupMenuListeners())
                    listener.popupMenuWillBecomeVisible(new javax.swing.event.PopupMenuEvent(menu));
                JMenuItem pin = (JMenuItem)menu.getComponent(0);
                assertEquals("Pin selection", pin.getText()); assertTrue(pin.isEnabled());
                int requests = reader.requests.get(); pin.doClick();
                assertEquals(1, pinned.size());
                assertTrue(reader.article("Recipe for Disaster"));
                assertEquals(211, reader.scroller().getVerticalScrollBar().getValue());
                assertEquals(requests, reader.requests.get());
                com.wikipane.pin.PinSelection selected = pinned.get(0);
                assertEquals("Recipe for Disaster", selected.sources.get(0).article);
                assertTrue(selected.fragments.get(0).text().contains("Cooking"));
                assertNotNull(selected.fragments.get(0).context.condition);
                return null;
            });
        }
        assertFalse(pinned.get(0).fragments.get(0).paragraphs.isEmpty());
    }
    @Test
    public void parentChapterAnchorsMaterializeTheirActualChildContent() throws Exception {
        WikiPage page = new WikiPage(DocumentTest.document("Recipe_for_Disaster__Full_guide"));
        ArticleNavigation.Destination chapter =
            page.navigation.sections.stream()
                .filter(d
                    -> d.area.equals("walkthrough")
                        && d.label.toLowerCase(Locale.ROOT).contains("mountain dwarf"))
                .findFirst()
                .orElseThrow();
        try (Reader reader = new Reader()) {
            edt(() -> {
                ContentRenderer renderer = new ContentRenderer(
                    url -> {}, reader.workers, new com.wikipane.cache.ImageCache(reader.wiki));
                renderer.renderArea(page, page.navigation.area(chapter.area), Map.of(), "");
                renderer.revealAnchor(chapter.anchor);
                assertNotNull(renderer.anchor(chapter.anchor));
                return null;
            });
            reader.open(page.document.title);
            edt(() -> {
                reader.panel.navigateSection(chapter);
                return null;
            });
            await(() -> {
                reader.size();
                Component header=all(reader.panel).stream().filter(c -> c instanceof JButton&&"wiki.sectionHeading".equals(c.getName()))
                    .filter(c -> {Object value=((JComponent)c).getClientProperty("wiki.destination");return value instanceof ArticleNavigation.Destination&&((ArticleNavigation.Destination)value).anchor.equals(chapter.anchor);})
                    .findFirst().orElse(null);
                return header!=null&&Math.abs(SwingUtilities.convertPoint(header,0,0,reader.scroller().getViewport()).y)<=2
                    &&reader.sectionLabel().toLowerCase(Locale.ROOT).contains("mountain dwarf");
            });
            edt(() -> {
                assertTrue(all(reader.panel)
                        .stream()
                        .anyMatch(c
                            -> "wiki.currentSection".equals(c.getName()) && c instanceof JButton
                                && ((JButton) c)
                                    .getText()
                                    .toLowerCase(Locale.ROOT)
                                    .contains("mountain dwarf")));
                return null;
            });
        }
    }
    @Test
    public void sectionFilterAndEnterChooseAnActualChapterInOneStep() throws Exception {
        WikiPage page = new WikiPage(DocumentTest.document("Recipe_for_Disaster__Full_guide"));
        edt(() -> {
            List<ArticleNavigation.Destination> chosen = new ArrayList<>();
            SectionNavigator navigator =
                new SectionNavigator(page.navigation.roots, Set.of("overview\0"), "walkthrough", "", chosen::add);
            JTextField filter = (JTextField) all(navigator)
                                    .stream()
                                    .filter(c -> c instanceof JTextField)
                                    .findFirst()
                                    .orElseThrow();
            filter.setText("mountain dwarf");
            JTree list =
                (JTree) all(navigator).stream().filter(c -> c instanceof JTree).findFirst().orElseThrow();
            assertTrue(list.getRowCount() > 0);
            list.getActionMap().get("choose").actionPerformed(
                new java.awt.event.ActionEvent(list, 0, "choose"));
            assertEquals(1, chosen.size());
            assertEquals("walkthrough", chosen.get(0).area);
            assertFalse(chosen.get(0).anchor.isBlank());
            assertTrue(chosen.get(0).label.toLowerCase(Locale.ROOT).contains("mountain dwarf"));
            return null;
        });
    }
}
