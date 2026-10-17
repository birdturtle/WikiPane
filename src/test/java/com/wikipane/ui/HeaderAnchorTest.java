package com.wikipane.ui;

import com.google.gson.Gson;
import com.wikipane.DocumentTest;
import com.wikipane.api.WikiClient;
import com.wikipane.cache.ImageCache;
import com.wikipane.view.WikiPage;
import java.util.*;
import java.util.concurrent.*;
import javax.swing.*;
import okhttp3.OkHttpClient;
import org.junit.Test;
import static org.junit.Assert.*;

public class HeaderAnchorTest {
    @Test public void skillItemMonsterAndQuestDestinationsUseTheirActualSourceHeaders() throws Exception {
        ExecutorService workers=Executors.newSingleThreadExecutor();
        WikiClient wiki=new WikiClient(new OkHttpClient(),new Gson());
        try {
            for(String[] example:new String[][]{{"Cooking","Wine"},{"Cooking","Pies"},
                {"Bronze_dagger","Creation"},{"Vorkath","Tertiary"},{"Recipe_for_Disaster","Rewards"}}) {
                WikiPage page=new WikiPage(DocumentTest.document(example[0]));
                SwingUtilities.invokeAndWait(()->{
                    ContentRenderer renderer=new ContentRenderer(url->{},workers,new ImageCache(wiki));
                    renderer.renderArea(page,page.view.sourceArea(example[1]),Map.of(),"");
                    renderer.revealAnchor(example[1]);
                    JComponent target=renderer.anchor(example[1]);
                    assertNotNull(example[0]+"#"+example[1],target);
                    assertEquals("wiki.sectionHeading",target.getName());
                    assertEquals(page.document.url+"#"+example[1],target.getClientProperty("wiki.heading.source"));
                    assertNotNull(target.getComponentPopupMenu());
                    renderer.clear();
                });
            }
        } finally { wiki.close(); workers.shutdownNow(); }
    }
}
