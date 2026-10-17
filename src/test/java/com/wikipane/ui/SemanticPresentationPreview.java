package com.wikipane.ui;

import com.google.gson.Gson;
import com.wikipane.DocumentTest;
import com.wikipane.api.WikiClient;
import com.wikipane.cache.ImageCache;
import com.wikipane.view.WikiPage;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Map;
import java.util.concurrent.*;
import javax.imageio.ImageIO;
import javax.swing.*;
import okhttp3.OkHttpClient;

/** Offline Swing-only capture of a selected source section. No client or game input. */
public final class SemanticPresentationPreview {
    private static void layout(Container root) {
        root.doLayout(); for (Component child : root.getComponents()) if (child instanceof Container) layout((Container)child);
    }
    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");
        WikiPage page = new WikiPage(DocumentTest.document(args[0]));
        ExecutorService workers = Executors.newSingleThreadExecutor();
        WikiClient wiki = new WikiClient(new OkHttpClient(), new Gson());
        try {
            SwingUtilities.invokeAndWait(() -> {
                com.formdev.flatlaf.FlatDarkLaf.setup();
                ContentRenderer renderer = new ContentRenderer(url -> {}, workers, new ImageCache(wiki));
                JPanel body = renderer.renderArea(page, page.view.sourceArea(args[1]), Map.of(), args.length > 3 ? args[3] : "");
                renderer.revealAll();
                JScrollPane scroll = new JScrollPane(body); scroll.setBorder(BorderFactory.createEmptyBorder());
                scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
                JPanel root = new JPanel(new BorderLayout()); root.setBackground(ReaderComponents.BACKGROUND);
                root.setBorder(BorderFactory.createEmptyBorder(8,8,8,8));
                JLabel title = new JLabel(page.document.title); title.setForeground(SemanticPalette.TEXT);
                title.setBorder(BorderFactory.createEmptyBorder(0,0,8,0)); root.add(title, BorderLayout.NORTH); root.add(scroll);
                root.setSize(242,780);
                for (int i=0;i<6;i++) layout(root);
                JComponent target = renderer.anchor(args[1]);
                if (target != null) {
                    Point point = SwingUtilities.convertPoint(target, 0, 0, body);
                    scroll.getViewport().setViewPosition(new Point(0, Math.min(point.y, Math.max(0, body.getHeight()-scroll.getViewport().getHeight()))));
                }
                SidebarLayoutValidator.inspect(body, page.view.audit);
                BufferedImage image = new BufferedImage(242,780,BufferedImage.TYPE_INT_RGB);
                Graphics2D graphics = image.createGraphics(); root.paint(graphics); graphics.dispose();
                try { ImageIO.write(image,"png",new File(args[2])); } catch (Exception ex) { throw new IllegalStateException(ex); }
                System.out.println(page.view.audit.summary()); renderer.clear();
            });
        } finally { wiki.close(); workers.shutdownNow(); }
    }
}
