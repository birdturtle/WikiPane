package com.wikipane.ui;

import com.google.gson.Gson;
import com.wikipane.DocumentTest;
import com.wikipane.api.WikiClient;
import com.wikipane.cache.ImageCache;
import com.wikipane.context.*;
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

public class PlayerContextUiTest {
    private static <T>T edt(Callable<T> call)throws Exception {FutureTask<T> task=new FutureTask<>(call);SwingUtilities.invokeAndWait(task);return task.get();}
    private static List<Component> all(Container root){List<Component> result=new ArrayList<>();for(Component child:root.getComponents()){result.add(child);if(child instanceof Container)result.addAll(all((Container)child));}return result;}
    private static void layout(Container root){root.doLayout();for(Component child:root.getComponents())if(child instanceof Container)layout((Container)child);}
    private PlayerContextSnapshot snapshot(int level){return new PlayerContextSnapshot(true,true,Map.of("Cooking",new PlayerContextSnapshot.Level(level,level)),Map.of("Cook's Assistant",PlayerContextSnapshot.QuestState.COMPLETE),100);}
    private void drain(ExecutorService worker)throws Exception{worker.submit(()->{}).get(10,TimeUnit.SECONDS);edt(()->null);}
    private static final class Paused extends AbstractExecutorService {
        final List<Runnable> tasks=new ArrayList<>(); boolean stopped;
        public void execute(Runnable task){tasks.add(task);}public void shutdown(){stopped=true;}public List<Runnable> shutdownNow(){stopped=true;return List.of();}public boolean isShutdown(){return stopped;}public boolean isTerminated(){return stopped;}public boolean awaitTermination(long t,TimeUnit u){return stopped;}
    }
    @Test public void updatesAnnotationsWithoutReplacingTextChangingScrollOrPollutingSelection()throws Exception{
        WikiPage page=new WikiPage(DocumentTest.document("Recipe_for_Disaster"));ExecutorService worker=Executors.newSingleThreadExecutor();WikiClient wiki=new WikiClient(new OkHttpClient(),new Gson());
        try{
            ContentRenderer renderer=edt(()->new ContentRenderer(url->{},worker,new ImageCache(wiki),pin->{},error->fail(error)));
            JPanel body=edt(()->{renderer.playerContext(snapshot(42));return renderer.renderArea(page,page.view.area("requirements"),Map.of(),"");});drain(worker);
            List<JEditorPane> text=edt(()->{List<JEditorPane> panes=new ArrayList<>();for(Component c:all(body))if(c instanceof JEditorPane)panes.add((JEditorPane)c);return panes;});
            JEditorPane cooking=text.stream().filter(p->org.jsoup.Jsoup.parseBodyFragment(p.getText()).text().contains("10 Cooking")).findFirst().orElseThrow();
            JScrollPane scroll=edt(()->{JScrollPane pane=new JScrollPane(body);pane.setSize(225,450);for(int i=0;i<5;i++)layout(pane);pane.getVerticalScrollBar().setValue(100);return pane;});
            int before=edt(()->scroll.getVerticalScrollBar().getValue());
            edt(()->{assertTrue(all(body).stream().filter(c->c instanceof JLabel).anyMatch(c->((JLabel)c).getText()!=null&&((JLabel)c).getText().contains("Level 42")));renderer.playerContext(snapshot(8));return null;});drain(worker);
            edt(()->{
                assertEquals(before,scroll.getVerticalScrollBar().getValue());assertTrue(all(body).containsAll(text));
                JLabel status=(JLabel)all(body).stream().filter(c->c instanceof JLabel&&((JLabel)c).getText()!=null&&((JLabel)c).getText().contains("Level 8")).findFirst().orElseThrow();
                assertEquals(RequirementEvaluator.Status.UNMET,((RequirementEvaluator.Result)status.getClientProperty("wiki.requirement.result")).status);
                PinSelection.Fragment capture=SelectionCapture.capture(cooking,0,cooking.getDocument().getLength());
                assertTrue(capture.text().contains("Cooking"));assertFalse(capture.text().contains("Level 8"));assertFalse(capture.text().contains("Current"));
                renderer.playerContext(PlayerContextSnapshot.unavailable(true));
                assertTrue(all(body).stream().filter(c->c instanceof JLabel&&"wiki.requirementStatus".equals(c.getName())).allMatch(c->((JLabel)c).getText().contains("unavailable")));
                renderer.clear();return null;
            });
        }finally{wiki.close();worker.shutdownNow();}
    }
    @Test public void annotationsFitNarrowWidthsAndDisableWithoutChangingWikiText()throws Exception{
        WikiPage page=new WikiPage(DocumentTest.document("Recipe_for_Disaster"));ExecutorService worker=Executors.newSingleThreadExecutor();WikiClient wiki=new WikiClient(new OkHttpClient(),new Gson());
        try{
            ContentRenderer renderer=edt(()->new ContentRenderer(url->{},worker,new ImageCache(wiki)));
            JPanel body=edt(()->{renderer.playerContext(snapshot(42));return renderer.renderArea(page,page.view.area("requirements"),Map.of(),"");});drain(worker);
            edt(()->{for(int width:new int[]{185,225}){for(int i=0;i<5;i++){body.setSize(width,body.getPreferredSize().height);layout(body);}for(Component c:all(body)) {
                    if("wiki.requirementStatus".equals(c.getName())){assertTrue(c.getX()+c.getWidth()<=c.getParent().getWidth()+1);assertTrue(c.getHeight()>=17);}
                    if(c instanceof JEditorPane) {
                        JEditorPane pane=(JEditorPane)c;String raw=pane.getDocument().getText(0,pane.getDocument().getLength());int last=raw.length()-1;
                        while(last>=0&&Character.isWhitespace(raw.charAt(last)))last--;
                        if(last>=0)assertTrue("Requirement text clipped at width "+width+": "+raw,
                            pane.modelToView2D(last).getMaxY()<=pane.getHeight()+2);
                    }
                }}
                renderer.playerContext(PlayerContextSnapshot.unavailable(false));assertTrue(all(body).stream().filter(c->"wiki.requirementStatus".equals(c.getName())).noneMatch(Component::isVisible));renderer.clear();return null;});
        }finally{wiki.close();worker.shutdownNow();}
    }
    @Test public void olderWorkerResultsCannotRestoreAnotherPlayersStateOrDisposedRows()throws Exception{
        WikiPage page=new WikiPage(DocumentTest.document("Recipe_for_Disaster"));Paused worker=new Paused();WikiClient wiki=new WikiClient(new OkHttpClient(),new Gson());
        try{
            ContentRenderer renderer=edt(()->new ContentRenderer(url->{},worker,new ImageCache(wiki)));
            JPanel body=edt(()->{renderer.playerContext(snapshot(42));return renderer.renderArea(page,page.view.area("requirements"),Map.of(),"");});
            edt(()->{worker.tasks.get(0).run();renderer.playerContext(PlayerContextSnapshot.unavailable(true));return null;});
            for(Runnable task:List.copyOf(worker.tasks))task.run();edt(()->{assertTrue(all(body).stream().filter(c->"wiki.requirementStatus".equals(c.getName())).allMatch(c->((JLabel)c).getText().contains("unavailable")));renderer.clear();return null;});
        }finally{wiki.close();worker.shutdownNow();}
    }
}
