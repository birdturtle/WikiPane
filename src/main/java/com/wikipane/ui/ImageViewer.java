package com.wikipane.ui;

import com.wikipane.cache.ImageCache;
import com.wikipane.document.WikiDocument.Image;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.util.concurrent.*;
import javax.swing.*;

/** One non-modal image window, with bounded worker decoding and stale-result protection. */
final class ImageViewer {
    private final ExecutorService workers;
    private final ImageCache images;
    private JDialog dialog;
    private long request;
    private Future<?> task;
    ImageViewer(ExecutorService workers, ImageCache images) {
        this.workers = workers; this.images = images;
    }
    void open(Component owner, Image image) {
        close();
        long version = request;
        dialog = new JDialog(SwingUtilities.getWindowAncestor(owner), "WikiPane — Image", Dialog.ModalityType.MODELESS);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        dialog.addWindowListener(new WindowAdapter() {
            @Override public void windowClosed(WindowEvent event) {
                if (event.getWindow() == dialog) close();
            }
        });
        JLabel status = new JLabel("Loading image…", SwingConstants.CENTER);
        status.putClientProperty("html.disable", true);
        status.setForeground(ReaderComponents.MUTED);
        JPanel content = new JPanel(new BorderLayout());
        content.setBackground(new Color(30, 30, 30));
        content.add(status, BorderLayout.CENTER);
        dialog.setContentPane(content);
        dialog.getRootPane().registerKeyboardAction(e -> close(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
            JComponent.WHEN_IN_FOCUSED_WINDOW);
        Rectangle screen = owner.getGraphicsConfiguration().getBounds();
        dialog.setSize(Math.min(900, screen.width * 3 / 4), Math.min(700, screen.height * 3 / 4));
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
        try {
            task = workers.submit(() -> {
                try {
                    BufferedImage decoded = images.preview(image.url);
                    SwingUtilities.invokeLater(() -> {
                        if (version != request || dialog == null) return;
                        content.removeAll(); content.add(new Canvas(decoded), BorderLayout.CENTER);
                        content.revalidate(); content.repaint();
                    });
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> {
                        if (version == request && dialog != null) status.setText("Image unavailable. Please try again.");
                    });
                }
            });
        } catch (RejectedExecutionException ex) { status.setText("Image viewer is unavailable."); }
    }
    void close() {
        request++;
        if (task != null) { task.cancel(true); task = null; }
        JDialog old = dialog; dialog = null;
        if (old != null) old.dispose();
    }
    private static final class Canvas extends JComponent {
        private final BufferedImage image;
        Canvas(BufferedImage image) { this.image = image; }
        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            double scale = Math.min(1, Math.min((double) Math.max(1, getWidth()-24)/image.getWidth(),
                (double) Math.max(1, getHeight()-24)/image.getHeight()));
            int width = Math.max(1, (int)(image.getWidth()*scale)), height = Math.max(1, (int)(image.getHeight()*scale));
            Graphics2D g = (Graphics2D)graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.drawImage(image, (getWidth()-width)/2, (getHeight()-height)/2, width, height, null);
            } finally { g.dispose(); }
        }
    }
}
