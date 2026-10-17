package com.wikipane.ui;

import com.wikipane.DocumentTest;
import com.wikipane.view.*;
import java.awt.*;
import java.util.*;
import javax.swing.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class CompactRecordUiTest {
    private static void layout(Container root) {
        root.doLayout();
        for (Component child : root.getComponents()) if (child instanceof Container) layout((Container)child);
    }
    private static void check(Container root) {
        for (Component child : root.getComponents()) {
            assertTrue("Horizontal overflow: " + child.getName(), child.getX()+child.getWidth() <= root.getWidth()+1);
            if (child instanceof JEditorPane) {
                JEditorPane text=(JEditorPane)child;
                try {
                    Rectangle end=text.modelToView(text.getDocument().getLength()-1);
                    assertNotNull(end);
                    String content=text.getDocument().getText(0,text.getDocument().getLength());
                    if(content.strip().matches("[0-9,]+")) {
                        int first=0,last=content.length()-1;
                        while(first<last && !Character.isDigit(content.charAt(first))) first++;
                        while(last>first && !Character.isDigit(content.charAt(last))) last--;
                        assertEquals("Numeric value split across lines: "+content,text.modelToView(first).y,text.modelToView(last).y);
                    }
                    assertTrue("Clipped last line: " + text.getDocument().getText(0,text.getDocument().getLength()) + " size=" + text.getSize() + " last=" + end,end.y+end.height <= text.getHeight()+2);
                } catch(javax.swing.text.BadLocationException ex) { throw new AssertionError(ex); }
            }
            if (child instanceof Container) check((Container)child);
        }
    }
    @Test public void compactRecordsKeepEveryValueVisibleAtNarrowWidths() throws Exception {
        WikiPage page=new WikiPage(DocumentTest.document("Cooking"));
        ReaderLayout.Record food=page.document.semantic.nodes.stream().filter(n->n.table!=null)
            .flatMap(n->page.layout.records(n.table).stream())
            .filter(r->r.identity!=null&&r.identity.value.text.equals("Cooked giant crab meat")).findFirst().orElseThrow();
        SwingUtilities.invokeAndWait(()->{
            for(int width:new int[]{145,185,225}) {
                FittedThumbnail icon=new FittedThumbnail(22,22,32,32,null);
                icon.image(new java.awt.image.BufferedImage(22,22,java.awt.image.BufferedImage.TYPE_INT_ARGB));
                JComponent card=new ReaderComponents(url->{}).record(food,icon);
                assertEquals(0,icon.getMouseListeners().length);
                assertEquals(Cursor.DEFAULT_CURSOR,icon.getCursor().getType());
                card.setSize(width,1000);
                for(int i=0;i<8;i++){layout(card);card.setSize(width,card.getPreferredSize().height);}
                check(card);
                assertTrue("Compact record grew excessively",card.getHeight() < (width<160?220:160));
                java.util.List<String> text=new ArrayList<>();collect(card,text);
                assertTrue(text.toString(), text.stream().anyMatch(t->t.contains("Cooked giant crab meat")));
                assertTrue(text.toString(), text.stream().anyMatch(t->t.strip().equals("100")));
                assertTrue(text.toString(), text.stream().anyMatch(t->t.strip().equals("Members")));
            }
        });
    }
    @Test public void conditionalDropOddsStayVisibleInCompactRecords() throws Exception {
        WikiPage page=new WikiPage(DocumentTest.document("Vorkath"));
        ReaderLayout.Record drop=page.document.semantic.nodes.stream().filter(n->n.table!=null)
            .flatMap(n->page.layout.records(n.table).stream())
            .filter(r->r.identity!=null&&r.identity.value.text.equals("Vorkath's head")).findFirst().orElseThrow();
        assertTrue(drop.compact);
        SwingUtilities.invokeAndWait(()->{
            for(int width:new int[]{145,185,225}) {
                JComponent card=new ReaderComponents(url->{}).record(drop);card.setSize(width,1000);
                for(int i=0;i<8;i++){layout(card);card.setSize(width,card.getPreferredSize().height);}
                check(card);
                java.util.List<String> text=new ArrayList<>();collect(card,text);
                assertTrue(text.toString(),text.stream().anyMatch(t->t.contains("1/50")));
                assertTrue(text.toString(),text.stream().anyMatch(t->t.contains("Not sold")));
            }
        });
    }
    private static void collect(Container root,java.util.List<String> values){
        for(Component child:root.getComponents()){
            if(child instanceof JEditorPane)try{JEditorPane pane=(JEditorPane)child;values.add(pane.getDocument().getText(0,pane.getDocument().getLength()));}catch(Exception ex){throw new AssertionError(ex);}
            if(child instanceof Container)collect((Container)child,values);
        }
    }
}
