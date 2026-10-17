package com.wikipane.ui;
import static org.junit.Assert.*;

import com.google.gson.Gson;
import com.wikipane.DocumentTest;
import com.wikipane.api.WikiClient;
import com.wikipane.cache.ImageCache;
import com.wikipane.document.WikiDocument;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import javax.swing.*;
import okhttp3.OkHttpClient;
import org.junit.Test;
public class PresentationUiTest {
    private List<Component> all(Container root) {
        List<Component> found = new ArrayList<>();
        for (Component c : root.getComponents()) {
            found.add(c);
            if (c instanceof Container)
                found.addAll(all((Container) c));
        }
        return found;
    }
    private String html(Container root) {
        return all(root)
            .stream()
            .filter(c -> c instanceof JEditorPane)
            .map(c -> ((JEditorPane) c).getText())
            .reduce("", String::concat);
    }
    @Test public void informationRetainsSecondaryFactsWithoutRawTableControls()throws Exception{
        WikiDocument doc=DocumentTest.document("Bronze_dagger");ExecutorService worker=Executors.newSingleThreadExecutor();WikiClient wiki=new WikiClient(new OkHttpClient(),new Gson());
        try{SwingUtilities.invokeAndWait(()->{ContentRenderer renderer=new ContentRenderer(url->{},worker,new ImageCache(wiki));JPanel body=renderer.renderArea(doc,com.wikipane.view.ArticleViewModel.create(doc.semantic).area("info"),Map.of(),"Unpoisoned");renderer.revealAll();String html=html(body);assertFalse(html.contains("spans "));assertFalse(html.contains("data-attr"));assertTrue(html.contains("Released"));assertTrue(html.contains("Members"));assertFalse(all(body).stream().anyMatch(c->c instanceof JButton&&((JButton)c).getText().equals("Show more details")));});}finally{wiki.close();worker.shutdownNow();}
    }
    @Test
    public void questRequirementsAppearBeforeSecondaryDescription() throws Exception {
        WikiDocument doc = DocumentTest.document("Recipe_for_Disaster");
        ExecutorService worker = Executors.newSingleThreadExecutor();
        WikiClient wiki = new WikiClient(new OkHttpClient(), new Gson());
        try {
            SwingUtilities.invokeAndWait(() -> {
                ContentRenderer renderer = new ContentRenderer(url -> {}, worker, new ImageCache(wiki));
                String html = html(renderer.render(doc, Map.of(), "Details"));
                assertTrue(html.contains("To start the quest"));
                assertTrue(html.contains("10"));
                assertTrue(html.contains("Cooking"));
                assertTrue(org.jsoup.Jsoup.parseBodyFragment(html).text().contains("Cook's Assistant"));
                assertFalse(html.contains("Show on map"));
                assertFalse(html.contains("surprisingly small number"));
            });
        } finally {
            wiki.close();
            worker.shutdownNow();
        }
    }
}
