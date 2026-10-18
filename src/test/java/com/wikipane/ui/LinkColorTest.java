package com.wikipane.ui;

import java.awt.Color;
import javax.swing.*;
import javax.swing.text.View;
import javax.swing.text.html.InlineView;
import net.runelite.client.ui.ColorScheme;
import org.junit.Test;
import static org.junit.Assert.*;

public class LinkColorTest {
    @Test public void linksKeepRuneLiteGoldInsideMutedAndColoredSourceText() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            assertEquals(ColorScheme.BRAND_ORANGE, SemanticPalette.LINK);
            ReaderComponents components = new ReaderComponents(url -> {});
            JEditorPane pane = components.text("<span class='muted'><a href='https://oldschool.runescape.wiki/w/Test'>"
                + "<span style='color:#123456'>Related article</span></a></span><p>Plain text</p>");
            pane.setSize(225, 300);
            pane.getPreferredSize();
            int[] counts = new int[2];
            inspect(pane.getUI().getRootView(pane), counts);
            assertTrue("A rendered link must be checked", counts[0] > 0);
            assertTrue("Plain content must retain its own color", counts[1] > 0);
            JButton disclosure = (JButton) components.disclosure("Related links", new JPanel()).getComponent(0);
            assertEquals(ColorScheme.BRAND_ORANGE, disclosure.getForeground());
        });
    }
    private static void inspect(View view, int[] counts) {
        if (view instanceof InlineView) {
            boolean link = view.getElement().getAttributes().isDefined(javax.swing.text.html.HTML.Tag.A);
            Color color = ((InlineView)view).getForeground();
            if (link) { assertEquals(ColorScheme.BRAND_ORANGE, color); counts[0]++; }
            else if (!ColorScheme.BRAND_ORANGE.equals(color)) counts[1]++;
        }
        for (int i = 0; i < view.getViewCount(); i++) inspect(view.getView(i), counts);
    }
}
