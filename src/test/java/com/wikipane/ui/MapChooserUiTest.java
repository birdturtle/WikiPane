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
import org.junit.Test;
import okhttp3.OkHttpClient;
import static org.junit.Assert.*;

public class MapChooserUiTest {
    private static List<Component> all(Container root) {
        List<Component> result=new ArrayList<>();for(Component child:root.getComponents()) {result.add(child);if(child instanceof Container)result.addAll(all((Container)child));}return result;
    }
    private static void layout(Container root) {root.doLayout();for(Component child:root.getComponents())if(child instanceof Container)layout((Container)child);}
    @Test public void chooserShowsSourceGroupsAndSelectingIngredientsUsesOneMapRequest() throws Exception {
        WikiPage page=new WikiPage(DocumentTest.document("Cooks_Assistant_maps"));ExecutorService workers=Executors.newSingleThreadExecutor();
        WikiClient wiki=new WikiClient(new OkHttpClient(),new Gson());
        try {SwingUtilities.invokeAndWait(()->{
            List<MapDestination> shown=new ArrayList<>();boolean[] dismissed={false};ImageCache images=new ImageCache(wiki);
            MapChooser chooser=new MapChooser(page,workers,images,new ImageViewer(workers,images),shown::add,()->dismissed[0]=true);
            chooser.setSize(209,430);for(int pass=0;pass<5;pass++)layout(chooser);
            JButton ingredients=(JButton)all(chooser).stream().filter(c->c instanceof JButton&&((JButton)c).getText().contains("Ingredient locations")).findFirst().orElseThrow();
            ingredients.doClick();
            JButton chicken=(JButton)all(chooser).stream().filter(c->c instanceof JButton&&((JButton)c).getText().contains("Chicken coop")).findFirst().orElseThrow();
            assertEquals(ReaderComponents.ACCENT,chicken.getForeground());chicken.doClick();
            assertTrue(dismissed[0]);assertEquals(1,shown.size());assertEquals("Chicken coop",shown.get(0).places.get(0).label);
            assertEquals("3181",shown.get(0).places.get(0).location.x);
            for(Component child:all(chooser))if((child instanceof JButton||child instanceof JEditorPane)&&child.getWidth()>0)
                assertTrue(child.getBounds().toString(),child.getX()+child.getWidth()<=child.getParent().getWidth()+1);
            assertTrue(MapPreview.legend(page.document.maps.frames.get(0),page.document.maps).contains("1. Chicken coop"));
            chooser.stop();
        });}finally{workers.shutdownNow();wiki.close();}
    }
    @Test public void mapPreviewAppearsInItsSourceSectionAndIsRemovedOnClear() throws Exception {
        WikiPage page=new WikiPage(DocumentTest.document("Cooks_Assistant_maps"));ExecutorService workers=Executors.newSingleThreadExecutor();
        WikiClient wiki=new WikiClient(new OkHttpClient(),new Gson());
        try {SwingUtilities.invokeAndWait(()->{
            ContentRenderer renderer=new ContentRenderer(url->{},workers,new ImageCache(wiki));int count=0;
            for(ArticleViewModel.Area area:page.view.areas) {
                JPanel body=renderer.renderArea(page,area,Map.of(),"");renderer.revealAll();
                count+=all(body).stream().filter(c->"wiki.mapPreview".equals(c.getName())).count();
            }
            assertEquals(1,count);renderer.clear();
        });}finally{workers.shutdownNow();wiki.close();}
    }
    @Test public void farmingMapsMaterializeOnlyAfterOpeningASpecificLocation() throws Exception {
        WikiPage page=new WikiPage(DocumentTest.document("Farming_patches_maps"));ExecutorService workers=Executors.newSingleThreadExecutor();
        WikiClient wiki=new WikiClient(new OkHttpClient(),new Gson());
        try {SwingUtilities.invokeAndWait(()->{
            ImageCache images=new ImageCache(wiki);MapChooser chooser=new MapChooser(page,workers,images,new ImageViewer(workers,images),ignored->{},()->{});
            assertEquals(0,all(chooser).stream().filter(c->"wiki.mapPreview".equals(c.getName())).count());
            JButton first=(JButton)all(chooser).stream().filter(c->c instanceof JButton&&((JButton)c).getText().contains("South of Falador")).findFirst().orElseThrow();
            first.doClick();assertEquals(1,all(chooser).stream().filter(c->"wiki.mapPreview".equals(c.getName())).count());
            chooser.setSize(185,430);for(int pass=0;pass<6;pass++)layout(chooser);
            for(Component child:all(chooser))if((child instanceof JButton||child instanceof JEditorPane)&&child.getWidth()>0)
                assertTrue(child.getBounds().toString(),child.getX()+child.getWidth()<=child.getParent().getWidth()+1);
            chooser.stop();
        });}finally{workers.shutdownNow();wiki.close();}
    }

    @Test public void regionAndRouteLegendsRetainDistinctVisualReferences() throws Exception {
        com.wikipane.semantic.WikiMaps maps=DocumentTest.document("Tree_Gnome_Village_maps").maps;
        String routes=MapPreview.legend(maps.frames.get(0),maps);
        assertTrue(routes.contains("R1"));assertTrue(routes.contains("R2"));
        String regions=MapPreview.legend(maps.frames.get(1),maps);
        assertTrue(regions.contains("A1"));assertTrue(regions.contains("Tracker gnome 1"));
    }

}
