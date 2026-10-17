package com.wikipane;

import com.google.gson.Gson;
import com.wikipane.api.WikiClient;
import com.wikipane.ui.WikiPanePanel;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import javax.imageio.ImageIO;
import javax.swing.*;
import net.runelite.client.ui.ColorScheme;
import okhttp3.*;

/** Isolated reader preview. No game client, login, saved settings, or network. */
public class WikiPanePreview {
    private static void expandTable(Container root,String title){for(Component c:root.getComponents()){if(c instanceof JButton&&((JButton)c).getToolTipText()!=null&&((JButton)c).getToolTipText().startsWith(title))((JButton)c).doClick();else if(c instanceof Container)expandTable((Container)c,title);}}
    public static void main(String[] args) throws Exception {
        System.setProperty("flatlaf.uiScale","1.0");
        ExecutorService workers = Executors.newFixedThreadPool(3);
        WikiClient wiki = new WikiClient(
            new OkHttpClient.Builder()
                .addInterceptor(chain -> {
                    if(chain.request().url().encodedPath().startsWith("/images/")){
                        try(InputStream source=WikiPanePreview.class.getResourceAsStream("/wiki/images/sources.json")){
                            com.google.gson.JsonObject mapping=new com.google.gson.JsonParser().parse(new InputStreamReader(source,StandardCharsets.UTF_8)).getAsJsonObject();String url=chain.request().url().toString();
                            if(!mapping.has(url))throw new IOException("No offline image fixture.");
                            try(InputStream image=WikiPanePreview.class.getResourceAsStream("/wiki/images/"+mapping.get(url).getAsString())){return new Response.Builder().request(chain.request()).code(200).message("Preview image").protocol(Protocol.HTTP_1_1).body(ResponseBody.create(MediaType.get("image/png"),image.readAllBytes())).build();}
                        }
                    }
                    String title = chain.request().url().queryParameter("page");
                    String body;
                    if (title == null)
                        body = "[\"\",[\"Bronze dagger\",\"Recipe for Disaster\",\"Vorkath\"],[],[]]";
                    else
                        try (InputStream stream = WikiPanePreview.class.getResourceAsStream(
                                 "/wiki/" + title.replace("/","__").replace(' ', '_') + ".json")) {
                            if (stream == null)
                                throw new IOException("No preview fixture for this article.");
                            body = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                        }
                    return new Response.Builder()
                        .request(chain.request())
                        .code(200)
                        .message("Preview")
                        .protocol(Protocol.HTTP_1_1)
                        .body(ResponseBody.create(MediaType.get("application/json"), body))
                        .build();
                })
                .build(),
            new Gson());
        SwingUtilities.invokeLater(() -> {
            com.formdev.flatlaf.FlatDarkLaf.setup();
            UIManager.put("Panel.background", ColorScheme.DARK_GRAY_COLOR);
            UIManager.put("Button.background", new Color(55, 55, 55));
            UIManager.put("Button.foreground", Color.LIGHT_GRAY);
            UIManager.put("Label.foreground", Color.LIGHT_GRAY);
            UIManager.put("TextField.background", new Color(45, 45, 45));
            UIManager.put("TextField.foreground", Color.WHITE);
            UIManager.put("ComboBox.background", new Color(45, 45, 45));
            UIManager.put("ComboBox.foreground", Color.WHITE);
            JFrame frame = new JFrame("WikiPane reader preview");
            WikiPanePanel panel = new WikiPanePanel(wiki, workers);
            frame.setContentPane(panel);
            frame.setSize(242, 780);
            frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
            frame.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosed(WindowEvent e) {
                    panel.stop();
                    wiki.close();
                    workers.shutdownNow();
                }
            });
            frame.setVisible(true);
            panel.openTitle(args.length > 0 ? args[0] : "Bronze dagger",args.length>2?args[2]:"");
            if(args.length>3&&!args[3].isEmpty()){javax.swing.Timer expand=new javax.swing.Timer(1000,e->{expandTable(panel,args[3]);});expand.setRepeats(false);expand.start();}
            if (args.length > 1) {
                javax.swing.Timer capture = new javax.swing.Timer(1800, e -> {
                    try {
                        BufferedImage image = new BufferedImage(
                            frame.getWidth(), frame.getHeight(), BufferedImage.TYPE_INT_RGB);
                        Graphics2D g = image.createGraphics();
                        frame.paint(g);
                        g.dispose();
                        ImageIO.write(image, "png", new File(args[1]));
                    } catch (IOException ex) {
                        throw new UncheckedIOException(ex);
                    } finally {
                        frame.dispose();
                    }
                });
                capture.setRepeats(false);
                capture.start();
            }
        });
    }
}
