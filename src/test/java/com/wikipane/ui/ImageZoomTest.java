package com.wikipane.ui;

import java.awt.*;
import java.awt.image.BufferedImage;
import javax.swing.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class ImageZoomTest {
    @Test public void zoomKeepsTheImagePixelUnderTheCursorAndToolbarKeepsTheCenter() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            ImageViewer.Canvas canvas = new ImageViewer.Canvas(new BufferedImage(2000,1600,BufferedImage.TYPE_INT_ARGB));
            JViewport viewport = new JViewport(); viewport.setExtentSize(new Dimension(400,300));
            viewport.setView(canvas); viewport.setViewSize(new Dimension(400,300));
            canvas.zoom(1);
            viewport.setViewPosition(new Point(300,200));
            Point cursor = new Point(80,70);
            double beforeX = viewport.getViewPosition().x+cursor.x-12;
            double beforeY = viewport.getViewPosition().y+cursor.y-12;
            canvas.zoom(1.25,new Point(viewport.getViewPosition().x+cursor.x,viewport.getViewPosition().y+cursor.y));
            assertEquals(beforeX,(viewport.getViewPosition().x+cursor.x-12)/1.25,1);
            assertEquals(beforeY,(viewport.getViewPosition().y+cursor.y-12)/1.25,1);
            double centerX=(viewport.getViewPosition().x+200-12)/1.25;
            double centerY=(viewport.getViewPosition().y+150-12)/1.25;
            canvas.zoom(2);
            assertEquals(centerX,(viewport.getViewPosition().x+200-12)/2.0,1);
            assertEquals(centerY,(viewport.getViewPosition().y+150-12)/2.0,1);
            canvas.fit(); assertEquals(new Point(),viewport.getViewPosition());
        });
    }
    @Test public void chooserSizingUsesRoomAboveOrBelowWithinSmallWindows() {
        Dimension size=PopupSizing.fit(new Rectangle(650,140,200,28),new Rectangle(100,100,760,340),215,400);
        assertTrue(size.height<=272);assertEquals(215,size.width);
        Dimension above=PopupSizing.fit(new Rectangle(650,380,200,28),new Rectangle(100,100,760,340),215,400);
        assertTrue(above.height<=268);
        Dimension tiny=PopupSizing.fit(new Rectangle(0,25,20,28),new Rectangle(0,0,80,100),215,400);
        assertTrue(tiny.width<=80);assertTrue(tiny.height<=100);
    }
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
