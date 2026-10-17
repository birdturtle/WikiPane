package com.wikipane.ui;

import com.google.gson.JsonObject;
import com.wikipane.DocumentTest;
import com.wikipane.document.WikiDocumentParser;
import com.wikipane.view.*;
import java.awt.*;
import java.util.*;
import javax.swing.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class RecordHierarchyTest {
    private static WikiPage page(String html) {
        JsonObject response = new JsonObject(), parse = new JsonObject();
        parse.addProperty("title", "Generic table"); parse.addProperty("text", html); response.add("parse", parse);
        return new WikiPage(new WikiDocumentParser().parse(response));
    }
    private static java.util.List<ReaderLayout.Record> records(WikiPage page) {
        java.util.List<ReaderLayout.Record> result = new ArrayList<>();
        page.document.semantic.nodes.stream().filter(n -> n.table != null).forEach(n -> result.addAll(page.layout.records(n.table)));
        return result;
    }
    @Test public void sharedRowKeysRetainColumnMeaningAndDoNotBecomeNumericFieldLabels() throws Exception {
        for (String fixture : java.util.List.of("Sailing__Level_up_table", "Farming__Level_up_table", "Herblore__Level_up_table")) {
            WikiPage page = new WikiPage(DocumentTest.document(fixture));
            java.util.List<ReaderLayout.Record> rows = records(page);
            assertTrue(fixture, rows.stream().anyMatch(r -> r.keyedIdentity && r.identity.label.equals("Level")));
            for (ReaderLayout.Record row : rows) {
                assertFalse(fixture, row.fields.stream().anyMatch(f -> f.label.matches("\\d+")));
                if (row.keyedIdentity) {
                    assertTrue(row.identity.presentation.numeric);
                    assertFalse(row.fields.stream().anyMatch(f -> f.source.unit.equals(row.identity.source.unit)));
                }
                assertTrue(row.metadata.stream().allMatch(f -> f.presentation.membership));
            }
            assertTrue(page.view.audit.summary(), page.view.audit.valid());
        }
    }
    @Test public void sameRulesWorkForNamedRowsTiersAndNestedContentWithoutArticleKnowledge() {
        WikiPage page = page("<table class='wikitable'><tr><th>Name</th><th>Score</th><th>Details</th></tr>"
            + "<tr><th>Alice</th><td>90</td><td><ol><li>First</li><li>Then <a href='/w/Tools'>tools</a></li></ol></td></tr></table>"
            + "<table><tr><th>Tier</th><th>Effect</th></tr><tr><th>2</th><td>Extra capacity</td></tr></table>");
        ReaderLayout.Record named = records(page).stream().filter(r -> r.identity != null && r.identity.value.text.equals("Alice")).findFirst().orElseThrow();
        assertEquals("Name", named.identity.label);
        assertTrue(named.fields.stream().anyMatch(f -> f.label.equals("Score") && f.value.text.equals("90")));
        assertTrue(named.fields.stream().anyMatch(f -> f.label.equals("Details") && f.value.html.contains("<ol>") && !f.value.links.isEmpty()));
        ReaderLayout.Record tier = records(page).stream().filter(r -> r.keyedIdentity).findFirst().orElseThrow();
        assertEquals("Tier", tier.identity.label); assertEquals("2", tier.identity.value.text);
        assertEquals("Effect", tier.fields.get(0).label);
    }
    @Test public void narrativeRecordsHaveOneLabeledKeyAndReadableMetadataAtSidebarWidths() throws Exception {
        WikiPage page = new WikiPage(DocumentTest.document("Sailing__Level_up_table"));
        ReaderLayout.Record row = records(page).stream().filter(r -> r.keyedIdentity && r.identity.value.text.equals("1")).findFirst().orElseThrow();
        assertFalse(row.metadata.isEmpty());
        SwingUtilities.invokeAndWait(() -> {
            for (int width : new int[]{185, 225}) {
                JComponent card = new ReaderComponents(url -> {}).record(row);
                card.setSize(width, 4000);
                for (int i = 0; i < 8; i++) { layout(card); card.setSize(width, card.getPreferredSize().height); }
                java.util.List<String> text = new ArrayList<>(); check(card, text);
                assertEquals(text.toString(), 1, text.stream().filter(s -> s.strip().equals("1")).count());
                assertTrue(text.toString(), text.stream().anyMatch(s -> s.strip().equals("Level")));
                assertTrue(text.toString(), text.stream().anyMatch(s -> s.strip().equals("Members")));
                assertTrue(text.toString(), text.stream().anyMatch(s -> s.contains("Have 1 boat at a time")));
                assertTrue("Unreasonable narrative height at " + width + ": " + card.getHeight(), card.getHeight() < 650);
            }
        });
    }
    private static void layout(Container root) {
        root.doLayout(); for (Component child : root.getComponents()) if (child instanceof Container) layout((Container) child);
    }
    @Test public void shortSingleDetailRowsStayCompactWithoutDroppingTheirText() throws Exception {
        WikiPage page = new WikiPage(DocumentTest.document("Sailing__Level_up_table"));
        ReaderLayout.Record row = records(page).stream().filter(r -> r.keyedIdentity && r.identity.value.text.equals("2")).findFirst().orElseThrow();
        SwingUtilities.invokeAndWait(() -> {
            for (int width : new int[]{185, 225}) {
                JComponent card = new ReaderComponents(url -> {}).record(row); card.setSize(width, 1000);
                for (int i=0;i<8;i++) { layout(card); card.setSize(width,card.getPreferredSize().height); }
                java.util.List<String> text=new ArrayList<>(); check(card,text);
                assertEquals("wiki.record.summary",card.getComponent(0).getName());
                assertTrue(text.toString(),text.stream().anyMatch(t->t.contains("No new abilities unlocked")));
                assertTrue("Summary grew excessively at " + width + ": "+card.getHeight(),card.getHeight()<=56);
            }
        });
    }
    private static void check(Container root, java.util.List<String> values) {
        for (Component child : root.getComponents()) {
            assertTrue("Horizontal overflow", child.getX() + child.getWidth() <= root.getWidth() + 1);
            if (child instanceof JEditorPane) try {
                JEditorPane pane = (JEditorPane) child;
                String text = pane.getDocument().getText(0, pane.getDocument().getLength()); values.add(text);
                Rectangle last = pane.modelToView(Math.max(0, pane.getDocument().getLength() - 1));
                assertNotNull(last); assertTrue("Clipped text: " + text, last.y + last.height <= pane.getHeight() + 2);
            } catch (javax.swing.text.BadLocationException ex) { throw new AssertionError(ex); }
            if (child instanceof Container) check((Container) child, values);
        }
    }
}
