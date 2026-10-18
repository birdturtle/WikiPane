package com.wikipane.ui;

import java.awt.*;
import java.awt.image.BufferedImage;
import javax.swing.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class ImageZoomTest {
    @Test public void largeImageCanBeReadAtActualSizeAndReturnedToFitWithoutNewRaster() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            ImageViewer.Canvas image = new ImageViewer.Canvas(new BufferedImage(2000, 1500, BufferedImage.TYPE_INT_ARGB));
            JViewport viewport = new JViewport(); viewport.setSize(500, 400); viewport.setView(image); viewport.doLayout();
            assertTrue(image.currentScale() < 1);
            assertTrue(image.getScrollableTracksViewportWidth());
            image.zoom(1); viewport.doLayout();
            assertEquals(2024, image.getPreferredSize().width); assertFalse(image.getScrollableTracksViewportWidth());
            image.zoom(100); assertEquals(4, image.currentScale(), .001);
            image.zoom(.001); assertEquals(.05, image.currentScale(), .001);
            image.fit(); assertTrue(image.getScrollableTracksViewportWidth()); assertTrue(image.currentScale() < 1);
        });
    }
}
