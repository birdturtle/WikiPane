package com.wikipane.ui;

import com.google.gson.Gson;
import com.wikipane.DocumentTest;
import com.wikipane.api.WikiClient;
import com.wikipane.cache.ImageCache;
import com.wikipane.pin.*;
import com.wikipane.view.WikiPage;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import javax.swing.*;
import okhttp3.OkHttpClient;
import org.junit.Test;
import static org.junit.Assert.*;

public class RecipeUiTest {
    private static List<Component> all(Container root) {
        List<Component> result=new ArrayList<>();for(Component child:root.getComponents()) {
            result.add(child);if(child instanceof Container)result.addAll(all((Container)child));
        }return result;
    }
    private static void layout(Container root){root.doLayout();for(Component c:root.getComponents())if(c instanceof Container)layout((Container)c);}
    private static String value(JEditorPane pane){try{return pane.getDocument().getText(0,pane.getDocument().getLength()).strip();}catch(Exception ex){throw new AssertionError(ex);}}
    @Test public void recipesRetainLinksPinProvenanceAndCompleteDetailsAtSidebarWidths() throws Exception {
        WikiPage page=new WikiPage(DocumentTest.document("Prayer_potion"));
        ExecutorService workers=Executors.newSingleThreadExecutor();WikiClient wiki=new WikiClient(new OkHttpClient(),new Gson());
        try {
            SwingUtilities.invokeAndWait(()->{
                for(int width:new int[]{185,225}) {
                    List<String> opened=new ArrayList<>();ContentRenderer renderer=new ContentRenderer(opened::add,workers,new ImageCache(wiki));
                    JPanel body=renderer.renderArea(page,page.view.sourceArea("Creation"),Map.of(),"");renderer.revealAll();
                    body.setSize(width,10000);for(int i=0;i<8;i++)layout(body);
                    Container recipe=(Container)all(body).stream().filter(c->"wiki.recipe".equals(c.getName())).findFirst().orElseThrow();
                    List<JEditorPane> panes=new ArrayList<>();for(Component child:all(recipe))if(child instanceof JEditorPane)panes.add((JEditorPane)child);
                    assertTrue(panes.stream().anyMatch(p->value(p).equals("Herblore 38 (boostable)")));
                    assertTrue(panes.stream().anyMatch(p->value(p).equals("87.5")));
                    JEditorPane grass=panes.stream().filter(p->value(p).equals("Snape grass")).findFirst().orElseThrow();
                    PinSelection selected=new PinSelection(List.of(SelectionCapture.capture(grass,0,grass.getDocument().getLength())));
                    assertTrue(selected.fragments.get(0).context.node.units.contains(selected.fragments.get(0).context.source.unit));
                    assertEquals("Snape grass",selected.fragments.get(0).context.record);
                    for(JEditorPane pane:panes) {
                        Rectangle bounds=SwingUtilities.convertRectangle(pane.getParent(),pane.getBounds(),body);
                        assertTrue("Recipe text exceeds sidebar",bounds.x>=0&&bounds.x+bounds.width<=width+1);
                        try {
                            Rectangle end=pane.modelToView(pane.getDocument().getLength()-1);
                            assertTrue("Recipe text clipped: "+value(pane),end.y+end.height<=pane.getHeight()+2);
                        }catch(javax.swing.text.BadLocationException ex){throw new AssertionError(ex);}
                    }
                    grass.getHyperlinkListeners()[0].hyperlinkUpdate(new javax.swing.event.HyperlinkEvent(grass,javax.swing.event.HyperlinkEvent.EventType.ACTIVATED,null,"https://oldschool.runescape.wiki/w/Snape_grass"));
                    assertEquals(List.of("https://oldschool.runescape.wiki/w/Snape_grass"),opened);
                    JButton details=(JButton)all(recipe).stream().filter(c->c instanceof JButton&&((JButton)c).getText().contains("source details")).findFirst().orElseThrow();
                    details.doClick();
                    assertTrue(all(recipe).stream().filter(c->c instanceof JEditorPane).map(c->value((JEditorPane)c)).anyMatch(s->s.contains("Profit after GE Tax")));
                    renderer.clear();
                }
            });
        } finally {wiki.close();workers.shutdownNow();}
    }
}
