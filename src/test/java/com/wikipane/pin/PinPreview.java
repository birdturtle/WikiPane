package com.wikipane.pin;

import com.wikipane.DocumentTest;
import com.wikipane.api.WikiClient;
import com.wikipane.cache.ImageCache;
import com.wikipane.ui.*;
import com.wikipane.view.WikiPage;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import javax.imageio.ImageIO;
import javax.swing.*;
import okhttp3.OkHttpClient;
import com.google.gson.Gson;

/** Actual fixture-backed reader selection painted as a pin, without running RuneScape. */
public final class PinPreview {
    private static List<JEditorPane> panes(Container root) {
        List<JEditorPane> panes = new ArrayList<>();
        for (Component component : root.getComponents()) {
            if (component instanceof JEditorPane) panes.add((JEditorPane)component);
            else if (component instanceof Container) panes.addAll(panes((Container)component));
        }
        return panes;
    }
    public static void main(String[] args) throws Exception {
        WikiPage page = new WikiPage(DocumentTest.document("Recipe_for_Disaster"));
        ExecutorService worker = Executors.newSingleThreadExecutor();
        WikiClient wiki = new WikiClient(new OkHttpClient(), new Gson());
        FutureTask<PinSelection> capture = new FutureTask<>(() -> {
            ContentRenderer renderer = new ContentRenderer(url -> {}, worker, new ImageCache(wiki), pin -> {}, message -> {});
            JPanel body = renderer.renderArea(page, page.navigation.area("overview"), Map.of(), "");
            renderer.revealAll();
            List<PinSelection.Fragment> fragments = new ArrayList<>();
            for (JEditorPane pane : panes(body)) {
                Object context = pane.getClientProperty("wiki.pin.context");
                String text = com.wikipane.vendor.jsoup.Jsoup.parseBodyFragment(pane.getText()).text();
                if (context instanceof PinContext && ((PinContext)context).condition != null
                    && (text.contains("Cooking") && text.contains("10") || text.contains("Cook's Assistant"))) {
                    fragments.add(SelectionCapture.capture(pane, 0, pane.getDocument().getLength()));
                    System.out.println(((PinContext)context).group + ": " + text);
                }
                if (fragments.size() == 2) break;
            }
            renderer.clear();
            return new PinSelection(fragments);
        });
        try {
            SwingUtilities.invokeAndWait(capture);
            PinLayout layout = new PinLayout(capture.get());
            for (int i = 0; i < layout.pages.size(); i++) {
                BufferedImage image = new BufferedImage(PinLayout.WIDTH, 500, BufferedImage.TYPE_INT_ARGB);
                Graphics2D graphics = image.createGraphics(); Dimension size = PinnedOverlay.paint(graphics, layout, i); graphics.dispose();
                String path = args[0].replace(".png", "-" + (i + 1) + ".png");
                ImageIO.write(image.getSubimage(0, 0, size.width, size.height), "png", new File(path));
                System.out.println(path + " " + size.width + "x" + size.height);
            }
        } finally { wiki.close(); worker.shutdownNow(); }
    }
}
