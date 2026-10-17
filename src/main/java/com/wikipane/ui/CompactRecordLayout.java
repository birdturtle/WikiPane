package com.wikipane.ui;

import java.awt.*;

/** Measures each metric at its actual width; avoids equal-height grids and stale BoxLayout sizes. */
final class CompactRecordLayout implements LayoutManager {
    private final boolean fullRows;
    CompactRecordLayout(boolean fullRows) { this.fullRows = fullRows; }
    @Override public void addLayoutComponent(String name, Component component) {}
    @Override public void removeLayoutComponent(Component component) {}
    private int columns(int width) { return fullRows || width < 160 ? 1 : 2; }
    private int[] heights(Container parent, int width) {
        int columns=columns(width), cell=Math.max(1,(width-(columns-1)*8)/columns);
        int[] heights=new int[(parent.getComponentCount()+columns-1)/columns];
        for(int i=0;i<parent.getComponentCount();i++) {
            Component child=parent.getComponent(i);
            child.setSize(cell,Short.MAX_VALUE);
            heights[i/columns]=Math.max(heights[i/columns],child.getPreferredSize().height);
        }
        return heights;
    }
    @Override public Dimension preferredLayoutSize(Container parent) {
        Insets insets=parent.getInsets();
        int width=parent.getWidth()>0?parent.getWidth()-insets.left-insets.right:180;
        int height=insets.top+insets.bottom;
        for(int row:heights(parent,width)) height+=row;
        return new Dimension(width+insets.left+insets.right,height);
    }
    @Override public Dimension minimumLayoutSize(Container parent) {
        return new Dimension(0,preferredLayoutSize(parent).height);
    }
    @Override public void layoutContainer(Container parent) {
        Insets insets=parent.getInsets();
        int width=Math.max(1,parent.getWidth()-insets.left-insets.right);
        int columns=columns(width), cell=Math.max(1,(width-(columns-1)*8)/columns);
        int[] heights=heights(parent,width);
        int y=insets.top;
        for(int i=0;i<parent.getComponentCount();i++) {
            parent.getComponent(i).setBounds(insets.left+(i%columns)*(cell+8),y,cell,heights[i/columns]);
            if(i%columns==columns-1) y+=heights[i/columns];
        }
    }
}
