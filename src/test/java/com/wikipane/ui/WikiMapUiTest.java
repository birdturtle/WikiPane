package com.wikipane.ui;

import com.google.gson.Gson;
import com.wikipane.DocumentTest;
import com.wikipane.api.WikiClient;
import com.wikipane.cache.ImageCache;
import com.wikipane.semantic.SemanticDocument.Location;
import com.wikipane.view.WikiPage;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import javax.swing.*;
import okhttp3.OkHttpClient;
import org.junit.Test;
import static org.junit.Assert.*;

public class WikiMapUiTest {
    private static List<Component> all(Container root) {
        List<Component> result=new ArrayList<>();for(Component child:root.getComponents()) {
            result.add(child);if(child instanceof Container)result.addAll(all((Container)child));
        }return result;
    }
    @Test public void realQuestSourceProvidesMapActionWithoutNavigatingAwayFromReader() throws Exception {
        WikiPage page=new WikiPage(DocumentTest.document("Cooks_Assistant"));
        ExecutorService workers=Executors.newSingleThreadExecutor();WikiClient wiki=new WikiClient(new OkHttpClient(),new Gson());
        try {
            SwingUtilities.invokeAndWait(()->{
                List<com.wikipane.view.MapDestination> shown=new ArrayList<>();List<String> navigation=new ArrayList<>();
                ContentRenderer renderer=new ContentRenderer(navigation::add,workers,new ImageCache(wiki));
                renderer.mapRequests(shown::add);int count=0;
                for(com.wikipane.view.ArticleViewModel.Area area:page.view.areas) {
                    JPanel body=renderer.renderArea(page,area,Map.of(),"");renderer.revealAll();
                    for(Component component:all(body)) if("wiki.showOnMap".equals(component.getName())) {
                        JButton show=(JButton)component;Location location=(Location)show.getClientProperty("wiki.location");
                        assertTrue(location.verifiedMap);assertEquals("Cook's Assistant",location.source.article);
                        show.doClick();assertSame(location,shown.get(shown.size()-1).places.get(0).location);count++;
                        assertEquals("Show quest start",show.getText());
                        assertTrue(show.getToolTipText().contains("Cook"));
                        assertFalse(all((Container)show.getParent()).stream().anyMatch(c->c instanceof JButton
                            && ((JButton)c).getText().contains("Remove")));
                    }
                }
                assertTrue("Quest start map action missing",count>0);
                assertTrue(navigation.isEmpty());renderer.clear();
            });
        } finally {wiki.close();workers.shutdownNow();}
    }
    @Test public void aRecordWithSeveralPointsOffersOneNamedMapAction() throws Exception {
        WikiPage page=com.wikipane.view.MapDestinationTest.groupedPage();
        ExecutorService workers=Executors.newSingleThreadExecutor();WikiClient wiki=new WikiClient(new OkHttpClient(),new Gson());
        try {
            SwingUtilities.invokeAndWait(()->{
                List<com.wikipane.view.MapDestination> shown=new ArrayList<>();
                ContentRenderer renderer=new ContentRenderer(url->fail("Map lookup must not navigate"),workers,new ImageCache(wiki));
                renderer.mapRequests(shown::add);int count=0;
                for(com.wikipane.view.ArticleViewModel.Area area:page.view.areas) {
                    JPanel body=renderer.renderArea(page,area,Map.of(),"");renderer.revealAll();
                    for(Component component:all(body)) if("wiki.showOnMap".equals(component.getName())) {
                        JButton show=(JButton)component;show.doClick();count++;
                        assertEquals("Show spawn locations",show.getText());
                        assertTrue(show.getToolTipText().contains("West garden"));
                        assertFalse(show.getText().contains("3208"));
                    }
                }
                assertEquals(1,count);assertEquals(2,shown.get(0).places.size());renderer.clear();
            });
        } finally {wiki.close();workers.shutdownNow();}
    }

}
