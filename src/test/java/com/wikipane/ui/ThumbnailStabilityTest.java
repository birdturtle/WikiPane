package com.wikipane.ui;

import java.awt.*;
import java.awt.image.BufferedImage;
import javax.swing.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class ThumbnailStabilityTest {
    @Test public void decodedImagesCannotChangeReservedRowIconOrArticleImageGeometry() throws Exception {
        SwingUtilities.invokeAndWait(()->{
            for(boolean fixed:new boolean[]{false,true}) {
                FittedThumbnail image=new FittedThumbnail(fixed?22:200,fixed?22:220,32,24,null,fixed);
                Dimension before=image.getPreferredSize();
                image.image(new BufferedImage(7,40,BufferedImage.TYPE_INT_ARGB));
                assertEquals(before,image.getPreferredSize());
                image.image(new BufferedImage(100,5,BufferedImage.TYPE_INT_ARGB));
                assertEquals(before,image.getPreferredSize());
                image.setSize(before);
                BufferedImage canvas=new BufferedImage(before.width,before.height,BufferedImage.TYPE_INT_ARGB);
                Graphics2D graphics=canvas.createGraphics(); image.paint(graphics);graphics.dispose();
                if(fixed) assertEquals(new Dimension(22,22),before);
            }
        });
    }
    @Test public void missingDimensionsStillReserveStableBoundedSpace() throws Exception {
        SwingUtilities.invokeAndWait(()->{
            FittedThumbnail image=new FittedThumbnail(88,88,0,0,null);
            Dimension before=image.getPreferredSize();
            image.image(new BufferedImage(10,20,BufferedImage.TYPE_INT_ARGB));
            assertEquals(before,image.getPreferredSize());
            assertTrue(before.width<=88&&before.height<=96);
        });
    }
}
