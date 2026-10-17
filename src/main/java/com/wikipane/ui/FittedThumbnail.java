package com.wikipane.ui;

import com.wikipane.view.CompositionAudit;
import java.awt.*;
import java.awt.image.BufferedImage;
import javax.swing.*;

/** Paints an already bounded decoded thumbnail into actual available width without clipping. */
final class FittedThumbnail extends JLabel {
    private final int maxWidth, maxHeight, declaredWidth, declaredHeight;
    private final CompositionAudit audit;
    private BufferedImage image;
    private int auditedWidth = -1;
    FittedThumbnail(int maxWidth, int maxHeight, int width, int height, CompositionAudit audit) {
        this.maxWidth = maxWidth;
        this.maxHeight = maxHeight;
        declaredWidth = width;
        declaredHeight = height;
        this.audit = audit;
    }
    void image(BufferedImage image) {
        this.image = image;
        setText("");
        revalidate();
        repaint();
    }
    private Dimension fitted(int width) {
        int w = image == null ? declaredWidth : image.getWidth(),
            h = image == null ? declaredHeight : image.getHeight();
        int available = Math.max(1, Math.min(maxWidth, width));
        if (w <= 0 || h <= 0)
            return new Dimension(available, maxHeight);
        double ratio = Math.min(1, Math.min((double) available / w, (double) maxHeight / h));
        return new Dimension(Math.max(1, (int) (w * ratio)), Math.max(1, (int) (h * ratio)));
    }
    @Override
    public Dimension getPreferredSize() {
        int width = maxWidth;
        if (getParent() != null && getParent().getWidth() > 0)
            width = Math.min(
                width, getParent().getWidth() - getParent().getInsets().left - getParent().getInsets().right);
        Dimension fit = fitted(width);
        return new Dimension(fit.width, fit.height + 8);
    }
    @Override
    public Dimension getMaximumSize() {
        return getPreferredSize();
    }
    @Override
    public Dimension getMinimumSize() {
        return new Dimension(0, getPreferredSize().height);
    }
    @Override
    protected void paintComponent(Graphics graphics) {
        if (image == null) {
            super.paintComponent(graphics);
            return;
        }
        Dimension fit = fitted(getWidth());
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(image, 0, 4, fit.width, fit.height, null);
        g.dispose();
        if (getWidth() < maxWidth && audit != null && auditedWidth != getWidth()) {
            auditedWidth = getWidth();
            audit.add("IMAGE_FITTED", "image", CompositionAudit.Outcome.REPAIRED,
                "Thumbnail constrained to available width of " + getWidth() + " px.");
        }
    }
}
