package com.wikipane.ui;

import com.wikipane.view.ArticleNavigation;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.tree.*;

/** Searchable tree of the exact same sections displayed by ArticleReader. */
public final class SectionNavigator extends JPanel {
    private final JTree tree=new JTree(){
        @Override public boolean getScrollableTracksViewportWidth(){return true;}
        @Override public void scrollRectToVisible(Rectangle rectangle){
            super.scrollRectToVisible(new Rectangle(0,rectangle.y,Math.max(1,getWidth()),rectangle.height));
        }
    };
    private final JTextField filter=new JTextField();
    private final JLabel empty=new JLabel("No matching sections");
    private final Set<String> expanded=new HashSet<>();
    private boolean rebuilding;
    public void focusFilter(){filter.requestFocusInWindow();}
    public SectionNavigator(List<ArticleNavigation.ReadingSection> roots,Set<String> open,String activeArea,
                            String activeAnchor,Consumer<ArticleNavigation.Destination> choose) {
        super(new BorderLayout(0,5));setBorder(BorderFactory.createEmptyBorder(6,6,6,6));
        expanded.addAll(open);
        JPanel filterRow=new JPanel(new BorderLayout(0,3));filterRow.setOpaque(false);
        JLabel hint=new JLabel("Find a section");hint.setForeground(ReaderComponents.MUTED);hint.setFont(hint.getFont().deriveFont(11f));
        filter.setToolTipText("Filter article sections");filter.getAccessibleContext().setAccessibleName("Filter article sections");
        filterRow.add(hint,BorderLayout.NORTH);filterRow.add(filter);add(filterRow,BorderLayout.NORTH);
        tree.setName("wiki.sections");tree.setRootVisible(false);tree.setShowsRootHandles(true);tree.setRowHeight(0);
        if(tree.getUI() instanceof javax.swing.plaf.basic.BasicTreeUI) {
            Icon emptyHandle=new Icon(){public int getIconWidth(){return 10;}public int getIconHeight(){return 10;}public void paintIcon(Component c,Graphics g,int x,int y){}};
            javax.swing.plaf.basic.BasicTreeUI ui=(javax.swing.plaf.basic.BasicTreeUI)tree.getUI();
            ui.setExpandedIcon(emptyHandle);ui.setCollapsedIcon(emptyHandle);
        }
        tree.setToggleClickCount(0);tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        tree.setCellRenderer(new DefaultTreeCellRenderer(){
            @Override public Component getTreeCellRendererComponent(JTree owner,Object value,boolean selected,boolean expanded,boolean leaf,int row,boolean focus){
                JLabel label=(JLabel)super.getTreeCellRendererComponent(owner,value,selected,expanded,leaf,row,focus);
                ArticleNavigation.ReadingSection section=section(value);label.setIcon(null);
                if(section==null)return label;
                label.setForeground(ReaderComponents.ACCENT);
                if(!leaf)label.setIcon(new Icon(){
                    public int getIconWidth(){return 10;}public int getIconHeight(){return 10;}
                    public void paintIcon(Component c,Graphics g,int x,int y){
                        g.setColor(ReaderComponents.ACCENT);
                        if(expanded)g.fillPolygon(new int[]{x+1,x+9,x+5},new int[]{y+3,y+3,y+8},3);
                        else g.fillPolygon(new int[]{x+3,x+3,x+8},new int[]{y+1,y+9,y+5},3);
                    }
                });
                int width=Math.max(60,(owner.getWidth()>0?owner.getWidth():215)-40-(section.destination.depth+1)*16);
                String text=com.wikipane.document.WikiDocumentParser.escape(section.destination.label);
                label.putClientProperty("html.disable",false);
                label.setText("<html><body style='width:"+width+"px'>"+(section.destination.depth==0?"<b>"+text+"</b>":text)+"</body></html>");
                label.setBorder(BorderFactory.createEmptyBorder(5,2,5,2));
                label.setToolTipText("<html>"+com.wikipane.document.WikiDocumentParser.escape(section.destination.label)+"</html>");
                label.getAccessibleContext().setAccessibleName(section.destination.label);
                return label;
            }
        });
        tree.addTreeExpansionListener(new TreeExpansionListener(){
            public void treeExpanded(TreeExpansionEvent event){remember(event,true);}
            public void treeCollapsed(TreeExpansionEvent event){remember(event,false);}
            private void remember(TreeExpansionEvent event,boolean state){
                if(rebuilding||!filter.getText().isBlank())return;
                ArticleNavigation.ReadingSection section=section(event.getPath().getLastPathComponent());
                if(section!=null){if(state)expanded.add(section.key());else expanded.remove(section.key());}
            }
        });
        Runnable update=() -> {
            rebuilding=true;
            String query=filter.getText().strip().toLowerCase(Locale.ROOT);
            DefaultMutableTreeNode root=new DefaultMutableTreeNode("Sections");
            for(ArticleNavigation.ReadingSection section:roots){DefaultMutableTreeNode node=filtered(section,query);if(node!=null)root.add(node);}
            tree.setModel(new DefaultTreeModel(root));
            TreePath selected=null;
            Enumeration<?> nodes=root.preorderEnumeration();
            while(nodes.hasMoreElements()){
                DefaultMutableTreeNode node=(DefaultMutableTreeNode)nodes.nextElement();ArticleNavigation.ReadingSection section=section(node);
                if(section==null)continue;
                TreePath path=new TreePath(node.getPath());
                boolean parentsOpen=true;
                for(TreePath parent=path.getParentPath();parent!=null;parent=parent.getParentPath()) {
                    ArticleNavigation.ReadingSection ancestor=section(parent.getLastPathComponent());
                    if(ancestor!=null&&!expanded.contains(ancestor.key()))parentsOpen=false;
                }
                if(!query.isEmpty()||parentsOpen&&expanded.contains(section.key()))tree.expandPath(path);
                if(query.isEmpty()&&section.destination.area.equals(activeArea)&&section.destination.anchor.equals(activeAnchor))selected=path;
                else if(!query.isEmpty()&&selected==null&&section.destination.label.toLowerCase(Locale.ROOT).contains(query))selected=path;
            }
            if(selected!=null){tree.setSelectionPath(selected);tree.scrollPathToVisible(selected);}
            else if(tree.getRowCount()>0)tree.setSelectionRow(0);
            empty.setVisible(root.getChildCount()==0);rebuilding=false;
        };
        filter.getDocument().addDocumentListener(new DocumentListener(){
            public void insertUpdate(DocumentEvent event){update.run();}public void removeUpdate(DocumentEvent event){update.run();}public void changedUpdate(DocumentEvent event){update.run();}
        });
        Runnable select=() -> {TreePath path=tree.getSelectionPath();ArticleNavigation.ReadingSection section=path==null?null:section(path.getLastPathComponent());if(section!=null)choose.accept(section.destination);};
        tree.addMouseListener(new MouseAdapter(){
            @Override public void mouseClicked(MouseEvent event){
                if(!SwingUtilities.isLeftMouseButton(event))return;
                TreePath path=tree.getPathForLocation(event.getX(),event.getY());if(path==null)return;
                Rectangle bounds=tree.getPathBounds(path);
                // The native disclosure handle expands in place. Clicking the label navigates directly.
                if(bounds!=null&&event.getX()>=bounds.x) {
                    if(!tree.getModel().isLeaf(path.getLastPathComponent())&&event.getX()<bounds.x+16) {
                        if(tree.isExpanded(path))tree.collapsePath(path);else tree.expandPath(path);
                    } else {tree.setSelectionPath(path);select.run();}
                }
            }
        });
        tree.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER,0),"choose");
        tree.getActionMap().put("choose",new AbstractAction(){public void actionPerformed(ActionEvent event){select.run();}});
        filter.addActionListener(event -> select.run());
        filter.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN,0),"sections");
        filter.getActionMap().put("sections",new AbstractAction(){public void actionPerformed(ActionEvent event){tree.requestFocusInWindow();}});
        JScrollPane scroll=new JScrollPane(tree);scroll.setBorder(null);scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setPreferredSize(new Dimension(215,300));add(scroll,BorderLayout.CENTER);
        empty.setForeground(ReaderComponents.MUTED);add(empty,BorderLayout.SOUTH);update.run();
    }
    private static ArticleNavigation.ReadingSection section(Object node){
        Object value=node instanceof DefaultMutableTreeNode?((DefaultMutableTreeNode)node).getUserObject():null;
        return value instanceof ArticleNavigation.ReadingSection?(ArticleNavigation.ReadingSection)value:null;
    }
    private static DefaultMutableTreeNode filtered(ArticleNavigation.ReadingSection section,String query){
        DefaultMutableTreeNode node=new DefaultMutableTreeNode(section);
        boolean matches=query.isEmpty()||section.destination.label.toLowerCase(Locale.ROOT).contains(query);
        for(ArticleNavigation.ReadingSection child:section.children){DefaultMutableTreeNode nested=filtered(child,matches?"":query);if(nested!=null)node.add(nested);}
        return matches||node.getChildCount()>0?node:null;
    }
}
