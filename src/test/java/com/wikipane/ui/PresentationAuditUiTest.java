package com.wikipane.ui;

import static org.junit.Assert.*;

import com.google.gson.Gson;
import com.wikipane.DocumentTest;
import com.wikipane.api.*;
import com.wikipane.semantic.SemanticDocument.*;
import com.wikipane.view.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import javax.swing.*;
import okhttp3.*;
import org.junit.Test;

public class PresentationAuditUiTest {
    private <T> T edt(Callable<T> task) throws Exception {
        FutureTask<T> result = new FutureTask<>(task);
        SwingUtilities.invokeAndWait(result);
        return result.get();
    }
    private List<Component> all(Container parent) {
        List<Component> result = new ArrayList<>();
        for (Component c : parent.getComponents()) {
            result.add(c);
            if (c instanceof Container)
                result.addAll(all((Container) c));
        }
        return result;
    }
    private void await(Callable<Boolean> condition) throws Exception {
        ScheduledExecutorService poll = Executors.newSingleThreadScheduledExecutor();
        CompletableFuture<Void> done = new CompletableFuture<>();
        poll.scheduleAtFixedRate(() -> {
            try {
                if (edt(condition))
                    done.complete(null);
            } catch (Exception e) {
                done.completeExceptionally(e);
            }
        }, 0, 20, TimeUnit.MILLISECONDS);
        try {
            done.get(8, TimeUnit.SECONDS);
        } finally {
            poll.shutdownNow();
        }
    }
    private boolean referencesSelected(WikiPanePanel panel) {
        return all(panel).stream().anyMatch(c
            -> c instanceof JButton && "wiki.currentSection".equals(c.getName())
                && ((JButton) c).getText().equals("References"));
    }
    @Test
    public void citationDefinitionBacklinkAndHistoryUseTheSameNativeNavigation() throws Exception {
        String fixture = "Recipe_for_Disaster__Full_guide";
        WikiPage page = new WikiPage(DocumentTest.document(fixture));
        Node origin = page.document.semantic.nodes.stream()
                          .filter(n -> !PresentationValidator.citations(n).isEmpty())
                          .findFirst()
                          .orElseThrow();
        Citation citation = PresentationValidator.citations(origin).get(0);
        WikiClient wiki =
            new WikiClient(new OkHttpClient.Builder()
                               .addInterceptor(chain
                                   -> new Response.Builder()
                                       .request(chain.request())
                                       .protocol(Protocol.HTTP_1_1)
                                       .code(200)
                                       .message("fixture")
                                       .body(ResponseBody.create(MediaType.get("application/json"),
                                           DocumentTest.fixture(fixture).toString()))
                                       .build())
                               .build(),
                new Gson());
        ExecutorService workers = Executors.newFixedThreadPool(3);
        WikiPanePanel panel = edt(() -> new WikiPanePanel(wiki, workers));
        try {
            edt(() -> {
                panel.openTitle(page.document.title, origin.source.section);
                return null;
            });
            await(()
                      -> all(panel).stream().anyMatch(c
                          -> c instanceof JEditorPane && "wiki.articleTitle".equals(c.getName())
                              && ((JEditorPane) c).getText().contains("Recipe")));
            edt(() -> {
                panel.openLink(citation.url);
                return null;
            });
            await(() -> referencesSelected(panel));
            edt(() -> {
                assertTrue(all(panel).stream().anyMatch(c -> "wiki.reference".equals(c.getName())));
                JButton backTo = (JButton) all(panel)
                                     .stream()
                                     .filter(c
                                         -> c instanceof JButton
                                             && ((JButton) c).getText().startsWith("Referenced from"))
                                     .findFirst()
                                     .orElseThrow();
                assertNotNull(backTo.getComponentPopupMenu());
                ((JMenuItem) backTo.getComponentPopupMenu().getComponent(0)).doClick();
                return null;
            });
            await(() -> !referencesSelected(panel));
            edt(() -> {
                ((JButton) all(panel)
                        .stream()
                        .filter(c -> c instanceof JButton && "Back".equals(((JButton) c).getToolTipText()))
                        .findFirst()
                        .orElseThrow())
                    .doClick();
                return null;
            });
            await(() -> referencesSelected(panel));
        } finally {
            edt(() -> {
                panel.stop();
                return null;
            });
            wiki.close();
            workers.shutdownNow();
        }
    }
    @Test
    public void narrowFieldsStackAndHighlightGridFallsBackWithoutLosingValues() throws Exception {
        WikiPage item = new WikiPage(DocumentTest.document("Bronze_dagger"));
        WikiPage monster = new WikiPage(DocumentTest.document("Vorkath"));
        edt(() -> {
            ReaderComponents components = new ReaderComponents(url -> {});
            components.audit(item.view.audit);
            ReaderLayout.Field fact = item.view.area("stats")
                                          .components.stream()
                                          .flatMap(c -> item.layout.section(c.id).facts.stream())
                                          .flatMap(g -> g.fields.stream())
                                          .findFirst()
                                          .orElseThrow();
            JComponent row = components.field(fact);
            row.setSize(110, 100);
            row.doLayout();
            assertTrue(row.getComponent(1).getY() > row.getComponent(0).getY());
            assertTrue(item.view.audit.findings().stream().anyMatch(f -> f.rule.equals("FACT_STACKED")));
            components.audit(monster.view.audit);
            JComponent strip = components.highlights(monster.layout.highlights(""));
            strip.setSize(130, 200);
            strip.doLayout();
            assertTrue(strip.getComponent(1).getY() > strip.getComponent(0).getY());
            assertTrue(
                monster.view.audit.findings().stream().anyMatch(f -> f.rule.equals("HIGHLIGHTS_STACKED")));
            return null;
        });
    }
    @Test
    public void measuredOverflowAndClippingProduceDiagnosticsWithoutRandomRepairLoops() throws Exception {
        edt(() -> {
            JPanel root = new JPanel(null);
            root.setSize(100, 100);
            JEditorPane text =
                new ReaderComponents(url -> {}).text("Several words need more than five pixels of height.");
            text.setBounds(90, 0, 50, 5);
            root.add(text);
            CompositionAudit audit = new CompositionAudit(0, 0);
            assertTrue(SidebarLayoutValidator.inspect(root, audit) > 0);
            assertTrue(audit.findings().stream().anyMatch(f -> f.rule.equals("HORIZONTAL_OVERFLOW")));
            assertTrue(audit.findings().stream().anyMatch(f -> f.rule.equals("TEXT_HEIGHT")));
            assertEquals(90, text.getX());
            return null;
        });
    }
    @Test
    public void thumbnailFitsActualWidthAndPaintsTheWholeImage() throws Exception {
        edt(() -> {
            CompositionAudit audit = new CompositionAudit(0, 0);
            FittedThumbnail image = new FittedThumbnail(200, 220, 200, 100, audit);
            BufferedImage source = new BufferedImage(200, 100, BufferedImage.TYPE_INT_RGB);
            Graphics2D sg = source.createGraphics();
            sg.setColor(Color.RED);
            sg.fillRect(0, 0, 100, 100);
            sg.setColor(Color.BLUE);
            sg.fillRect(100, 0, 100, 100);
            sg.dispose();
            image.image(source);
            JPanel parent = new JPanel();
            parent.setSize(100, 100);
            parent.add(image);
            Dimension size = image.getPreferredSize();
            assertEquals(100, size.width);
            assertEquals(58, size.height);
            image.setSize(size);
            BufferedImage output = new BufferedImage(100, 58, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = output.createGraphics();
            image.paint(g);
            g.dispose();
            assertEquals(Color.RED.getRGB(), output.getRGB(2, 20));
            assertEquals(Color.BLUE.getRGB(), output.getRGB(97, 20));
            assertTrue(audit.findings().stream().anyMatch(f -> f.rule.equals("IMAGE_FITTED")));
            return null;
        });
    }
}
