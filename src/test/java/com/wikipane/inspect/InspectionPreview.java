package com.wikipane.inspect;

import com.wikipane.DocumentTest;
import com.wikipane.integration.GameEntityLookup;
import com.wikipane.knowledge.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/** Offline overlay-only preview. Never starts RuneLite or captures the game. */
public final class InspectionPreview {
    public static void main(String[] args) throws Exception {
        String fixture = args.length > 0 ? args[0] : "Ranarr_seed";
        Inspection inspection = Inspection.project(new KnowledgeArticle(DocumentTest.document(fixture), args.length > 2 ? args[2] : "", ""));
        InspectionLayout layout = new InspectionLayout(inspection);
        for (Inspection.Row row : inspection.rows)
            System.out.println(row.source.unit + " · " + row.label + " · " + row.value.text);
        for (int page = 0; page < layout.pages.size(); page++) {
            BufferedImage image = new BufferedImage(InspectionLayout.WIDTH, 500, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = image.createGraphics();
            Dimension size = QuickInspectOverlay.paint(graphics, new QuickInspectController.View(
                GameEntityLookup.url("item", 5295, "preview", null), inspection, layout, null, "", page));
            graphics.dispose();
            String path = args[1].replace(".png", "-" + (page + 1) + ".png");
            ImageIO.write(image.getSubimage(0, 0, size.width, size.height), "png", new File(path));
            System.out.println(path + " " + size.width + "x" + size.height);
        }
    }
}
