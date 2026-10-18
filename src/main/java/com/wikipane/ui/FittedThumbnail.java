package com.wikipane.ui;

import com.wikipane.view.CompositionAudit;
import java.awt.*;
import java.awt.image.BufferedImage;
import javax.swing.*;

/** Paints an already bounded decoded thumbnail into actual available width without clipping. */
final class FittedThumbnail extends JLabel {
    private final int maxWidth, maxHeight, declaredWidth, declaredHeight;
    private final CompositionAudit audit;
    private final boolean fixedSlot;
    private BufferedImage image;
    private boolean unavailable;
    void unavailable() {
        unavailable = true; setText("No image"); revalidate(); repaint();
    }
    private int auditedWidth = -1;
    FittedThumbnail(int maxWidth, int maxHeight, int width, int height, CompositionAudit audit) {
        this(maxWidth,maxHeight,width,height,audit,false);
    }
    FittedThumbnail(int maxWidth, int maxHeight, int width, int height, CompositionAudit audit, boolean fixedSlot) {
        this.fixedSlot = fixedSlot;
        this.maxWidth = maxWidth;
        this.maxHeight = maxHeight;
        declaredWidth = width;
        declaredHeight = height;
        this.audit = audit;
    }
    void image(BufferedImage image) {
        this.image = image; unavailable = false;
        setText("");
        revalidate();
        repaint();
    }
    private Dimension reserved(int width) {
        int available = Math.max(1,Math.min(maxWidth,width));
        if (fixedSlot || declaredWidth <= 0 || declaredHeight <= 0)
            return new Dimension(available,maxHeight);
        double ratio = Math.min(1,Math.min((double)available/declaredWidth,(double)maxHeight/declaredHeight));
        return new Dimension(Math.max(1,(int)(declaredWidth*ratio)),Math.max(1,(int)(declaredHeight*ratio)));
    }
    private Dimension fitted(int width) {
        Dimension slot = reserved(width);
        double ratio = Math.min(1,Math.min((double)slot.width/image.getWidth(),(double)slot.height/image.getHeight()));
        return new Dimension(Math.max(1,(int)(image.getWidth()*ratio)),Math.max(1,(int)(image.getHeight()*ratio)));
    }
    @Override
    public Dimension getPreferredSize() {
        int width = maxWidth;
        if (getParent() != null && getParent().getWidth() > 0)
            width = Math.min(
                width, getParent().getWidth() - getParent().getInsets().left - getParent().getInsets().right);
        if (unavailable) return new Dimension(Math.max(1, width), 26);
        Dimension fit = reserved(width);
        return new Dimension(fit.width, fit.height + (fixedSlot ? 0 : 8));
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
        Dimension slot = reserved(getWidth());
        g.drawImage(image, (slot.width-fit.width)/2, (fixedSlot ? 0 : 4)+(slot.height-fit.height)/2, fit.width, fit.height, null);
        g.dispose();
        if (getWidth() < maxWidth && audit != null && auditedWidth != getWidth()) {
            auditedWidth = getWidth();
            audit.add("IMAGE_FITTED", "image", CompositionAudit.Outcome.REPAIRED,
                "Thumbnail constrained to available width of " + getWidth() + " px.");
        }
    }
}
