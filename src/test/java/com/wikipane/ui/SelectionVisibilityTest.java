package com.wikipane.ui;

import com.wikipane.document.WikiDocument;
import com.wikipane.pin.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class SelectionVisibilityTest {
    @Test public void draggingAcrossCollapsedContentNeverCopiesOrPinsHiddenFacts() throws Exception {
        PinSelectionTest.edt(() -> {
            WikiDocument doc=PinSelectionTest.document("<p>First</p><p>Secret</p><p>Last</p>");
            JPanel root=ReaderComponents.column();
            JEditorPane first=PinSelectionTest.pane("First",new PinContext(doc.semantic,doc.semantic.nodes.get(0))),
                hidden=PinSelectionTest.pane("Secret",new PinContext(doc.semantic,doc.semantic.nodes.get(1))),
                last=PinSelectionTest.pane("Last",new PinContext(doc.semantic,doc.semantic.nodes.get(2)));
            JPanel collapsed=ReaderComponents.column();collapsed.add(hidden);collapsed.setVisible(false);
            root.add(first);root.add(collapsed);root.add(last);
            ReaderSelection selection=new ReaderSelection(pin -> {}, error -> fail(error));selection.refresh(root);
            selection.selectRange(first,0,last,last.getDocument().getLength());
            assertEquals("First\nLast",selection.selectedText(first));
            PinSelection pin=selection.capture(first);assertEquals(2,pin.fragments.size());
            assertFalse(pin.fragments.stream().anyMatch(f -> f.text().contains("Secret")));
            selection.clear();return null;
        });
    }
    @Test public void copyingLargePassageIsIndependentOfOverlayLimitsAndOriginalMenusSurvive() throws Exception {
        PinSelectionTest.edt(() -> {
            String text="reference ".repeat(1500);
            WikiDocument doc=PinSelectionTest.document("<p>"+text+"</p>");
            JPanel root=ReaderComponents.column();
            JEditorPane pane=PinSelectionTest.pane(text,new PinContext(doc.semantic,doc.semantic.nodes.get(0)));root.add(pane);
            JPopupMenu original=new JPopupMenu();original.add(new JMenuItem("Source"));pane.setComponentPopupMenu(original);
            ReaderSelection selection=new ReaderSelection(pin -> {},error -> fail(error));selection.refresh(root);
            selection.selectRange(pane,0,pane,pane.getDocument().getLength());
            assertEquals(text.strip(),selection.selectedText(pane));
            try {selection.capture(pane);fail("Overlay limit should still apply to pinning");}catch(IllegalArgumentException expected){}
            selection.clear();assertSame(original,pane.getComponentPopupMenu());assertEquals(1,original.getComponentCount());
            return null;
        });
    }
}
