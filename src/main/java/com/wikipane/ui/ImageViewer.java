package com.wikipane.ui;

import com.wikipane.api.WikiUrls;
import com.wikipane.cache.ImageCache;
import com.wikipane.document.WikiDocument.Image;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.util.concurrent.*;
import javax.swing.*;
import net.runelite.client.util.LinkBrowser;

/** One non-modal image window. Downloads/decoding are bounded workers; zoom paints the same raster. */
final class ImageViewer {
    private final ExecutorService workers;
    private final ImageCache images;
    private JDialog dialog;
    private long request;
    private Future<?> task;
    ImageViewer(ExecutorService workers, ImageCache images) { this.workers = workers; this.images = images; }
    void open(Component owner, Image image) {
        Window parent = dialog == null ? SwingUtilities.getWindowAncestor(owner) : dialog.getOwner();
        close();
        long version = request;
        String title = image.alt.isBlank() ? "Image" : image.alt;
        dialog = new JDialog(parent, "WikiPane — " + title, Dialog.ModalityType.MODELESS);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        dialog.addWindowListener(new WindowAdapter() {
            @Override public void windowClosed(WindowEvent event) { if (event.getWindow() == dialog) close(); }
        });
        JPanel content = new JPanel(new BorderLayout(0, 4)); content.setBackground(new Color(30, 30, 30));
        JLabel status = new JLabel("Loading full-size image…", SwingConstants.CENTER);
        status.putClientProperty("html.disable", true); status.setForeground(ReaderComponents.MUTED);
        JPanel tools = new JPanel(new BorderLayout());
        JPanel zoomTools = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
        tools.add(zoomTools, BorderLayout.NORTH); tools.add(actions, BorderLayout.SOUTH);
        JButton fit = new JButton("Fit"), actual = new JButton("100%"), minus = new JButton("−"), plus = new JButton("+"),
            retry = new JButton("Retry"), browser = new JButton("Open in browser");
        minus.setToolTipText("Zoom out"); plus.setToolTipText("Zoom in");
        JLabel amount = new JLabel(); amount.setForeground(ReaderComponents.MUTED);
        for (Component control : new Component[]{fit, actual, minus, plus, amount}) zoomTools.add(control);
        actions.add(retry); actions.add(browser);
        retry.setVisible(false);
        content.add(tools, BorderLayout.NORTH); content.add(status, BorderLayout.CENTER);
        dialog.setContentPane(content);
        browser.addActionListener(event -> {
            String url = WikiUrls.browser(WikiUrls.resolve(image.link)) ? image.link : image.url;
            if (WikiUrls.browser(WikiUrls.resolve(url))) LinkBrowser.browse(url);
        });
        retry.addActionListener(event -> open(owner, image));
        for (JButton control : new JButton[]{fit, actual, minus, plus}) control.setEnabled(false);
        dialog.getRootPane().registerKeyboardAction(e -> close(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        GraphicsConfiguration display = parent == null ? owner.getGraphicsConfiguration() : parent.getGraphicsConfiguration();
        if (display == null) display = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration();
        Rectangle screen = display.getBounds();
        dialog.setSize(Math.min(900, screen.width * 3 / 4), Math.min(700, screen.height * 3 / 4));
        dialog.setMinimumSize(new Dimension(Math.min(420, screen.width), Math.min(240, screen.height)));
        dialog.setLocationRelativeTo(parent); dialog.setVisible(true);
        try {
            task = workers.submit(() -> {
                try {
                    BufferedImage decoded = images.preview(image.url);
                    SwingUtilities.invokeLater(() -> {
                        if (version != request || dialog == null) return;
                        Canvas canvas = new Canvas(decoded);
                        JScrollPane scroll = new JScrollPane(canvas); scroll.setBorder(null);
                        scroll.getViewport().setBackground(content.getBackground());
                        canvas.setToolTipText("Drag to pan · Ctrl + mouse wheel to zoom");
                        Runnable update = () -> amount.setText(canvas.fit ? "Fit" : Math.round(canvas.scale * 100) + "%");
                        fit.addActionListener(event -> { canvas.fit(); update.run(); });
                        actual.addActionListener(event -> { canvas.zoom(1); update.run(); });
                        minus.addActionListener(event -> { canvas.zoom(canvas.currentScale() / 1.25); update.run(); });
                        plus.addActionListener(event -> { canvas.zoom(canvas.currentScale() * 1.25); update.run(); });
                        canvas.addMouseWheelListener(event -> {
                            if (event.isControlDown()) { canvas.zoom(canvas.currentScale() * Math.pow(1.25, -event.getWheelRotation())); update.run(); event.consume(); }
                        });
                        for (JButton control : new JButton[]{fit, actual, minus, plus}) control.setEnabled(true);
                        content.remove(status); content.add(scroll, BorderLayout.CENTER); update.run();
                        content.revalidate(); content.repaint();
                    });
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> {
                        if (version == request && dialog != null) {
                            status.setText("Image unavailable. Retry or open it on the Wiki."); retry.setVisible(true);
                            content.revalidate();
                        }
                    });
                }
            });
        } catch (RejectedExecutionException ex) { status.setText("Image viewer is busy. Please retry."); retry.setVisible(true); }
    }
    void close() {
        request++;
        if (task != null) { task.cancel(true); task = null; }
        JDialog old = dialog; dialog = null; if (old != null) old.dispose();
    }
    static final class Canvas extends JComponent implements Scrollable {
        private final BufferedImage image;
        private boolean fit = true;
        private double scale = 1;
        Canvas(BufferedImage image) {
            this.image = image;
            MouseAdapter pan = new MouseAdapter() {
                Point start, position;
                @Override public void mousePressed(MouseEvent event) {
                    if (!SwingUtilities.isLeftMouseButton(event) || !(getParent() instanceof JViewport)) return;
                    JViewport viewport = (JViewport)getParent();
                    start = SwingUtilities.convertPoint(Canvas.this, event.getPoint(), viewport); position = viewport.getViewPosition();
                    setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                }
                @Override public void mouseDragged(MouseEvent event) {
                    if (start == null || !(getParent() instanceof JViewport)) return;
                    JViewport viewport = (JViewport)getParent(); Point now = SwingUtilities.convertPoint(Canvas.this, event.getPoint(), viewport);
                    viewport.setViewPosition(new Point(Math.max(0, Math.min(getWidth()-viewport.getWidth(), position.x+start.x-now.x)),
                        Math.max(0, Math.min(getHeight()-viewport.getHeight(), position.y+start.y-now.y))));
                }
                @Override public void mouseReleased(MouseEvent event) { start = null; setCursor(Cursor.getDefaultCursor()); }
            };
            addMouseListener(pan); addMouseMotionListener(pan);
        }
        double currentScale() {
            if (!fit) return scale;
            Dimension available = getParent() instanceof JViewport ? ((JViewport)getParent()).getExtentSize() : getSize();
            return Math.min(1, Math.min((double)Math.max(1, available.width-24)/image.getWidth(),
                (double)Math.max(1, available.height-24)/image.getHeight()));
        }
        void fit() { fit = true; revalidate(); repaint(); }
        void zoom(double value) { fit = false; scale = Math.max(.05, Math.min(4, value)); revalidate(); repaint(); }
        @Override public Dimension getPreferredSize() {
            if (fit && getParent() instanceof JViewport) return ((JViewport)getParent()).getExtentSize();
            return new Dimension((int)Math.ceil(image.getWidth()*scale)+24, (int)Math.ceil(image.getHeight()*scale)+24);
        }
        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            double factor = currentScale();
            int width = Math.max(1, (int)(image.getWidth()*factor)), height = Math.max(1, (int)(image.getHeight()*factor));
            Graphics2D g = (Graphics2D)graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.drawImage(image, Math.max(12,(getWidth()-width)/2), Math.max(12,(getHeight()-height)/2), width, height, null);
            } finally { g.dispose(); }
        }
        public Dimension getPreferredScrollableViewportSize() { return new Dimension(700, 500); }
        public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) { return 24; }
        public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) { return Math.max(24, (orientation == SwingConstants.HORIZONTAL ? visible.width : visible.height)-24); }
        public boolean getScrollableTracksViewportWidth() { return fit || getParent() instanceof JViewport && getPreferredSize().width < getParent().getWidth(); }
        public boolean getScrollableTracksViewportHeight() { return fit || getParent() instanceof JViewport && getPreferredSize().height < getParent().getHeight(); }
    }
}
