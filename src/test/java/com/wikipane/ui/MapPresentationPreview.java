package com.wikipane.ui;

import com.google.gson.Gson;
import com.wikipane.DocumentTest;
import com.wikipane.api.WikiClient;
import com.wikipane.cache.ImageCache;
import com.wikipane.view.WikiPage;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.concurrent.*;
import javax.imageio.ImageIO;
import javax.swing.*;
import okhttp3.OkHttpClient;

/** Fixture-only Swing preview. Optional live tile fetching never touches the game client. */
public final class MapPresentationPreview {
    private static void layout(Container root) {root.doLayout();for(Component c:root.getComponents())if(c instanceof Container)layout((Container)c);}
    public static void main(String[] args)throws Exception {
        System.setProperty("java.awt.headless","true");
        WikiPage page=new WikiPage(DocumentTest.document(args.length>1?args[1]:"Cooks_Assistant_maps"));
        int frameIndex=args.length>2?Integer.parseInt(args[2]):0;
        com.wikipane.semantic.WikiMaps.Frame selected=page.document.maps.frames.get(frameIndex);
        OkHttpClient transport=new OkHttpClient();WikiClient wiki=new WikiClient(transport,new Gson());ExecutorService workers=Executors.newFixedThreadPool(2);
        try {
            ImageCache images=new ImageCache(wiki);
            BufferedImage detail=images.maps.load(selected,page.document.maps,true);
            images.maps.load(selected,page.document.maps,false);
            ImageIO.write(detail,"png",new File("/tmp/wikipane-map-detail.png"));
            SwingUtilities.invokeAndWait(()->{
                com.formdev.flatlaf.FlatDarkLaf.setup();
                MapChooser chooser=new MapChooser(page,workers,images,new ImageViewer(workers,images),ignored->{},()->{});
                for(String title:path(page.maps.groups,selected.id)) {
                    for(Component c:all(chooser))if(c instanceof JButton&&"wiki.disclosure".equals(c.getName())
                        &&((JButton)c).getAccessibleContext().getAccessibleName().equals(title+", collapsed")){((JButton)c).doClick();break;}
                }
                chooser.setSize(225,720);for(int pass=0;pass<8;pass++)layout(chooser);
                BufferedImage image=new BufferedImage(225,720,BufferedImage.TYPE_INT_RGB);Graphics2D graphics=image.createGraphics();chooser.paint(graphics);graphics.dispose();
                try{ImageIO.write(image,"png",new File(args.length>0?args[0]:"/tmp/wikipane-map-chooser.png"));}catch(Exception e){throw new RuntimeException(e);}
                chooser.stop();
            });
        }finally{wiki.close();transport.dispatcher().executorService().shutdown();transport.connectionPool().evictAll();workers.shutdownNow();}
    }
    private static java.util.List<String> path(java.util.List<com.wikipane.view.ArticleMaps.Group> groups,String id) {
        for(com.wikipane.view.ArticleMaps.Group group:groups) {
            if(group.frame!=null&&group.frame.id.equals(id))return java.util.List.of(group.title);
            java.util.List<String> child=path(group.children,id);
            if(!child.isEmpty()){java.util.List<String> result=new java.util.ArrayList<>();result.add(group.title);result.addAll(child);return result;}
        }
        return java.util.List.of();
    }
    private static java.util.List<Component> all(Container root) {
        java.util.List<Component> result=new java.util.ArrayList<>();for(Component c:root.getComponents()){result.add(c);if(c instanceof Container)result.addAll(all((Container)c));}return result;
    }
}
