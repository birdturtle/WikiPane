package com.wikipane.pin;

import com.google.gson.JsonObject;
import com.wikipane.document.*;
import com.wikipane.pin.PinSelection.*;
import com.wikipane.semantic.SemanticDocument.*;
import com.wikipane.ui.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import javax.swing.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class PinSelectionTest {
    public static <T> T edt(Callable<T> work) throws Exception {
        FutureTask<T> task = new FutureTask<>(work); SwingUtilities.invokeAndWait(task); return task.get();
    }
    public static WikiDocument document(String html) {
        JsonObject parse = new JsonObject(); parse.addProperty("title", "Pin test"); parse.addProperty("revid", 7);
        parse.addProperty("text", html); JsonObject response = new JsonObject(); response.add("parse", parse);
        return new WikiDocumentParser().parse(response);
    }
    public static JEditorPane pane(String html, PinContext context) {
        JEditorPane pane = new ReaderComponents(url -> {}).text(html);
        pane.putClientProperty("wiki.pin.context", context); return pane;
    }
    public static Fragment whole(JEditorPane pane) {
        return SelectionCapture.capture(pane, 0, pane.getDocument().getLength());
    }
    public static PinSelection simple(String html) throws Exception {
        WikiDocument doc = document(html);
        return edt(() -> new PinSelection(List.of(whole(pane(html, new PinContext(doc.semantic, doc.semantic.nodes.get(0)))))));
    }
    private static String text(PinLayout layout) {
        StringBuilder text = new StringBuilder();
        for (List<PinLayout.Line> page : layout.pages) for (PinLayout.Line line : page) text.append(line.plain);
        return text.toString();
    }
    @Test public void numberedListsNestedBulletsEmphasisAndLinksSurviveSelection() throws Exception {
        PinSelection selection = simple("<ol start='5'><li>Bring <b>milk</b> and <em>flour</em>.</li>"
            + "<li>Visit <a href='https://oldschool.runescape.wiki/w/Cook'>Cook</a>.<ul><li>Keep this nested.</li></ul></li></ol>");
        List<Paragraph> paragraphs = selection.fragments.get(0).paragraphs;
        assertTrue(paragraphs.stream().anyMatch(p -> p.marker.equals("5.") && p.text().contains("milk")));
        assertTrue(paragraphs.stream().anyMatch(p -> p.marker.equals("6.") && p.text().contains("Visit")));
        assertTrue(paragraphs.stream().anyMatch(p -> p.marker.equals("•") && p.depth == 1));
        assertTrue(paragraphs.stream().flatMap(p -> p.spans.stream()).anyMatch(s -> s.bold && s.text.contains("milk")));
        assertTrue(paragraphs.stream().flatMap(p -> p.spans.stream()).anyMatch(s -> s.italic && s.text.contains("flour")));
        assertTrue(paragraphs.stream().flatMap(p -> p.spans.stream()).anyMatch(s -> s.link.endsWith("/w/Cook")));
        PinLayout layout = new PinLayout(selection);
        assertTrue(text(layout).contains("5. Bring"));
        assertTrue(text(layout).contains("6. Visit"));
        assertEquals(7, selection.sources.get(0).revision);
    }
    @Test public void explicitLineBreaksAreSeparateReadableParagraphs() throws Exception {
        PinSelection selection = simple("<p>First line<br>Second line</p>");
        assertEquals(2, selection.fragments.get(0).paragraphs.size());
        assertEquals("First line", selection.fragments.get(0).paragraphs.get(0).text());
        assertEquals("Second line", selection.fragments.get(0).paragraphs.get(1).text());
    }
    @Test public void partialStatisticIsLabeledAsAnExcerptNotAChangedValue() throws Exception {
        WikiDocument doc = document("<table class='infobox infobox-item'><tr><th colspan='2'>Attack bonuses</th></tr>"
            + "<tr><th>Stab</th><td>+40</td></tr></table>");
        Node node = doc.semantic.nodes.get(0);
        Fact fact = node.facts.stream().filter(f -> f.label.equals("Stab")).findFirst().orElseThrow();
        PinContext context = new PinContext(doc.semantic, node).group(fact.group)
            .field(fact.source, fact.label, fact.value, PinContext.Part.VALUE);
        PinSelection selection = edt(() -> {
            JEditorPane pane = pane(fact.value.html, context);
            int offset = pane.getDocument().getText(0, pane.getDocument().getLength()).indexOf("+40");
            return new PinSelection(List.of(SelectionCapture.capture(pane, offset, offset + 2)));
        });
        String output = text(new PinLayout(selection));
        assertTrue(output.contains("Attack bonuses"));
        assertTrue(output.contains("Stab (excerpt): +4"));
        assertFalse(output.contains("Stab: +4"));
        assertEquals("+40", selection.fragments.get(0).context.value.text);
    }
    @Test public void crossParagraphSelectionProducesOneImmediatePinWithoutChangingText() throws Exception {
        WikiDocument doc = document("<p>First passage</p><p>Second passage</p>");
        edt(() -> {
            List<PinSelection> pinned = new ArrayList<>();
            ReaderSelection selection = new ReaderSelection(pinned::add, error -> fail(error));
            JPanel root = ReaderComponents.column();
            JEditorPane first = pane("First passage", new PinContext(doc.semantic, doc.semantic.nodes.get(0)));
            JEditorPane second = pane("Second passage", new PinContext(doc.semantic, doc.semantic.nodes.get(1)));
            root.add(first); root.add(second); selection.refresh(root);
            selection.selectRange(first, 1, second, second.getDocument().getLength());
            JPopupMenu popup = first.getComponentPopupMenu();
            for (javax.swing.event.PopupMenuListener listener : popup.getPopupMenuListeners())
                listener.popupMenuWillBecomeVisible(new javax.swing.event.PopupMenuEvent(popup));
            JMenuItem pin = (JMenuItem)popup.getComponent(0);
            assertEquals("Pin selection", pin.getText()); assertTrue(pin.isEnabled()); pin.doClick();
            assertEquals(1, pinned.size()); assertEquals(2, pinned.get(0).fragments.size());
            assertTrue(text(new PinLayout(pinned.get(0))).contains("First passageSecond passage"));
            assertEquals("First passage", pinned.get(0).fragments.get(0).text());
            selection.clear(); assertNull(first.getComponentPopupMenu());
            return null;
        });
    }
    @Test public void labelsAndValuesAreNotDuplicatedWhenBothAreHighlighted() throws Exception {
        WikiDocument doc = document("<table class='infobox infobox-item'><tr><th>Weight</th><td>2 kg</td></tr></table>");
        Node node = doc.semantic.nodes.get(0); Fact fact = node.facts.get(0);
        PinContext context = new PinContext(doc.semantic, node);
        PinSelection selection = edt(() -> new PinSelection(List.of(
            whole(pane("Weight", context.field(fact.source, fact.label, fact.value, PinContext.Part.LABEL))),
            whole(pane("2 kg", context.field(fact.source, fact.label, fact.value, PinContext.Part.VALUE))))));
        assertEquals("Weight: 2 kg", text(new PinLayout(selection)));
        assertEquals(2, selection.fragments.size());
    }
    @Test public void longSelectionsArePaginatedWithEverySelectedWordRetained() throws Exception {
        StringBuilder html = new StringBuilder("<ol>");
        for (int i = 1; i <= 55; i++) html.append("<li>Entry_").append(i).append(" unique content.</li>");
        html.append("</ol>");
        PinSelection selection = simple(html.toString());
        PinLayout layout = new PinLayout(selection);
        assertTrue(layout.pages.size() > 1);
        String output = text(layout);
        for (int i = 1; i <= 55; i++) assertTrue("Missing selected entry " + i, output.contains("Entry_" + i + " unique content."));
        for (List<PinLayout.Line> page : layout.pages) {
            assertTrue(page.size() <= PinLayout.LINES);
            for (PinLayout.Line line : page) assertTrue(line.text.getAdvance() + line.x <= PinLayout.INNER + 1);
        }
    }
    @Test public void monsterStatisticRetainsItsExactVariantAndValue() throws Exception {
        WikiDocument doc = com.wikipane.DocumentTest.document("Vorkath");
        com.wikipane.knowledge.KnowledgeArticle article = new com.wikipane.knowledge.KnowledgeArticle(doc, "8061", "");
        Node node = article.nodes.stream().filter(n -> n.facts.stream().anyMatch(f -> f.label.equals("Hitpoints") && f.value.text.equals("750")))
            .findFirst().orElseThrow();
        Fact fact = node.facts.stream().filter(f -> f.label.equals("Hitpoints") && f.value.text.equals("750")).findFirst().orElseThrow();
        PinContext context = new PinContext(doc.semantic, node).field(fact.source, fact.label, fact.value, PinContext.Part.VALUE);
        PinSelection selection = edt(() -> new PinSelection(List.of(whole(pane(fact.value.html, context)))));
        String output = text(new PinLayout(selection));
        assertTrue(output.contains("Post-quest")); assertTrue(output.contains("Hitpoints: 750"));
        assertEquals("Post-quest", selection.fragments.get(0).context.node.table.variant);
    }
    @Test public void combatStrategyCannotBypassOverlayRulesThroughSelection() throws Exception {
        WikiDocument doc = com.wikipane.DocumentTest.document("Vorkath__Strategies");
        Node node = doc.semantic.nodes.stream().filter(n -> n.text != null && !n.text.text.isBlank()).findFirst().orElseThrow();
        PinSelection selection = edt(() -> new PinSelection(List.of(whole(pane(node.text.html, new PinContext(doc.semantic, node))))));
        assertFalse(selection.fragments.isEmpty()); // It remains ordinary selectable/copyable reader text.
        try { new PinLayout(selection); fail(); }
        catch (IllegalArgumentException expected) { assertTrue(expected.getMessage().contains("Combat strategy")); }
    }
    @Test public void completeValuesWithNonBreakingSpacesAreNotMistakenForPartialSelections() throws Exception {
        WikiDocument doc = document("<table class='infobox infobox-item'><tr><th>Weight</th><td>2&nbsp;kg</td></tr></table>");
        Node node = doc.semantic.nodes.get(0); Fact fact = node.facts.get(0);
        PinContext context = new PinContext(doc.semantic, node).field(fact.source, fact.label, fact.value, PinContext.Part.VALUE);
        PinSelection selection = edt(() -> new PinSelection(List.of(whole(pane("2&nbsp;kg", context)))));
        assertEquals("Weight: 2 kg", text(new PinLayout(selection)).replace('\u00a0', ' '));
    }
    @Test public void excessiveSelectionsAreRejectedWithoutTruncation() throws Exception {
        try { simple("<p>" + "a".repeat(PinSelection.MAX_CHARACTERS + 1) + "</p>"); fail(); }
        catch (ExecutionException expected) { assertTrue(expected.getCause().getMessage().contains("smaller")); }
    }
}
