package com.wikipane.ui;

import com.google.gson.Gson;
import com.wikipane.DocumentTest;
import com.wikipane.api.WikiClient;
import com.wikipane.cache.ImageCache;
import com.wikipane.context.*;
import com.wikipane.view.WikiPage;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import javax.swing.*;
import okhttp3.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class SkillProgressUiTest {
    private static PlayerContextSnapshot player(int base, int boosted) {
        return new PlayerContextSnapshot(true,true,Map.of("Farming",new PlayerContextSnapshot.Level(base,boosted)),Map.of(),null);
    }
    private static <T> T edt(Callable<T> call) throws Exception {
        FutureTask<T> task=new FutureTask<>(call); SwingUtilities.invokeAndWait(task); return task.get();
    }
    private static List<Component> all(Container root) {
        List<Component> result=new ArrayList<>(); for(Component c:root.getComponents()) {result.add(c); if(c instanceof Container)result.addAll(all((Container)c));} return result;
    }
    private static JComponent row(Container root,int level) {
        return (JComponent)all(root).stream().filter(c->c instanceof JComponent&&Integer.valueOf(level).equals(((JComponent)c).getClientProperty("wiki.skillLevel"))).findFirst().orElseThrow();
    }
    @Test public void baseLevelDrivesLazyRevealAndDimmingWithoutChangingRowsOrDisablingLinks() throws Exception {
        WikiPage page=new WikiPage(DocumentTest.document("Farming__Level_up_table"));
        ExecutorService workers=Executors.newSingleThreadExecutor(); WikiClient wiki=new WikiClient(new OkHttpClient(),new Gson());
        try {
            edt(()->{
                ContentRenderer renderer=new ContentRenderer(url->{},workers,new ImageCache(wiki));
                renderer.playerContext(player(80,85));
                JPanel body=renderer.renderArea(page,page.view.areas.get(0),Map.of(),"");
                JComponent target=renderer.currentLevelTarget(); assertNotNull(target);
                assertEquals(80,target.getClientProperty("wiki.skillLevel"));
                assertEquals(Boolean.TRUE,row(body,1).getClientProperty("wiki.reachedLevel"));
                assertEquals(Boolean.FALSE,target.getClientProperty("wiki.reachedLevel"));
                assertTrue(row(body,1).isEnabled());
                renderer.playerContext(player(81,90));
                assertSame(target,row(body,80)); assertEquals(Boolean.TRUE,target.getClientProperty("wiki.reachedLevel"));
                renderer.playerContext(PlayerContextSnapshot.unavailable(true));
                assertEquals(Boolean.FALSE,target.getClientProperty("wiki.reachedLevel")); assertNull(renderer.currentLevelTarget());
                renderer.clear(); assertNull(renderer.currentLevelTarget()); return null;
            });
        } finally {wiki.close();workers.shutdownNow();}
    }
    @Test public void onlyKnownSkillLevelTablesQualifyAndMissingLevelsChooseNextRow() {
        assertEquals("Farming",SkillGuideProgress.skill("Farming/Level up table"));
        assertEquals("",SkillGuideProgress.skill("Farming")); assertEquals("",SkillGuideProgress.skill("Unknown/Level up table"));
        assertEquals(Integer.valueOf(20),SkillGuideProgress.target(List.of(1,10,20,30),15));
        assertEquals(Integer.valueOf(30),SkillGuideProgress.target(List.of(1,10,20,30),99));
        assertNull(SkillGuideProgress.target(List.of(),10));
        assertNull(SkillGuideProgress.base("Farming",PlayerContextSnapshot.unavailable(true)));
        assertNull(SkillGuideProgress.base("Farming",new PlayerContextSnapshot(false,true,player(80,90).levels,Map.of(),null)));
    }
    @Test public void everyRowAtTheSameLevelUpdatesWhileNavigationUsesTheFirstRow() throws Exception {
        com.google.gson.JsonObject response = new com.google.gson.JsonObject(), parse = new com.google.gson.JsonObject();
        parse.addProperty("title", "Magic/Level up table");
        parse.addProperty("text", "<table><tr><th>Level</th><th>Members</th><th>Unlock</th></tr>"
            + "<tr><th>51</th><td>Free-to-play</td><td>First unlock</td></tr>"
            + "<tr><th>51</th><td>Members</td><td>Second unlock</td></tr>"
            + "<tr><th>52</th><td>Members</td><td>Later unlock</td></tr></table>");
        response.add("parse", parse);
        WikiPage page = new WikiPage(new com.wikipane.document.WikiDocumentParser().parse(response));
        ExecutorService workers = Executors.newSingleThreadExecutor(); WikiClient wiki = new WikiClient(new OkHttpClient(),new Gson());
        try {
            edt(() -> {
                ContentRenderer renderer = new ContentRenderer(url -> {}, workers, new ImageCache(wiki));
                renderer.playerContext(new PlayerContextSnapshot(true,true,Map.of("Magic",new PlayerContextSnapshot.Level(51,60)),Map.of(),null));
                JPanel body = renderer.renderArea(page,page.view.areas.get(0),Map.of(),"");
                List<JComponent> duplicates = new ArrayList<>();
                for (Component c : all(body)) if (c instanceof JComponent
                    && Integer.valueOf(51).equals(((JComponent)c).getClientProperty("wiki.skillLevel"))) duplicates.add((JComponent)c);
                assertEquals(2,duplicates.size()); assertSame(duplicates.get(0),renderer.currentLevelTarget());
                assertTrue(duplicates.stream().allMatch(r -> Boolean.FALSE.equals(r.getClientProperty("wiki.reachedLevel"))));
                renderer.playerContext(new PlayerContextSnapshot(true,true,Map.of("Magic",new PlayerContextSnapshot.Level(52,60)),Map.of(),null));
                assertTrue(duplicates.stream().allMatch(r -> Boolean.TRUE.equals(r.getClientProperty("wiki.reachedLevel"))));
                assertEquals(Boolean.FALSE,row(body,52).getClientProperty("wiki.reachedLevel"));
                renderer.playerContext(PlayerContextSnapshot.unavailable(true));
                assertTrue(duplicates.stream().allMatch(r -> Boolean.FALSE.equals(r.getClientProperty("wiki.reachedLevel"))));
                renderer.clear(); return null;
            });
        } finally {wiki.close(); workers.shutdownNow();}
    }
    private static void layout(Container root) {root.doLayout();for(Component c:root.getComponents())if(c instanceof Container)layout((Container)c);}
    private static void await(Callable<Boolean> condition) throws Exception {
        ScheduledExecutorService timer=Executors.newSingleThreadScheduledExecutor(); CompletableFuture<Void> done=new CompletableFuture<>();
        timer.scheduleAtFixedRate(()->{try{if(edt(condition))done.complete(null);}catch(Exception ex){done.completeExceptionally(ex);}},0,20,TimeUnit.MILLISECONDS);
        try{done.get(10,TimeUnit.SECONDS);}finally{timer.shutdownNow();}
    }
    @Test public void firstOpenPositionsAtCurrentLevelAndBackKeepsManualReadingPosition() throws Exception {
        ExecutorService workers=Executors.newFixedThreadPool(2);
        WikiClient wiki=new WikiClient(new OkHttpClient.Builder().addInterceptor(chain-> {
            String title=chain.request().url().queryParameter("page");
            return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("fixture")
                .body(ResponseBody.create(MediaType.get("application/json"),DocumentTest.fixture(title.equals("Farming/Level up table")?"Farming__Level_up_table":"Cooks_Assistant").toString())).build();
        }).build(),new Gson());
        WikiPanePanel panel=edt(()->{WikiPanePanel p=new WikiPanePanel(wiki,workers);p.setSize(225,780);layout(p);p.onActivate();p.playerContext(player(80,90));p.openTitle("Farming/Level up table","");return p;});
        try {
            await(()->{layout(panel);return all(panel).stream().anyMatch(c->c instanceof JComponent&&Integer.valueOf(80).equals(((JComponent)c).getClientProperty("wiki.skillLevel")))
                && all(panel).stream().anyMatch(c->c instanceof JScrollPane&&((JScrollPane)c).getVerticalScrollBar().getValue()>0);});
            int saved=edt(()->{
                JScrollPane scroll=(JScrollPane)all(panel).stream().filter(c->c instanceof JScrollPane&&SwingUtilities.isDescendingFrom(row(panel,80),(JScrollPane)c)).findFirst().orElseThrow();
                scroll.getVerticalScrollBar().setValue(150);int value=scroll.getVerticalScrollBar().getValue();panel.openTitle("Cook's Assistant","");return value;
            });
            try {
                await(()->all(panel).stream().anyMatch(c->c instanceof JEditorPane&&"wiki.articleTitle".equals(c.getName())&&"Cook's Assistant".equals(com.wikipane.html.WikiHtml.parse(((JEditorPane)c).getText()).text())));
            } catch (TimeoutException ex) {
                fail(edt(()->all(panel).stream().filter(c->c instanceof JLabel).map(c->((JLabel)c).getText())
                    .collect(java.util.stream.Collectors.toList()).toString()));
            }
            edt(()->{((JButton)all(panel).stream().filter(c->c instanceof JButton&&"Back".equals(((JButton)c).getToolTipText())).findFirst().orElseThrow()).doClick();return null;});
            await(()->{layout(panel);return all(panel).stream().anyMatch(c->c instanceof JComponent&&Integer.valueOf(80).equals(((JComponent)c).getClientProperty("wiki.skillLevel")));});
            edt(()->{panel.playerContext(player(85,90));return null;}); edt(()->null);
            edt(()->{
                JScrollPane scroll=(JScrollPane)all(panel).stream().filter(c->c instanceof JScrollPane&&SwingUtilities.isDescendingFrom(row(panel,80),(JScrollPane)c)).findFirst().orElseThrow();
                assertEquals(saved,scroll.getVerticalScrollBar().getValue());return null;
            });
        } finally {edt(()->{panel.stop();return null;});wiki.close();workers.shutdownNow();}
    }
}
