package com.wikipane.ui;

import com.google.gson.Gson;
import com.wikipane.DocumentTest;
import com.wikipane.api.WikiClient;
import com.wikipane.cache.ImageCache;
import com.wikipane.view.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;
import java.util.concurrent.*;
import javax.imageio.ImageIO;
import javax.swing.*;
import okhttp3.OkHttpClient;

/** Only isolated Swing components and offline article fixtures. Never starts or captures RuneScape. */
public final class UnifiedSectionPreview {
    private static void layout(Container root){root.doLayout();for(Component child:root.getComponents())if(child.isVisible()&&child instanceof Container)layout((Container)child);}
    public static void main(String[] args)throws Exception {
        System.setProperty("java.awt.headless","true");
        WikiPage page=new WikiPage(DocumentTest.document(args.length>0?args[0]:"Vorkath"));
        String anchor=args.length>1?args[1]:"Weapons_and_armour";
        ExecutorService workers=Executors.newSingleThreadExecutor();WikiClient wiki=new WikiClient(new OkHttpClient(),new Gson());
        try{SwingUtilities.invokeAndWait(() -> {
            com.formdev.flatlaf.FlatDarkLaf.setup();
            ArticleReader reader=new ArticleReader(url -> {},workers,new ImageCache(wiki),pin -> {},error -> {});
            JPanel body=reader.render(page,Map.of(),"Post-quest");
            String area=page.navigation.areaForAnchor(anchor);reader.revealDestination(area,anchor);
            JScrollPane scroll=new JScrollPane(body);scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);scroll.getViewport().setBackground(ReaderComponents.BACKGROUND);
            JPanel root=new JPanel(new GridLayout(1,2,10,0));root.setBackground(ReaderComponents.BACKGROUND);
            root.add(new SectionNavigator(page.navigation.roots,reader.expanded(),area,anchor,destination -> {}));root.add(scroll);
            root.setSize(470,780);for(int i=0;i<7;i++)layout(root);
            JComponent target=reader.destination(area,anchor);
            if(target!=null){Point point=SwingUtilities.convertPoint(target,0,0,body);scroll.getViewport().setViewPosition(new Point(0,point.y));}
            BufferedImage image=new BufferedImage(470,780,BufferedImage.TYPE_INT_RGB);Graphics2D graphics=image.createGraphics();root.paint(graphics);graphics.dispose();
            try{ImageIO.write(image,"png",new File(args.length>2?args[2]:"/tmp/wikipane-unified-sections.png"));}catch(Exception ex){throw new RuntimeException(ex);}
            reader.clear();
        });}finally{wiki.close();workers.shutdownNow();}
    }
}
