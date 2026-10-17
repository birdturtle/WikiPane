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
        Reader() throws Exception {
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
            panel = edt(() -> new WikiPanePanel(wiki, workers));
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
        JToggleButton mode(String label) {
            return (JToggleButton) all(panel)
                .stream()
                .filter(c -> c instanceof JToggleButton && label.equals(((JToggleButton) c).getText()))
                .findFirst()
                .orElseThrow();
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
    public void readingHasPinnedIdentityTwoModesAndSearchExpandsOnlyWhenRequested() throws Exception {
        try (Reader reader = new Reader()) {
            reader.open("Recipe for Disaster");
            edt(() -> {
                assertFalse(visible(reader.input(), reader.panel));
                assertEquals(2,
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
    public void eachReadingModeRestoresItsOwnPosition() throws Exception {
        try (Reader reader = new Reader()) {
            reader.open("Recipe for Disaster/Full guide");
            edt(() -> {
                reader.scroller().getVerticalScrollBar().setValue(211);
                reader.mode("Walkthrough").doClick();
                reader.size();
                return null;
            });
            await(() -> reader.mode("Walkthrough").isSelected());
            edt(() -> {
                reader.scroller().getVerticalScrollBar().setValue(347);
                reader.mode("Overview").doClick();
                reader.size();
                return null;
            });
            await(() -> reader.scroller().getVerticalScrollBar().getValue() == 211);
            edt(() -> {
                reader.mode("Walkthrough").doClick();
                reader.size();
                return null;
            });
            await(() -> reader.scroller().getVerticalScrollBar().getValue() == 347);
        }
    }
    @Test
    public void sectionsJumpToTheDestinationAndRemainInBackForwardHistory() throws Exception {
        try (Reader reader = new Reader()) {
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
            await(()
                      -> all(reader.panel)
                          .stream()
                          .anyMatch(c
                              -> c instanceof JLabel && "wiki.currentSection".equals(c.getName())
                                  && ((JLabel) c).getText().equals("Drops")));
            edt(() -> {
                reader.action("Back").doClick();
                return null;
            });
            await(() -> reader.mode("Overview").isSelected());
            edt(() -> {
                reader.action("Forward").doClick();
                return null;
            });
            await(()
                      -> all(reader.panel)
                          .stream()
                          .anyMatch(c
                              -> c instanceof JLabel && "wiki.currentSection".equals(c.getName())
                                  && ((JLabel) c).getText().equals("Drops")));
        }
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
            await(() -> reader.mode("Walkthrough").isSelected());
            edt(() -> {
                assertTrue(all(reader.panel)
                        .stream()
                        .anyMatch(c
                            -> "wiki.currentSection".equals(c.getName()) && c instanceof JLabel
                                && ((JLabel) c)
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
                new SectionNavigator(page.navigation.sections, "walkthrough", "", chosen::add);
            JTextField filter = (JTextField) all(navigator)
                                    .stream()
                                    .filter(c -> c instanceof JTextField)
                                    .findFirst()
                                    .orElseThrow();
            filter.setText("mountain dwarf");
            JList<?> list =
                (JList<?>) all(navigator).stream().filter(c -> c instanceof JList).findFirst().orElseThrow();
            assertTrue(list.getModel().getSize() > 0);
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
