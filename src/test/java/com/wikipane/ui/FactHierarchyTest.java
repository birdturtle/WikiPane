package com.wikipane.ui;

import com.google.gson.Gson;
import com.wikipane.DocumentTest;
import com.wikipane.api.WikiClient;
import com.wikipane.cache.ImageCache;
import com.wikipane.view.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import javax.swing.*;
import okhttp3.OkHttpClient;
import org.junit.Test;
import static org.junit.Assert.*;

public class FactHierarchyTest {
    private static List<Component> all(Container parent) {
        List<Component> result = new ArrayList<>();
        for (Component child : parent.getComponents()) { result.add(child); if (child instanceof Container) result.addAll(all((Container) child)); }
        return result;
    }
    private static boolean shown(Component component, Container root) {
        for (Component current = component; current != root; current = current.getParent())
            if (current == null || !current.isVisible()) return false;
        return true;
    }
    @Test public void technicalFactsRemainAvailableButDoNotOccupyDefaultReader() throws Exception {
        ExecutorService workers = Executors.newSingleThreadExecutor();
        WikiClient wiki = new WikiClient(new OkHttpClient(), new Gson());
        WikiPage page = new WikiPage(DocumentTest.document("Rake"));
        try {
            SwingUtilities.invokeAndWait(() -> {
                ContentRenderer renderer = new ContentRenderer(url -> {}, workers, new ImageCache(wiki));
                JPanel body = renderer.renderArea(page, page.navigation.area("overview"), Map.of(), ""); renderer.revealAll();
                List<Component> children = all(body);
                JEditorPane moid = (JEditorPane) children.stream().filter(c -> c instanceof JEditorPane
                    && com.wikipane.html.WikiHtml.parse(((JEditorPane)c).getText()).text().equals("MOID")).findFirst().orElseThrow();
                assertFalse(shown(moid, body));
                JButton details = (JButton) children.stream().filter(c -> c instanceof JButton && ((JButton)c).getText().contains("Technical details")).findFirst().orElseThrow();
                details.doClick(); assertTrue(shown(moid, body));
                assertTrue(page.view.audit.summary(), page.view.audit.valid());
                renderer.clear();
            });
        } finally { wiki.close(); workers.shutdownNow(); }
    }
    @Test public void equivalentSingularFactsRetainEverySourceAndVariantsStayDistinct() throws Exception {
        com.wikipane.document.WikiDocument doc=com.wikipane.pin.PinSelectionTest.document(
            "<table class='infobox infobox-item'><tr><th>Daily volume</th><td>325</td></tr></table>"
            +"<table class='infobox infobox-item'><tr><th>Daily volume</th><td>325</td></tr></table>");
        java.util.List<com.wikipane.semantic.SemanticDocument.Fact> facts=new ArrayList<>();
        doc.semantic.nodes.forEach(node -> facts.addAll(node.facts));
        assertEquals(2,facts.size());assertEquals(FactPolicy.duplicateKey(facts.get(0)),FactPolicy.duplicateKey(facts.get(1)));
        com.wikipane.semantic.SemanticDocument.Fact first=facts.get(0);
        com.wikipane.semantic.SemanticDocument.Fact otherVariant=new com.wikipane.semantic.SemanticDocument.Fact(
            first.key,first.label,first.group,"Other",first.value,first.source);
        assertNotEquals(FactPolicy.duplicateKey(first),FactPolicy.duplicateKey(otherVariant));
        ExecutorService workers=Executors.newSingleThreadExecutor();WikiClient wiki=new WikiClient(new OkHttpClient(),new Gson());
        try {SwingUtilities.invokeAndWait(() -> {
            WikiPage page=new WikiPage(doc);ContentRenderer renderer=new ContentRenderer(url -> {},workers,new ImageCache(wiki));
            JPanel body=renderer.renderArea(page,page.navigation.area("overview"),Map.of(),"");renderer.revealAll();
            assertEquals(1,all(body).stream().filter(c -> c instanceof JEditorPane && com.wikipane.html.WikiHtml.parse(((JEditorPane)c).getText()).text().equals("Daily volume")).count());
            assertTrue(all(body).stream().filter(c -> c instanceof JComponent)
                .map(c -> ((JComponent)c).getClientProperty("wiki.fact.sources"))
                .anyMatch(sources -> sources instanceof List && ((List<?>)sources).size()==2));
            renderer.clear();
        });}finally{wiki.close();workers.shutdownNow();}
    }
    @Test public void identityAndEditorialImportanceUseFieldMeaning() {
        assertEquals(FactPolicy.Importance.TECHNICAL, FactPolicy.importance("Item ID", "5341"));
        assertEquals(FactPolicy.Importance.TECHNICAL, FactPolicy.importance("Link", "MOID"));
        assertEquals(FactPolicy.Importance.SUPPORTING, FactPolicy.importance("Released", "Today"));
        assertEquals(FactPolicy.Importance.PRIMARY, FactPolicy.importance("Growth time", "80 minutes"));
    }
}
